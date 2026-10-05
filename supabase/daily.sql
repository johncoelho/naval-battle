-- Diário de bordo: check-in diário com trilha de 7 dias e desafio do dia, pagos em
-- dobrões. Rodar depois de economy.sql (usa brt_today, brt_seconds_to_midnight e
-- config_int). Mesmo padrão das outras recompensas: o servidor decide e registra
-- (uma vez por dia, travado aqui), o app credita os dobrões que ele devolver.
--
-- Trilha: 7 dias SEGUIDOS a partir do primeiro check-in. Pulou um dia, volta ao dia 1;
-- fechou o dia 7, ganha o bônus da semana e a trilha recomeça no dia seguinte.
-- Desafio: uma missão por dia, igual para todos, sorteada pela data. O app conta o
-- progresso nas partidas (contra a IA e online) e resgata depois do check-in do dia.

insert into public.app_config (key, value) values
  ('daily_checkin_doubloons', '25'),
  ('daily_week_bonus', '300'),
  ('daily_challenge_doubloons', '50')
on conflict (key) do nothing;

create table if not exists public.daily_progress (
  user_id           uuid primary key references auth.users(id) on delete cascade,
  streak            integer not null default 0,
  last_checkin      date,
  challenge_claimed date,
  updated_at        timestamptz not null default now()
);

alter table public.daily_progress enable row level security;

drop policy if exists "diario: ler o proprio" on public.daily_progress;
create policy "diario: ler o proprio"
  on public.daily_progress for select
  using (auth.uid() = user_id);

-- histórico do que foi pago — o índice único é a trava de "uma vez por dia"
create table if not exists public.daily_claims (
  id         uuid primary key default gen_random_uuid(),
  user_id    uuid not null references auth.users(id) on delete cascade,
  day        date not null,
  kind       text not null check (kind in ('checkin', 'week', 'challenge')),
  doubloons  integer not null,
  created_at timestamptz not null default now(),
  unique (user_id, day, kind)
);

alter table public.daily_claims enable row level security;

drop policy if exists "diario: ler os proprios resgates" on public.daily_claims;
create policy "diario: ler os proprios resgates"
  on public.daily_claims for select
  using (auth.uid() = user_id);

-- missão do dia: um catálogo fixo em rodízio pela data (o texto mora no app)
create or replace function public.daily_mission(p_day date)
returns table (code text, target integer)
language sql immutable as $$
  select (array['PLAY_1', 'WIN_1', 'SINK_5', 'ABILITY_2', 'ACCURACY_60'])[((p_day - date '2026-01-01') % 5) + 1],
         (array[1, 1, 5, 2, 60])[((p_day - date '2026-01-01') % 5) + 1];
$$;

-- dias já feitos na trilha atual (0 a 7), do ponto de vista de hoje
create or replace function public.daily_streak_now(p_streak integer, p_last date)
returns integer
language sql stable as $$
  select case
    when p_last = public.brt_today() then p_streak
    when p_last = public.brt_today() - 1 and p_streak < 7 then p_streak
    else 0
  end;
$$;

create or replace function public.daily_status()
returns table (
  today date, streak integer, checked_in boolean, checkin_reward integer, week_bonus integer,
  mission text, mission_target integer, challenge_claimed boolean, challenge_reward integer,
  resets_in_seconds integer
)
language plpgsql security definer set search_path = public as $$
#variable_conflict use_column
declare
  p public.daily_progress%rowtype;
  d date := public.brt_today();
begin
  if auth.uid() is null then
    raise exception 'daily_auth';
  end if;
  select * into p from public.daily_progress dp where dp.user_id = auth.uid();
  return query select
    d,
    public.daily_streak_now(coalesce(p.streak, 0), p.last_checkin),
    coalesce(p.last_checkin = d, false),
    public.config_int('daily_checkin_doubloons', 25),
    public.config_int('daily_week_bonus', 300),
    m.code,
    m.target,
    coalesce(p.challenge_claimed = d, false),
    public.config_int('daily_challenge_doubloons', 50),
    public.brt_seconds_to_midnight()
  from public.daily_mission(d) m;
end $$;

-- check-in do dia: devolve o dia da trilha e os dobrões a creditar (0 se já tinha feito)
create or replace function public.daily_checkin()
returns table (streak integer, doubloons integer, week_completed boolean)
language plpgsql security definer set search_path = public as $$
#variable_conflict use_column
declare
  p public.daily_progress%rowtype;
  d date := public.brt_today();
  next_streak integer;
  pay integer;
  bonus integer := 0;
begin
  if auth.uid() is null then
    raise exception 'daily_auth';
  end if;
  perform pg_advisory_xact_lock(hashtext('daily:' || auth.uid()::text));
  select * into p from public.daily_progress dp where dp.user_id = auth.uid();

  if p.last_checkin = d then
    streak := p.streak;
    doubloons := 0;
    week_completed := false;
    return next;
    return;
  end if;

  next_streak := case when p.last_checkin = d - 1 and p.streak < 7 then p.streak + 1 else 1 end;
  pay := public.config_int('daily_checkin_doubloons', 25);
  insert into public.daily_claims (user_id, day, kind, doubloons) values (auth.uid(), d, 'checkin', pay);
  if next_streak = 7 then
    bonus := public.config_int('daily_week_bonus', 300);
    insert into public.daily_claims (user_id, day, kind, doubloons) values (auth.uid(), d, 'week', bonus);
  end if;

  insert into public.daily_progress as dp (user_id, streak, last_checkin)
  values (auth.uid(), next_streak, d)
  on conflict (user_id) do update
    set streak = excluded.streak, last_checkin = excluded.last_checkin, updated_at = now();

  streak := next_streak;
  doubloons := pay + bonus;
  week_completed := next_streak = 7;
  return next;
end $$;

-- resgate do desafio: precisa do check-in de hoje, da missão de hoje e do alvo batido
drop function if exists public.claim_daily_challenge(text, integer);
create function public.claim_daily_challenge(p_mission text, p_progress integer)
returns table (doubloons integer)
language plpgsql security definer set search_path = public as $$
#variable_conflict use_column
declare
  p public.daily_progress%rowtype;
  d date := public.brt_today();
  m record;
  pay integer;
begin
  if auth.uid() is null then
    raise exception 'daily_auth';
  end if;
  perform pg_advisory_xact_lock(hashtext('daily:' || auth.uid()::text));
  select * into p from public.daily_progress dp where dp.user_id = auth.uid();
  if p.last_checkin is distinct from d then
    raise exception 'daily_checkin_first';
  end if;
  if p.challenge_claimed = d then
    doubloons := 0;
    return next;
    return;
  end if;
  select * into m from public.daily_mission(d);
  if p_mission is distinct from m.code or coalesce(p_progress, 0) < m.target then
    raise exception 'daily_not_done';
  end if;
  pay := public.config_int('daily_challenge_doubloons', 50);
  insert into public.daily_claims (user_id, day, kind, doubloons) values (auth.uid(), d, 'challenge', pay);
  update public.daily_progress dp set challenge_claimed = d, updated_at = now() where dp.user_id = auth.uid();
  doubloons := pay;
  return next;
end $$;

revoke all on function public.daily_status() from public;
revoke all on function public.daily_checkin() from public;
revoke all on function public.claim_daily_challenge(text, integer) from public;
grant execute on function public.daily_status() to authenticated;
grant execute on function public.daily_checkin() to authenticated;
grant execute on function public.claim_daily_challenge(text, integer) to authenticated;
