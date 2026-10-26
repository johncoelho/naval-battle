-- Desafio da semana (#47). Rode depois de economy.sql, daily.sql, online.sql, dobroes_saldo.sql e
-- test_accounts.sql. Idempotente. Migração: `weekly_challenge` (mesmo conteúdo).
--
-- Uma missão por semana (segunda 00h a domingo 23h59, Brasília), igual para todos, em rodízio fixo (nada
-- de sorteio, ECA Digital). Só missões que o SERVIDOR consegue provar; o aparelho não informa progresso:
--   ONLINE_PLAY_5 · 5 partidas online com combate (os dois lados deram tiro ou usaram habilidade, a mesma
--                   prova do award_online_match) em salas criadas na semana;
--   CHECKIN_5     · check-in do diário em 5 dias da semana (daily_claims kind = 'checkin');
--   RANKED_WIN_3  · 3 ranqueadas vencidas (online_matches.ranked com winner_id = eu).
-- Prêmio fixo e conhecido antes: weekly_doubloons + weekly_miles, uma vez por semana. Pago por
-- dobroes_apply(uid, valor, 'weekly', <segunda>): o razão é a trava de uma vez só e o pagamento NÃO entra
-- no teto de dobroes_online_daily_cap (que protege só reason = 'online'). Conta is_test não recebe; conta
-- sem carteira recebe no_wallet e nada é gravado (o app abre a carteira e tenta de novo).
-- Ordem de locks: weekly:<uid>, depois dobroes:<uid>, depois a linha de profiles (miles_refresh).
-- App antigo: não conhece estas RPCs e nada do diário muda.

insert into public.app_config (key, value) values
  ('weekly_enabled', 'true'),
  ('weekly_doubloons', '250'),
  ('weekly_miles', '5')
on conflict (key) do nothing;

-- segunda-feira da semana de Brasília
create or replace function public.brt_week_start()
returns date language sql stable as $$
  select public.brt_today() - (extract(isodow from public.brt_today())::integer - 1);
$$;

-- missão da semana: rodízio pelo número da semana desde 2026-01-05 (uma segunda-feira)
create or replace function public.weekly_mission(p_week date)
returns table (code text, target integer)
language sql immutable as $$
  select (array['ONLINE_PLAY_5', 'CHECKIN_5', 'RANKED_WIN_3'])[(((p_week - date '2026-01-05') / 7) % 3 + 3) % 3 + 1],
         (array[5, 5, 3])[(((p_week - date '2026-01-05') / 7) % 3 + 3) % 3 + 1];
$$;

create table if not exists public.weekly_claims (
  user_id    uuid        not null references auth.users(id) on delete cascade,
  week       date        not null,
  mission    text        not null,
  doubloons  integer     not null,
  miles      integer     not null,
  created_at timestamptz not null default now(),
  primary key (user_id, week)
);

alter table public.weekly_claims enable row level security;

drop policy if exists "semanal: ler o proprio" on public.weekly_claims;
create policy "semanal: ler o proprio"
  on public.weekly_claims for select
  using (auth.uid() = user_id);

revoke all on public.weekly_claims from anon, authenticated;
grant select on public.weekly_claims to authenticated;

-- índices da contagem por sala (a tabela não tinha nenhum por jogador); online_messages já tem
-- (match_id, sender_id, client_seq). CREATE INDEX normal, não CONCURRENTLY: o CONCURRENTLY não roda dentro de
-- transação (a migração roda em uma) e a tabela é pequena, então o bloqueio de escrita dura instantes.
create index if not exists online_matches_host_created_idx on public.online_matches (host_id, created_at);
create index if not exists online_matches_guest_created_idx on public.online_matches (guest_id, created_at);

-- progresso da missão da semana p_week para p_user (interna; não passa do alvo)
create or replace function public.weekly_progress_of(p_user uuid, p_week date)
returns integer
language plpgsql stable security definer set search_path = public as $$
declare
  m record;
  t0 timestamptz := p_week::timestamp at time zone 'America/Sao_Paulo';
  t1 timestamptz := (p_week + 7)::timestamp at time zone 'America/Sao_Paulo';
  n integer := 0;
begin
  select * into m from public.weekly_mission(p_week);
  if m.code = 'ONLINE_PLAY_5' then
    select count(*)::integer into n from (
      select om.id, om.host_id, om.guest_id from public.online_matches om
       where om.host_id = p_user and om.guest_id is not null and om.created_at >= t0 and om.created_at < t1
      union all
      select om.id, om.host_id, om.guest_id from public.online_matches om
       where om.guest_id = p_user and om.created_at >= t0 and om.created_at < t1
    ) s
    where exists (select 1 from public.online_messages x where x.match_id = s.id and x.sender_id = s.host_id
                    and (x.body like 'ACT|%' or x.body like 'ABIL|%'))
      and exists (select 1 from public.online_messages x where x.match_id = s.id and x.sender_id = s.guest_id
                    and (x.body like 'ACT|%' or x.body like 'ABIL|%'));
  elsif m.code = 'CHECKIN_5' then
    select count(*)::integer into n from public.daily_claims c
     where c.user_id = p_user and c.kind = 'checkin' and c.day >= p_week and c.day < p_week + 7;
  elsif m.code = 'RANKED_WIN_3' then
    -- ranqueada vencida COM combate dos dois lados (mesma prova da ONLINE_PLAY_5) e sem conflito de resultado
    select count(*)::integer into n from public.online_matches om
     where om.ranked and om.winner_id = p_user and om.created_at >= t0 and om.created_at < t1
       and om.guest_id is not null and (om.host_id = p_user or om.guest_id = p_user)
       and not coalesce(om.result_conflict, false)
       and exists (select 1 from public.online_messages x where x.match_id = om.id and x.sender_id = om.host_id
                     and (x.body like 'ACT|%' or x.body like 'ABIL|%'))
       and exists (select 1 from public.online_messages x where x.match_id = om.id and x.sender_id = om.guest_id
                     and (x.body like 'ACT|%' or x.body like 'ABIL|%'));
  end if;
  return least(n, m.target);
end $$;
revoke all on function public.weekly_progress_of(uuid, date) from public, anon, authenticated;

-- status do desafio da semana (uma linha; nenhuma se weekly_enabled = false)
drop function if exists public.weekly_status();
create or replace function public.weekly_status()
returns table (
  week date, mission text, target integer, progress integer, claimed boolean,
  doubloons integer, miles integer, resets_in_seconds integer
)
language plpgsql stable security definer set search_path = public as $$
#variable_conflict use_column
declare
  w date := public.brt_week_start();
begin
  if auth.uid() is null then
    raise exception 'weekly_auth';
  end if;
  if coalesce((select a.value from public.app_config a where a.key = 'weekly_enabled'), 'false') <> 'true' then
    return;
  end if;
  return query select
    w,
    m.code,
    m.target,
    public.weekly_progress_of(auth.uid(), w),
    exists (select 1 from public.weekly_claims c where c.user_id = auth.uid() and c.week = w),
    public.config_int('weekly_doubloons', 250),
    public.config_int('weekly_miles', 5),
    greatest(0, extract(epoch from (((w + 7)::timestamp at time zone 'America/Sao_Paulo') - now()))::integer)
  from public.weekly_mission(w) m;
end $$;

-- resgate: ok | already | not_done | no_wallet | not_eligible. `doubloons`/`miles` são o que foi pago de
-- fato (em 'already', o que foi pago naquela vez); `balance` é o saldo depois (0 quando não pagou).
create or replace function public.claim_weekly()
returns table (status text, doubloons integer, miles integer, balance integer)
language plpgsql security definer set search_path = public as $$
#variable_conflict use_column
declare
  uid uuid := auth.uid();
  w date := public.brt_week_start();
  m record;
  old public.weekly_claims%rowtype;
  pay integer;
  mi integer;
  bal integer;
  cur integer;
begin
  if uid is null then
    raise exception 'weekly_auth';
  end if;
  perform pg_advisory_xact_lock(hashtext('weekly:' || uid::text));

  select * into old from public.weekly_claims c where c.user_id = uid and c.week = w;
  if old.user_id is not null then
    return query select 'already'::text, old.doubloons, old.miles,
      coalesce((select p.credits from public.profiles p where p.id = uid), 0);
    return;
  end if;

  if coalesce((select a.value from public.app_config a where a.key = 'weekly_enabled'), 'false') <> 'true'
     or public.is_test_user(uid) then
    return query select 'not_eligible'::text, 0, 0, 0;
    return;
  end if;

  select * into m from public.weekly_mission(w);
  if public.weekly_progress_of(uid, w) < m.target then
    return query select 'not_done'::text, 0, 0, 0;
    return;
  end if;

  -- dobroes: antes de qualquer linha de profiles (miles_refresh), como no daily.sql e no welcome.sql
  perform pg_advisory_xact_lock(hashtext('dobroes:' || uid::text));
  if not public.dobroes_has_wallet(uid) then
    return query select 'no_wallet'::text, 0, 0, 0;
    return;
  end if;

  pay := public.config_int('weekly_doubloons', 250);
  bal := public.dobroes_apply(uid, pay, 'weekly', w::text);
  cur := public.miles_refresh(uid);
  update public.profiles
     set nautical_miles = greatest(cur, least(public.config_int('miles_cap', 50), cur + public.config_int('weekly_miles', 5)))
   where id = uid
  returning nautical_miles - cur into mi;
  insert into public.weekly_claims (user_id, week, mission, doubloons, miles)
  values (uid, w, m.code, pay, coalesce(mi, 0));

  return query select 'ok'::text, pay, coalesce(mi, 0), bal;
end $$;

revoke all on function public.weekly_status() from public, anon;
revoke all on function public.claim_weekly() from public, anon;
grant execute on function public.weekly_status() to authenticated;
grant execute on function public.claim_weekly() to authenticated;

notify pgrst, 'reload schema';
