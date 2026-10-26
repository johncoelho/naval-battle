-- Diário de bordo: check-in diário com trilha de 7 dias e desafio do dia, pagos em
-- dobrões. Rodar depois de economy.sql (usa brt_today, brt_seconds_to_midnight e
-- config_int). Mesmo padrão das outras recompensas: o servidor decide e registra
-- (uma vez por dia, travado aqui). Conta com carteira (dobroes_saldo.sql, #34): o servidor credita;
-- conta sem carteira (legado): o app credita os dobrões que ele devolver.
--
-- Trilha: 7 dias SEGUIDOS a partir do primeiro check-in. Pulou um dia, volta ao dia 1;
-- fechou o dia 7, ganha o bônus da semana e a trilha recomeça no dia seguinte.
-- Desafio: uma missão por dia, igual para todos, sorteada pela data. O app conta o
-- progresso nas partidas (contra a IA e online) e resgata depois do check-in do dia.
-- Prêmio de retorno (#42): no check-in, se o anterior foi há `daily_return_days` dias ou mais, paga
-- dobrões + milhas, uma vez a cada `daily_return_cooldown_days` dias. Fora do teto de dobrões online
-- (trava própria: reason 'return' no razão e kind 'return' em daily_claims). Conta is_test não recebe.
-- Sem carteira (legado) o servidor não credita dobrões: registra o resgate, paga as milhas e devolve
-- return_doubloons = 0.

insert into public.app_config (key, value) values
  ('daily_checkin_doubloons', '25'),
  ('daily_week_bonus', '300'),
  ('daily_challenge_doubloons', '50'),
  ('daily_return_days', '5'),
  ('daily_return_cooldown_days', '30'),
  ('daily_return_doubloons', '100'),
  ('daily_return_miles', '3')
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
  kind       text not null check (kind in ('checkin', 'week', 'challenge', 'return')),
  doubloons  integer not null,
  created_at timestamptz not null default now(),
  unique (user_id, day, kind)
);

-- tabela que já existia: troca a constraint para aceitar 'return' (#42)
alter table public.daily_claims drop constraint if exists daily_claims_kind_check;
alter table public.daily_claims add constraint daily_claims_kind_check
  check (kind in ('checkin', 'week', 'challenge', 'return'));

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

-- O prêmio de retorno está disponível no check-in de hoje? Último check-in há `daily_return_days`
-- dias ou mais, sem resgate de retorno nos últimos `daily_return_cooldown_days` dias, conta real.
create or replace function public.daily_return_ready(p_user uuid, p_last date)
returns boolean
language sql stable security definer set search_path = public as $$
  select p_last is not null
     and public.brt_today() - p_last >= public.config_int('daily_return_days', 5)
     and not public.is_test_user(p_user)
     and not exists (
       select 1 from public.daily_claims c
        where c.user_id = p_user and c.kind = 'return'
          and c.day > public.brt_today() - public.config_int('daily_return_cooldown_days', 30));
$$;
revoke all on function public.daily_return_ready(uuid, date) from public, anon, authenticated;

-- Mudou o retorno (colunas novas no fim; o app antigo lê por chave e ignora): drop + create e os
-- mesmos grants no fim do arquivo.
drop function if exists public.daily_status();
create or replace function public.daily_status()
returns table (
  today date, streak integer, checked_in boolean, checkin_reward integer, week_bonus integer,
  mission text, mission_target integer, challenge_claimed boolean, challenge_reward integer,
  resets_in_seconds integer, return_ready boolean, return_doubloons integer, return_miles integer
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
    public.brt_seconds_to_midnight(),
    public.daily_return_ready(auth.uid(), p.last_checkin),
    public.config_int('daily_return_doubloons', 100),
    public.config_int('daily_return_miles', 3)
  from public.daily_mission(d) m;
end $$;

-- check-in do dia: devolve o dia da trilha e os dobrões a creditar (0 se já tinha feito).
-- `doubloons` é só check-in + bônus da semana (é o que o app antigo mostra); o prêmio de retorno vem
-- à parte em return_doubloons / return_miles (0 quando não há). Mudou o retorno: drop + create.
drop function if exists public.daily_checkin();
create or replace function public.daily_checkin()
returns table (streak integer, doubloons integer, week_completed boolean,
               return_doubloons integer, return_miles integer)
language plpgsql security definer set search_path = public as $$
#variable_conflict use_column
declare
  p public.daily_progress%rowtype;
  d date := public.brt_today();
  next_streak integer;
  pay integer;
  bonus integer := 0;
  ret_dob integer := 0;
  ret_miles integer := 0;
  cur integer;
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
    return_doubloons := 0;
    return_miles := 0;
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

  -- prêmio de retorno (#42): antes de gravar o dia novo, pois depende do check-in anterior.
  -- Ordem de locks: daily: (acima) -> linha do perfil (miles_refresh) -> dobroes: (dobroes_apply)
  if public.daily_return_ready(auth.uid(), p.last_checkin) then
    ret_dob := public.config_int('daily_return_doubloons', 100);
    ret_miles := public.config_int('daily_return_miles', 3);
    insert into public.daily_claims (user_id, day, kind, doubloons) values (auth.uid(), d, 'return', ret_dob);
    cur := public.miles_refresh(auth.uid());
    update public.profiles
       set nautical_miles = greatest(cur, least(public.config_int('miles_cap', 50), cur + ret_miles))
     where id = auth.uid()
    returning nautical_miles - cur into ret_miles;
    if public.dobroes_has_wallet(auth.uid()) then
      perform public.dobroes_apply(auth.uid(), ret_dob, 'return', d::text);
    else
      ret_dob := 0;
    end if;
  end if;

  insert into public.daily_progress as dp (user_id, streak, last_checkin)
  values (auth.uid(), next_streak, d)
  on conflict (user_id) do update
    set streak = excluded.streak, last_checkin = excluded.last_checkin, updated_at = now();

  -- carteira no servidor (#34): com carteira o servidor credita (uma vez por dia); sem ela o aparelho soma
  perform public.dobroes_credit_if_wallet(auth.uid(), pay + bonus, 'checkin', d::text);

  streak := next_streak;
  doubloons := pay + bonus;
  week_completed := next_streak = 7;
  return_doubloons := ret_dob;
  return_miles := ret_miles;
  return next;
end $$;

-- resgate do desafio: precisa do check-in de hoje, da missão de hoje e do alvo batido
create or replace function public.claim_daily_challenge(p_mission text, p_progress integer)
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
  perform public.dobroes_credit_if_wallet(auth.uid(), pay, 'challenge', d::text);
  doubloons := pay;
  return next;
end $$;

revoke all on function public.daily_status() from public, anon;
revoke all on function public.daily_checkin() from public, anon;
revoke all on function public.claim_daily_challenge(text, integer) from public, anon;
grant execute on function public.daily_status() to authenticated;
grant execute on function public.daily_checkin() to authenticated;
grant execute on function public.claim_daily_challenge(text, integer) to authenticated;
