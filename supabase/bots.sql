-- Naval Battle · adversário virtual (bot) na partida rápida, casual e ranqueada (issue #105, parte A1/B, servidor)
-- Rode no SQL Editor depois de schema.sql, online.sql, economy.sql (brt_today, config_int), season.sql,
-- test_accounts.sql (is_qa_account) e walkover.sql (ranked_snapshot). Idempotente.
-- Migração no banco: `bots_server` (mesmo conteúdo). Teste que desfaz tudo: supabase/tests/bots_ensaio.sql
-- (cole este arquivo e o ensaio na MESMA execução; o raise no fim cancela a transação inteira, DDL inclusive).
--
-- O que é: quando o jogador procura partida rápida e ninguém entra, o APP pode jogar contra um bot (a IA de
-- `Ai.kt`, no aparelho, com persona do pool do app). O bot não tem conta: não aparece em ranking, busca, amizade nem
-- `online_matches`. Este arquivo é só o que o servidor decide e guarda:
--   * a chave `bots_enabled` (kill switch, padrão 'false'; 'test' liga só as contas QA; 'true' liga para todos);
--   * `bot_matches`: uma linha por partida contra bot (telemetria e cotas, e a base do Elo da ranqueada);
--   * `cancel_quick_room`: fecha a sala `waiting` do anfitrião sem corrida com um humano que entra (ajuste A1);
--   * `bot_match_start`: o servidor emite a partida (relógio, rating do bot e nível calculados aqui, ajuste A2);
--   * `record_ranked_bot_result` + `apply_ranked_bot_side`: o Elo do próprio jogador, com tetos (A3, A4);
--   * `bot_match_report`: fecha a partida casual; `bot_wait_info`: o app lê esperas e cotas; `purge_bots`.
--
-- EXCEÇÃO CONSCIENTE à regra "o servidor é autoritativo" (HISTORY: contra a IA o servidor não vê a partida) e à
-- soma zero da ranqueada (#6): a ranqueada contra bot altera só o Elo de quem jogou, o resultado é AUTODECLARADO
-- pelo aparelho e o servidor julga o que dá para julgar: partida emitida por ele, vitória só depois de
-- `bot_ranked_min_seconds` do relógio dele, relato uma vez só, cota contada no início (não no fim), partida
-- aberta há mais de `bot_open_timeout_minutes` fecha como DERROTA, delta cortado em `bot_ranked_delta_pct` % do
-- normal e no teto `bot_ranked_max_delta`, e no máximo `bot_ranked_total_limit` ranqueadas contra bot na vida da
-- conta. Teto de adulteração: 10 partidas x 16 pontos = 160 pontos de Elo por conta, no total (e a conta nunca
-- mexe em dobrão, milha, desafio, prêmio nem no Elo de outra pessoa).
--
-- Compatibilidade: nenhuma função existente muda de assinatura e `apply_ranked_side` NÃO é tocada (acabou de ser
-- validada na #85). App antigo nunca chama as funções novas. Aplicar o servidor ANTES do app novo.
--
-- Privacidade (política do #57, inventário em site/privacy.html e README → Especificação técnica):
--  * `bot_matches` tem user_id, então: RLS ligado SEM política, `revoke all` de anon e authenticated, escrita só
--    pelas funções abaixo (definer), leitura só service_role. O app não lê nem escreve a tabela.
--  * Só o necessário para as cotas e a medição: modo, nível, persona (texto curto, nunca exibido pelo servidor),
--    rating, horários, resultado, pontos e versão do app. Sem texto livre, aparelho nem IP.
--  * Retenção de 90 dias: `purge_bots()` na rodada diária da esteira (como `purge_activity()`).
--    `on delete cascade` em auth.users apaga a conta e o histórico dela.
--  * A tabela grava TODAS as contas que o servidor deixa jogar (a leitura exclui teste e internas).
--
-- Ponto de encaixe da #97 (cabeçalho de behavior.sql): a view `behavior_dias_so_bot` abaixo separa o dia em que a
-- conta só jogou contra bot (sem partida humana) do dia de partida humana; nada em `activity_days` muda.
--
-- Desfazer na produção:
--   drop view if exists public.behavior_dias_so_bot;
--   drop function if exists public.purge_bots();
--   drop function if exists public.bot_match_report(uuid, boolean);
--   drop function if exists public.record_ranked_bot_result(uuid, boolean);
--   drop function if exists public.apply_ranked_bot_side(uuid, boolean);
--   drop function if exists public.bot_match_start(text, uuid, text, text);
--   drop function if exists public.cancel_quick_room(uuid);
--   drop function if exists public.bot_wait_info();
--   drop function if exists public.bot_close_stale(uuid);
--   drop function if exists public.bots_on_for(uuid);
--   drop table if exists public.bot_matches;
--   delete from public.app_config where key = 'bots_enabled' or key like 'bot\_%' escape '\';

-- ------------------------------------------------------------------ números e chave

insert into public.app_config (key, value) values
  ('bots_enabled', 'false'),                -- 'false' desliga, 'test' só contas QA, 'true' liga (com bot_percent)
  ('bot_percent', '100'),                   -- com 'true': percentual de contas (0 a 100) que o bot alcança
  ('bot_wait_seconds', '30'),               -- espera na casual antes do bot (lida pelo app em bot_wait_info)
  ('bot_wait_seconds_ranked', '45'),        -- espera na ranqueada
  ('bot_max_per_day', '10'),                -- partidas contra bot por dia (Brasília), casual + ranqueada
  ('bot_ranked_daily_limit', '3'),          -- ranqueadas contra bot por dia
  ('bot_ranked_total_limit', '10'),         -- ranqueadas contra bot na vida da conta (teto de adulteração)
  ('bot_ranked_delta_pct', '50'),           -- % do delta normal do Elo contra bot
  ('bot_ranked_max_delta', '16'),           -- teto duro do delta por partida
  ('bot_rating_spread', '100'),             -- desvio máximo do rating do bot em torno do jogador
  ('bot_ranked_min_seconds', '75'),         -- vitória ranqueada só depois disto (relógio do servidor)
  ('bot_open_timeout_minutes', '30'),       -- ranqueada aberta além disto fecha como derrota
  ('bot_level_medium_matches', '5'),        -- menos partidas que isto = nível fácil
  ('bot_level_hard_matches', '15'),         -- a partir daqui, com vitórias suficientes, nível difícil
  ('bot_level_hard_winpct', '55'),          -- % de vitórias para o difícil
  ('bot_level_hard_rating', '1100')         -- ou rating ranqueado a partir daqui (com as partidas acima)
on conflict (key) do nothing;

-- ------------------------------------------------------------------ tabela

create table if not exists public.bot_matches (
  id          uuid        primary key default gen_random_uuid(),
  user_id     uuid        not null references auth.users(id) on delete cascade,
  room_id     uuid,                                  -- a sala rápida cancelada (medição #94); sem FK: a sala some sozinha
  mode        text        not null check (mode in ('CLASSIC', 'TACTICAL')),
  ranked      boolean     not null default false,
  level       text        not null check (level in ('easy', 'medium', 'hard')),
  persona     text        check (persona is null or char_length(persona) <= 40),
  bot_rating  integer     not null,                  -- força geral do bot (ranked_rating do jogador + desvio)
  bot_season  integer     not null,                  -- força na temporada (pontos do jogador + o mesmo desvio)
  started_at  timestamptz not null default now(),    -- relógio do servidor
  ended_at    timestamptz,
  status      text        not null default 'open' check (status in ('open', 'closed', 'expired')),
  won         boolean,
  delta_s     integer,
  delta_o     integer,
  app_version text
);
create index if not exists bot_matches_user_idx on public.bot_matches (user_id, started_at desc);
create index if not exists bot_matches_started_idx on public.bot_matches (started_at);
-- uma sala cancelada gera no máximo uma partida contra bot
create unique index if not exists bot_matches_room_key on public.bot_matches (room_id) where room_id is not null;

alter table public.bot_matches enable row level security;
revoke all on public.bot_matches from anon, authenticated;

-- ------------------------------------------------------------------ quem vê

-- Bot ligado para esta conta. 'true' liga para todos (menos robôs de teste, exceto QA) dentro do percentual
-- `bot_percent`; 'test' liga só para as contas QA; qualquer outro valor desliga. Mesmo padrão de `ads_on_for`.
create or replace function public.bots_on_for(p_user uuid)
returns boolean language sql stable security definer set search_path = public as $$
  select p_user is not null
         and case coalesce((select value from public.app_config where key = 'bots_enabled'), 'false')
           when 'true' then (not public.is_test_user(p_user) or public.is_qa_account(p_user))
                            and (abs(hashtext(p_user::text)::bigint) % 100)
                                < greatest(0, least(100, public.config_int('bot_percent', 100)))
           when 'test' then public.is_qa_account(p_user)
           else false
         end;
$$;
revoke all on function public.bots_on_for(uuid) from public, anon, authenticated;

-- ------------------------------------------------------------------ Elo contra o bot

-- Mesma fórmula de `apply_ranked_side` (K = 32, esperado pelo Elo), só do lado do jogador: o adversário é a
-- força gravada em bot_matches (jogador + desvio). Delta = base x bot_ranked_delta_pct / 100, cortado em
-- ±bot_ranked_max_delta. Com pct = 100 e teto >= 32 o delta é igual ao de `apply_ranked_side` (o ensaio prova).
-- Interna: quem chama (record_ranked_bot_result) já travou a linha, conferiu dono, passe e conta de teste.
create or replace function public.apply_ranked_bot_side(p_bot_id uuid, p_won boolean)
returns table (
  points_delta integer, season_points integer, season_position integer, season_name text,
  season_matches integer, season_wins integer, base_points integer, bonus_points integer
)
language plpgsql security definer set search_path = public as $$
declare
  b public.bot_matches%rowtype;
  szn text;
  szn_label text;
  my_season integer;
  my_overall integer;
  base_s integer;
  base_o integer;
  d_s integer;
  d_o integer;
  pct integer := greatest(0, public.config_int('bot_ranked_delta_pct', 50));
  cap integer := greatest(0, public.config_int('bot_ranked_max_delta', 16));
  uname text;
  sn record;
begin
  select * into b from public.bot_matches bm where bm.id = p_bot_id;
  if b.id is null then
    return;
  end if;
  select c.season_key, c.name into szn, szn_label from public.current_season() c;
  season_name := szn_label;

  select s.points into my_season from public.ranked_season_stats s where s.user_id = b.user_id and s.season_key = szn;
  select p.ranked_rating, p.username into my_overall, uname from public.profiles p where p.id = b.user_id;
  my_season := coalesce(my_season, 1000);
  my_overall := coalesce(my_overall, 1000);

  base_s := round(32 * ((case when p_won then 1 else 0 end)
    - 1.0 / (1 + power(10.0, (b.bot_season - my_season) / 400.0))));
  base_o := round(32 * ((case when p_won then 1 else 0 end)
    - 1.0 / (1 + power(10.0, (b.bot_rating - my_overall) / 400.0))));

  d_s := greatest(-cap, least(cap, round(base_s * pct / 100.0)::integer));
  d_o := greatest(-cap, least(cap, round(base_o * pct / 100.0)::integer));

  update public.profiles p
     set ranked_rating = greatest(0, p.ranked_rating + d_o),
         ranked_matches = p.ranked_matches + 1,
         ranked_wins = p.ranked_wins + case when p_won then 1 else 0 end
   where p.id = b.user_id;

  insert into public.ranked_season_stats as s (user_id, season_key, username, points, matches, wins)
  values (b.user_id, szn, coalesce(uname, ''), greatest(0, 1000 + d_s), 1, case when p_won then 1 else 0 end)
  on conflict (user_id, season_key) do update
    set points = greatest(0, s.points + d_s),
        matches = s.matches + 1,
        wins = s.wins + case when p_won then 1 else 0 end,
        username = excluded.username,
        updated_at = now();

  select * into sn from public.ranked_snapshot(b.user_id, szn);
  season_points := sn.points;
  season_matches := sn.matches;
  season_wins := sn.wins;
  season_position := sn.pos;
  points_delta := d_s;
  base_points := d_s;
  bonus_points := 0;
  -- grava os dois deltas na partida (o geral não volta no retorno, que espelha o de apply_ranked_side)
  update public.bot_matches bm set delta_s = d_s, delta_o = d_o where bm.id = p_bot_id;
  return next;
end $$;
revoke all on function public.apply_ranked_bot_side(uuid, boolean) from public, anon, authenticated;

-- ------------------------------------------------------------------ fechar partida vencida (derrota imposta)

-- Fecha como DERROTA toda ranqueada aberta do jogador há mais de bot_open_timeout_minutes (ajuste A3): abandono
-- é derrota, agora imposto no servidor, e quem abre várias e só relata as vencidas não ganha nada com isso.
-- A casual aberta vencida só vira `expired` (sem ponto). Devolve quantas fechou. Interna.
create or replace function public.bot_close_stale(p_user uuid)
returns integer language plpgsql security definer set search_path = public as $$
declare
  r record;
  n integer := 0;
  lim interval := make_interval(mins => greatest(public.config_int('bot_open_timeout_minutes', 30), 5));
begin
  for r in
    select bm.id, bm.ranked from public.bot_matches bm
     where bm.user_id = p_user and bm.status = 'open' and bm.started_at < now() - lim
       for update
  loop
    if r.ranked and not public.is_test_user(p_user)
       and (to_regclass('public.season_passes') is null
            or exists (select 1 from public.season_passes sp, public.current_season() c
                        where sp.user_id = p_user and sp.season_key = c.season_key)) then
      perform public.apply_ranked_bot_side(r.id, false);
    end if;
    update public.bot_matches bm set status = 'expired', ended_at = now(), won = false where bm.id = r.id;
    n := n + 1;
  end loop;
  return n;
end $$;
revoke all on function public.bot_close_stale(uuid) from public, anon, authenticated;

-- ------------------------------------------------------------------ o que o app lê

-- Esperas e cotas para a busca atual. Só leitura (não conta nada). `on` = bots_on_for e conta que pode jogar
-- contra bot (conta de teste fora); casual_ok / ranked_ok já consideram as cotas do dia e da vida.
create or replace function public.bot_wait_info()
returns table (bot_on boolean, wait_casual integer, wait_ranked integer, casual_ok boolean, ranked_ok boolean)
language plpgsql stable security definer set search_path = public as $$
declare
  me uuid := auth.uid();
  used_day integer;
  ranked_day integer;
  ranked_all integer;
  eligible boolean;
  has_pass boolean := true;
begin
  bot_on := false; wait_casual := public.config_int('bot_wait_seconds', 30);
  wait_ranked := public.config_int('bot_wait_seconds_ranked', 45);
  casual_ok := false; ranked_ok := false;
  if me is null or not public.bots_on_for(me) then
    return next;
    return;
  end if;
  eligible := not (public.is_test_user(me) and not public.is_qa_account(me));
  if to_regclass('public.season_passes') is not null then
    select exists (select 1 from public.season_passes sp, public.current_season() c
                    where sp.user_id = me and sp.season_key = c.season_key) into has_pass;
  end if;
  select count(*) filter (where (bm.started_at at time zone 'America/Sao_Paulo')::date = public.brt_today()),
         count(*) filter (where bm.ranked and (bm.started_at at time zone 'America/Sao_Paulo')::date = public.brt_today()),
         count(*) filter (where bm.ranked)
    into used_day, ranked_day, ranked_all
    from public.bot_matches bm where bm.user_id = me;
  bot_on := eligible;
  casual_ok := eligible and used_day < public.config_int('bot_max_per_day', 10);
  ranked_ok := casual_ok and has_pass and not public.is_test_user(me)
               and ranked_day < public.config_int('bot_ranked_daily_limit', 3)
               and ranked_all < public.config_int('bot_ranked_total_limit', 10);
  return next;
end $$;
revoke all on function public.bot_wait_info() from public, anon;
grant execute on function public.bot_wait_info() to authenticated;

-- ------------------------------------------------------------------ cancelar a sala (A1)

-- O anfitrião desiste de esperar para jogar contra o bot. A sala `waiting` só fecha se ninguém entrou: o
-- `for update` serializa com `join_online_match` (que também atualiza a linha). Devolve:
--   cancelled  a sala foi fechada agora (ou já estava cancelada por ele): pode pedir bot_match_start
--   joined     um humano entrou na frente: a partida é humana, o app NÃO inicia o bot
--   invalid    sem sessão, sala que não é dele, que não é de partida rápida aberta a qualquer um, ou inexistente
-- O bot nunca coexiste com sala viva do mesmo jogador: sem esta função, o humano que entrasse no instante em que
-- o bot começa ganharia WO em 60 s (#85) e o anfitrião levaria derrota cheia.
create or replace function public.cancel_quick_room(p_match_id uuid)
returns table (outcome text) language plpgsql security definer set search_path = public as $$
declare
  me uuid := auth.uid();
  m public.online_matches%rowtype;
begin
  if me is null then
    outcome := 'invalid'; return next; return;
  end if;
  select * into m from public.online_matches om where om.id = p_match_id for update;
  if m.id is null or m.host_id <> me or not m.is_quick_match or m.invited_id is not null then
    outcome := 'invalid'; return next; return;
  end if;
  if m.guest_id is not null or m.status = 'active' or m.status = 'finished' then
    outcome := 'joined'; return next; return;
  end if;
  if m.status = 'waiting' then
    update public.online_matches om set status = 'abandoned' where om.id = p_match_id;
    outcome := 'cancelled'; return next; return;
  end if;
  -- já cancelada (abandoned sem convidado): repetir é inofensivo
  outcome := 'cancelled'; return next; return;
end $$;
revoke all on function public.cancel_quick_room(uuid) from public, anon;
grant execute on function public.cancel_quick_room(uuid) to authenticated;

-- ------------------------------------------------------------------ emitir a partida (A2)

-- O servidor emite a partida contra bot DEPOIS de `cancel_quick_room` = cancelled. Valida chave, conta, sala
-- (cancelada por este jogador, rápida, sem convidado, há pouco, ainda sem bot), cotas e, na ranqueada, passe da
-- temporada. Grava `started_at = now()` (relógio do servidor), sorteia o desvio do rating e calcula o nível pelo
-- histórico: o cliente não escolhe adversário fácil. A persona (texto curto) só entra para a medição.
-- outcome: ok | off (chave desligada, conta fora ou de teste) | limit (cota) | invalid (sala/modo) | busy (outra sala
-- viva do jogador). Só `ok` traz id, bot_rating e level.
drop function if exists public.bot_match_start(text, uuid, text, text);
create function public.bot_match_start(
  p_mode text, p_room uuid, p_persona text default null, p_app_version text default null
)
returns table (outcome text, bot_id uuid, bot_rating integer, level text, ranked boolean)
language plpgsql security definer set search_path = public as $$
declare
  me uuid := auth.uid();
  m public.online_matches%rowtype;
  my_overall integer;
  my_season integer;
  szn text;
  spread integer := greatest(0, public.config_int('bot_rating_spread', 100));
  dev integer;
  used_day integer;
  ranked_day integer;
  ranked_all integer;
  played integer;
  wins_n integer;
  lvl text;
  new_id uuid;
  has_pass boolean := true;
begin
  outcome := 'off'; bot_id := null; bot_rating := null; level := null; ranked := false;
  if me is null or not public.bots_on_for(me) then
    return next; return;
  end if;
  -- conta de teste não joga contra bot (exceto a conta QA, que roda com is_test = false durante o teste)
  if public.is_test_user(me) and not public.is_qa_account(me) then
    return next; return;
  end if;
  if p_room is null then
    outcome := 'invalid'; return next; return;
  end if;

  select * into m from public.online_matches om where om.id = p_room for update;
  if m.id is null or m.host_id <> me or not m.is_quick_match or m.invited_id is not null
     or m.guest_id is not null or m.status <> 'abandoned'
     or m.updated_at < now() - interval '2 minutes'
     or exists (select 1 from public.bot_matches bm where bm.room_id = p_room) then
    outcome := 'invalid'; return next; return;
  end if;
  if p_mode is not null and p_mode <> m.mode then
    outcome := 'invalid'; return next; return;
  end if;
  -- nunca com sala viva do mesmo jogador (a #85 daria WO ao humano que entrasse)
  if exists (select 1 from public.online_matches om
              where om.id <> p_room and om.status = 'waiting' and om.host_id = me
                and om.updated_at > now() - interval '10 minutes')
     or exists (select 1 from public.online_matches om
                 where om.status = 'active' and (om.host_id = me or om.guest_id = me)
                   and om.updated_at > now() - interval '30 minutes') then
    outcome := 'busy'; return next; return;
  end if;

  perform public.bot_close_stale(me);

  select count(*) filter (where (bm.started_at at time zone 'America/Sao_Paulo')::date = public.brt_today()),
         count(*) filter (where bm.ranked and (bm.started_at at time zone 'America/Sao_Paulo')::date = public.brt_today()),
         count(*) filter (where bm.ranked)
    into used_day, ranked_day, ranked_all
    from public.bot_matches bm where bm.user_id = me;
  if used_day >= public.config_int('bot_max_per_day', 10) then
    outcome := 'limit'; return next; return;
  end if;
  if m.ranked then
    if public.is_test_user(me) then
      outcome := 'off'; return next; return;
    end if;
    select c.season_key into szn from public.current_season() c;
    if to_regclass('public.season_passes') is not null then
      select exists (select 1 from public.season_passes sp where sp.user_id = me and sp.season_key = szn) into has_pass;
    end if;
    if not has_pass then
      outcome := 'off'; return next; return;
    end if;
    if ranked_day >= public.config_int('bot_ranked_daily_limit', 3)
       or ranked_all >= public.config_int('bot_ranked_total_limit', 10) then
      outcome := 'limit'; return next; return;
    end if;
  end if;

  select c.season_key into szn from public.current_season() c;
  select p.ranked_rating, p.matches, p.wins into my_overall, played, wins_n from public.profiles p where p.id = me;
  select s.points into my_season from public.ranked_season_stats s where s.user_id = me and s.season_key = szn;
  my_overall := coalesce(my_overall, 1000);
  my_season := coalesce(my_season, 1000);
  played := coalesce(played, 0);
  wins_n := coalesce(wins_n, 0);
  dev := floor(random() * (2 * spread + 1))::integer - spread;

  -- nível pelo histórico: sem histórico = fácil; veterano de bom aproveitamento ou rating alto = difícil
  lvl := case
    when played < public.config_int('bot_level_medium_matches', 5) then 'easy'
    when played >= public.config_int('bot_level_hard_matches', 15)
         and (wins_n * 100 >= played * public.config_int('bot_level_hard_winpct', 55)
              or my_overall >= public.config_int('bot_level_hard_rating', 1100)) then 'hard'
    else 'medium'
  end;

  insert into public.bot_matches (user_id, room_id, mode, ranked, level, persona, bot_rating, bot_season, app_version)
  values (me, p_room, m.mode, m.ranked, lvl, case when p_persona ~ '^[[:alnum:]_ .-]{1,40}$' then p_persona end,
          greatest(0, my_overall + dev), greatest(0, my_season + dev), left(p_app_version, 20))
  returning bot_matches.id into new_id;

  outcome := 'ok';
  bot_id := new_id;
  bot_rating := greatest(0, my_overall + dev);
  level := lvl;
  ranked := m.ranked;
  return next;
end $$;
revoke all on function public.bot_match_start(text, uuid, text, text) from public, anon;
grant execute on function public.bot_match_start(text, uuid, text, text) to authenticated;

-- ------------------------------------------------------------------ relato da ranqueada (A4)

-- O jogador diz como terminou a ranqueada contra bot. Julgamento do servidor:
--   invalid     sem sessão, partida que não é dele, que não existe ou que não é ranqueada
--   repeat      já fechada: devolve o que ficou gravado, sem mexer em nada (idempotente)
--   too_short   vitória antes de `bot_ranked_min_seconds` do relógio do servidor: nada muda e a linha segue
--               aberta (vira derrota pelo prazo); a DERROTA vale em qualquer duração (não há ganho a evitar)
--   no_points   conta de teste ou sem passe: fecha sem pontuar
--   ok          pontos aplicados (delta cortado), linha fechada
-- Mais de 2 h de partida conta como derrota, qualquer que seja o relato.
create or replace function public.record_ranked_bot_result(p_id uuid, p_won boolean)
returns table (
  outcome text, points_delta integer, season_points integer, season_position integer, season_name text,
  season_matches integer, season_wins integer, accepted boolean, base_points integer, bonus_points integer
)
language plpgsql security definer set search_path = public as $$
declare
  me uuid := auth.uid();
  b public.bot_matches%rowtype;
  is_win boolean := coalesce(p_won, false);
  secs numeric;
  szn text;
  szn_label text;
  scored boolean := true;
  r record;
begin
  accepted := false; points_delta := 0; base_points := 0; bonus_points := 0;
  if me is null then
    outcome := 'invalid'; return next; return;
  end if;
  select * into b from public.bot_matches bm where bm.id = p_id for update;
  if b.id is null or b.user_id <> me or not b.ranked then
    outcome := 'invalid'; return next; return;
  end if;
  select c.season_key, c.name into szn, szn_label from public.current_season() c;
  season_name := szn_label;

  if b.status <> 'open' then
    outcome := 'repeat';
    points_delta := coalesce(b.delta_s, 0);
    base_points := points_delta;
    select sn.points, sn.matches, sn.wins, sn.pos into season_points, season_matches, season_wins, season_position
      from public.ranked_snapshot(me, szn) sn;
    return next; return;
  end if;

  secs := extract(epoch from (now() - b.started_at));
  if secs > 7200 then
    is_win := false;
  end if;
  if is_win and secs < greatest(public.config_int('bot_ranked_min_seconds', 75), 0) then
    outcome := 'too_short';
    select sn.points, sn.matches, sn.wins, sn.pos into season_points, season_matches, season_wins, season_position
      from public.ranked_snapshot(me, szn) sn;
    return next; return;
  end if;

  if public.is_test_user(me) then
    scored := false;
  elsif to_regclass('public.season_passes') is not null
        and not exists (select 1 from public.season_passes sp where sp.user_id = me and sp.season_key = szn) then
    scored := false;
  end if;

  if not scored then
    update public.bot_matches bm set status = 'closed', ended_at = now(), won = is_win, delta_s = 0, delta_o = 0
     where bm.id = p_id;
    outcome := 'no_points';
    select sn.points, sn.matches, sn.wins, sn.pos into season_points, season_matches, season_wins, season_position
      from public.ranked_snapshot(me, szn) sn;
    return next; return;
  end if;

  select * into r from public.apply_ranked_bot_side(p_id, is_win);
  update public.bot_matches bm set status = 'closed', ended_at = now(), won = is_win where bm.id = p_id;
  outcome := 'ok';
  points_delta := r.points_delta;
  season_points := r.season_points;
  season_position := r.season_position;
  season_matches := r.season_matches;
  season_wins := r.season_wins;
  base_points := r.base_points;
  bonus_points := r.bonus_points;
  accepted := true;
  return next;
end $$;
revoke all on function public.record_ranked_bot_result(uuid, boolean) from public, anon;
grant execute on function public.record_ranked_bot_result(uuid, boolean) to authenticated;

-- ------------------------------------------------------------------ relato da casual

-- Fecha a partida casual contra bot (só telemetria: sem ponto, dobrão, milha nem desafio). Idempotente.
-- Devolve: ok | repeat | invalid | ranked (a ranqueada fecha por record_ranked_bot_result).
create or replace function public.bot_match_report(p_id uuid, p_won boolean)
returns table (outcome text) language plpgsql security definer set search_path = public as $$
declare
  me uuid := auth.uid();
  b public.bot_matches%rowtype;
begin
  if me is null then
    outcome := 'invalid'; return next; return;
  end if;
  select * into b from public.bot_matches bm where bm.id = p_id for update;
  if b.id is null or b.user_id <> me then
    outcome := 'invalid'; return next; return;
  end if;
  if b.ranked then
    outcome := 'ranked'; return next; return;
  end if;
  if b.status <> 'open' then
    outcome := 'repeat'; return next; return;
  end if;
  update public.bot_matches bm set status = 'closed', ended_at = now(), won = coalesce(p_won, false), delta_s = 0, delta_o = 0
   where bm.id = p_id;
  outcome := 'ok'; return next; return;
end $$;
revoke all on function public.bot_match_report(uuid, boolean) from public, anon;
grant execute on function public.bot_match_report(uuid, boolean) to authenticated;

-- ------------------------------------------------------------------ retenção

-- 90 dias. A rodada diária da esteira chama `select public.purge_bots();` (como `purge_activity()`); devolve
-- quantas linhas apagou.
create or replace function public.purge_bots()
returns bigint language plpgsql security definer set search_path = public as $$
declare n bigint;
begin
  delete from public.bot_matches where started_at < now() - interval '90 days';
  get diagnostics n = row_count;
  return n;
end $$;
revoke all on function public.purge_bots() from public, anon, authenticated;
grant execute on function public.purge_bots() to service_role;

-- ------------------------------------------------------------------ medição (ponto de encaixe da #97)

-- Dias em que a conta abriu o app (activity_days) e jogou contra bot mas NÃO teve partida humana: separa "voltou
-- para jogar" de "voltou e só enfrentou bot". Agregado por dia, célula pequena (menos de activity_min_cell) vira
-- nula, conta de teste e interna fora. Só existe se behavior.sql já foi aplicado.
do $$
begin
  if to_regclass('public.activity_days') is not null then
    execute $v$
      create or replace view public.behavior_dias_so_bot
      with (security_invoker = true) as
      with so_bot as (
        select d.day, d.user_id
          from public.activity_days d
          join public.profiles p on p.id = d.user_id
         where not coalesce(p.is_test, false) and not public.is_internal_account(d.user_id)
           and exists (select 1 from public.bot_matches b
                        where b.user_id = d.user_id
                          and (b.started_at at time zone 'America/Sao_Paulo')::date = d.day)
           and not exists (select 1 from public.online_matches om
                            where (om.host_id = d.user_id or om.guest_id = d.user_id)
                              and om.guest_id is not null and om.status in ('active', 'finished', 'abandoned')
                              and (om.created_at at time zone 'America/Sao_Paulo')::date = d.day)
      ),
      g as (select day, count(*) as n from so_bot group by day)
      select g.day as dia,
             case when g.n >= public.config_int('activity_min_cell', 5) then g.n end as contas_so_bot
        from g order by g.day
    $v$;
    execute 'revoke all on public.behavior_dias_so_bot from public, anon, authenticated';
    execute 'grant select on public.behavior_dias_so_bot to service_role';
  end if;
end $$;
