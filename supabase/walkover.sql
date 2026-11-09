-- Naval Battle · vitória por ausência (WO) na partida online (issue #85, 0.44.0)
-- Rode no SQL Editor depois de schema.sql, online.sql, safety.sql, economy.sql e season.sql.
-- Idempotente: rodar de novo não duplica nada.
-- Teste que desfaz tudo: supabase/tests/walkover_ensaio.sql (cole este arquivo e o ensaio na MESMA
-- execução). O Elo da ranqueada tem ensaio próprio, supabase/tests/ranked_elo_ensaio.sql, que roda
-- antes e depois deste arquivo e tem de dar o mesmo resultado nos casos de relato normal.
--
-- O problema: o adversário aceita o balão (ou o convite) e a sala vira `active`, mas ele nunca entra
-- na partida, ou porque fechou o app logo depois, ou porque a sala era fantasma (o anfitrião saiu e a
-- sala `waiting` seguiu aberta por 10 minutos). Quem ficou esperava para sempre no posicionamento.
--
-- A regra, toda no servidor e pelo relógio do servidor:
-- 1. A sala `active` guarda `joined_at` (o instante do join). Sala anterior a esta migração fica sem
--    `joined_at` e fora da regra.
-- 2. SINAL de um comandante = a presença dele (`host/guest_seen_at`) ou uma linha dele em
--    `online_messages` posterior ao `joined_at`. Mensagem conta porque o app antigo, sem presença,
--    manda a frota (FLEET) ao terminar de posicionar.
-- 3. Passados `walkover_seconds` (60) do `joined_at` sem sinal do adversário, quem ficou vence por
--    ausência: `resolve_walkover` grava a sala como `abandoned`, `winner_id` = quem ficou,
--    `walkover` = true e as duas flags `*_result_recorded`. Escreve as linhas `QUIT|WO` (sender = o
--    ausente) e `DROP|WO` (sender = o vencedor): o app antigo de quem ficou já trata QUIT e o do
--    ausente, ao voltar, já trata DROP, então o essencial não pede app novo.
-- 4. Sem cron: o próprio `online_heartbeat` de quem ficou (a cada 3 s) chama `resolve_walkover` quando
--    o prazo vence. O app novo também chama a RPC no fim da contagem, para receber o resultado.
-- 5. Reconexão: se o primeiro sinal do ausente chegar antes de a RPC pegar a trava da sala, não há WO
--    (a checagem roda sob `for update`, então heartbeat e claim simultâneos dão um resultado só).
-- 6. Ranqueada: o ausente leva derrota cheia e quem ficou ganha o Elo normal (soma zero, pela força de
--    ANTES), no máximo `ranked_wo_daily_pair_limit` (1) WO pontuado por par por dia; sem XP, dobrões,
--    milha nem desafio (regra da #6: esses prêmios exigem combate dos dois lados). Conta de teste ou
--    sem passe: vitória sem pontos. Casual e sala de amigo: vitória no histórico, sem pontos.
-- 7. A milha gasta para entrar volta a quem ficou, uma vez por sala (`mile_ledger` kind 'refund').
-- 8. Sala fantasma na origem: o anfitrião passa a bater presença já em `waiting` e `find_quick_offer`
--    esconde a sala cujo anfitrião sumiu há mais de `quick_host_fresh_seconds` (20). `host_seen_at`
--    nulo (app antigo, que não bate em `waiting`) segue com a regra de 10 minutos.
-- 9. Varredura: sala `active` sem sinal de NENHUM dos dois por 2x o prazo vira `abandoned` sem
--    vencedor nem pontos (dentro de `find_quick_offer`).
--
-- Compatibilidade: só colunas e RPCs novas mais substituições com a mesma assinatura. Aplicar o
-- servidor ANTES do app novo (README → Release).

-- ------------------------------------------------------------------ colunas e números

alter table public.online_matches add column if not exists joined_at timestamptz;
alter table public.online_matches add column if not exists walkover boolean not null default false;

-- a varredura e o limite de WO leem só salas ativas / de WO
create index if not exists online_matches_active_joined_idx
  on public.online_matches (joined_at) where status = 'active';

insert into public.app_config (key, value) values
  ('walkover_seconds', '60'),
  ('ranked_wo_daily_pair_limit', '1'),
  ('quick_host_fresh_seconds', '20')
on conflict (key) do nothing;

-- razão de milhas ganha o tipo 'refund' (a milha gasta que volta a quem ficou). A chave
-- (user_id, match_id, kind) já garante uma devolução por sala.
do $$
declare c text;
begin
  for c in
    select con.conname from pg_constraint con
     where con.conrelid = 'public.mile_ledger'::regclass and con.contype = 'c'
       and pg_get_constraintdef(con.oid) like '%kind%'
  loop
    execute format('alter table public.mile_ledger drop constraint %I', c);
  end loop;
  alter table public.mile_ledger
    add constraint mile_ledger_kind_check check (kind in ('spend', 'reward', 'refund'));
end $$;

-- ------------------------------------------------------------------ sinal de presença

-- O comandante deu sinal nesta sala depois da entrada? Interna: só as RPCs abaixo chamam.
create or replace function public.room_has_signal(p_match_id uuid, p_user uuid)
returns boolean language sql security definer set search_path = public as $$
  select exists (
    select 1 from public.online_matches m
     where m.id = p_match_id and m.joined_at is not null
       and ((m.host_id = p_user and m.host_seen_at > m.joined_at)
         or (m.guest_id = p_user and m.guest_seen_at > m.joined_at))
  ) or exists (
    select 1 from public.online_messages om
      join public.online_matches m on m.id = om.match_id
     where om.match_id = p_match_id and om.sender_id = p_user
       and m.joined_at is not null and om.created_at > m.joined_at
  );
$$;
revoke all on function public.room_has_signal(uuid, uuid) from public, anon, authenticated;

-- ------------------------------------------------------------------ placar da temporada

-- Pontos, partidas, vitórias e posição de um comandante na temporada (conta de teste fora do
-- placar). Sem linha de temporada devolve nada. Interna.
create or replace function public.ranked_snapshot(p_user uuid, p_szn text)
returns table (points integer, matches integer, wins integer, pos integer)
language sql security definer set search_path = public as $$
  select s.points, s.matches, s.wins,
         (select x.pos::integer from (
            select r.user_id, row_number() over (
              order by r.points desc, r.wins desc,
                       (r.wins::numeric / greatest(r.matches, 1)) desc, r.updated_at asc
            ) as pos
            from public.ranked_season_stats r
            left join public.profiles p on p.id = r.user_id
            where r.season_key = p_szn and not coalesce(p.is_test, false)
          ) x where x.user_id = p_user)
    from public.ranked_season_stats s
   where s.user_id = p_user and s.season_key = p_szn;
$$;
revoke all on function public.ranked_snapshot(uuid, text) from public, anon, authenticated;

-- ------------------------------------------------------------------ Elo de um lado

-- O miolo de record_ranked_result (online.sql), extraído sem mudar a conta para os dois lados
-- poderem usá-lo (relato de cada lado e resolve_walkover). Quem chama já trava a sala (`for update`),
-- confere participante, conta de teste e passe, e grava o vencedor e as flags. Aqui só:
-- 1. força de antes dos dois (desconta o que o outro lado já levou desta sala);
-- 2. Elo esperado/base, soma zero;
-- 3. anti-farm: `ranked_daily_pair_limit` ranqueadas por dia por par; e, em WO (p_wo), também
--    `ranked_wo_daily_pair_limit` WO pontuado por dia por par — excedido, a partida não vale pontos
--    nem conta no placar;
-- 4. grava os deltas da sala, atualiza profiles e ranked_season_stats e devolve o estado.
create or replace function public.apply_ranked_side(
  p_match_id uuid, p_user uuid, p_won boolean, p_wo boolean default false
)
returns table (
  points_delta integer, season_points integer, season_position integer, season_name text,
  season_matches integer, season_wins integer, base_points integer, bonus_points integer
)
language plpgsql security definer set search_path = public as $$
declare
  m public.online_matches%rowtype;
  opp uuid;
  is_host boolean;
  my_username text;
  szn text;
  szn_label text;
  my_season integer;
  opp_season integer;
  my_overall integer;
  opp_overall integer;
  base_s integer;
  delta_s integer;
  base_o integer;
  delta_o integer;
  pair_today integer;
  wo_today integer;
  limited boolean;
  sn record;
begin
  select * into m from public.online_matches om where om.id = p_match_id;
  if m.id is null or m.guest_id is null then
    return;
  end if;
  is_host := (m.host_id = p_user);
  opp := case when is_host then m.guest_id else m.host_id end;
  my_username := case when is_host then m.host_name else m.guest_name end;

  select c.season_key, c.name into szn, szn_label from public.current_season() c;
  season_name := szn_label;

  -- força dos dois lados — quem ainda não jogou a temporada começa em 1000
  select s.points into my_season from public.ranked_season_stats s where s.user_id = p_user and s.season_key = szn;
  select s.points into opp_season from public.ranked_season_stats s where s.user_id = opp and s.season_key = szn;
  select p.ranked_rating into my_overall from public.profiles p where p.id = p_user;
  select p.ranked_rating into opp_overall from public.profiles p where p.id = opp;
  my_season := coalesce(my_season, 1000);
  opp_season := coalesce(opp_season, 1000);
  my_overall := coalesce(my_overall, 1000);
  opp_overall := coalesce(opp_overall, 1000);
  -- se o adversário já foi pontuado nesta sala, a força dele inclui esta partida: tira de volta,
  -- senão o resultado dependia de quem chegava antes
  opp_season := opp_season - coalesce(case when is_host then m.guest_delta_s else m.host_delta_s end, 0);
  opp_overall := opp_overall - coalesce(case when is_host then m.guest_delta_o else m.host_delta_o end, 0);

  base_s := round(32 * ((case when p_won then 1 else 0 end)
    - 1.0 / (1 + power(10.0, (opp_season - my_season) / 400.0))));
  base_o := round(32 * ((case when p_won then 1 else 0 end)
    - 1.0 / (1 + power(10.0, (opp_overall - my_overall) / 400.0))));

  -- soma zero (0.30.0): o vencedor ganha exatamente o que o perdedor perde
  delta_s := base_s;
  delta_o := base_o;

  -- anti-farm: a partir da (limite+1)ª ranqueada do dia (Brasília) contra o mesmo adversário
  select count(*) into pair_today from public.online_matches om
   where om.ranked and om.id <> p_match_id
     and ((om.host_id = p_user and om.guest_id = opp) or (om.host_id = opp and om.guest_id = p_user))
     and (om.created_at at time zone 'America/Sao_Paulo')::date = public.brt_today()
     and (om.host_delta_s is not null or om.guest_delta_s is not null);
  limited := pair_today >= public.config_int('ranked_daily_pair_limit', 3);

  -- WO: duas contas combinando o WO para subir pontos têm um limite mais apertado
  if p_wo and not limited then
    select count(*) into wo_today from public.online_matches om
     where om.ranked and om.walkover and om.id <> p_match_id
       and ((om.host_id = p_user and om.guest_id = opp) or (om.host_id = opp and om.guest_id = p_user))
       and (om.created_at at time zone 'America/Sao_Paulo')::date = public.brt_today()
       and (om.host_delta_s is not null or om.guest_delta_s is not null);
    limited := wo_today >= public.config_int('ranked_wo_daily_pair_limit', 1);
  end if;

  if limited then
    delta_s := 0;
    delta_o := 0;
    base_s := 0;
  end if;

  if is_host then
    update public.online_matches om set host_delta_s = delta_s, host_delta_o = delta_o where om.id = p_match_id;
  else
    update public.online_matches om set guest_delta_s = delta_s, guest_delta_o = delta_o where om.id = p_match_id;
  end if;

  if not limited then
    update public.profiles p
      set ranked_rating = greatest(0, p.ranked_rating + delta_o),
          ranked_matches = p.ranked_matches + 1,
          ranked_wins = p.ranked_wins + case when p_won then 1 else 0 end
      where p.id = p_user;

    insert into public.ranked_season_stats as s (user_id, season_key, username, points, matches, wins)
    values (p_user, szn, my_username, greatest(0, 1000 + delta_s), 1, case when p_won then 1 else 0 end)
    on conflict (user_id, season_key) do update
      set points = greatest(0, s.points + delta_s),
          matches = s.matches + 1,
          wins = s.wins + case when p_won then 1 else 0 end,
          username = excluded.username,
          updated_at = now();
  end if;

  select * into sn from public.ranked_snapshot(p_user, szn);
  season_points := sn.points;
  season_matches := sn.matches;
  season_wins := sn.wins;
  season_position := sn.pos;
  points_delta := delta_s;
  base_points := base_s;
  bonus_points := 0;
  return next;
end $$;
revoke all on function public.apply_ranked_side(uuid, uuid, boolean, boolean) from public, anon, authenticated;

-- ------------------------------------------------------------------ relato de cada lado

-- substitui record_ranked_result (online.sql): mesma assinatura, mesmas regras (o primeiro relato fixa
-- o vencedor, soma zero, anti-farm, abandono é derrota cheia), agora com a conta em apply_ranked_side.
-- Mudança visível: o relato repetido (`accepted` = false) devolve os pontos gravados nesta sala em vez
-- de 0, para o ausente de um WO que volta depois ver o que perdeu.
create or replace function public.record_ranked_result(
  p_match_id uuid, p_won boolean, p_accuracy integer, p_ships_left integer
)
returns table (
  points_delta integer, season_points integer, season_position integer, season_name text,
  season_matches integer, season_wins integer, accepted boolean,
  base_points integer, bonus_points integer
)
language plpgsql security definer set search_path = public as $$
declare
  m public.online_matches%rowtype;
  me uuid := auth.uid();
  opp uuid;
  is_host boolean;
  won boolean;
  szn text;
  szn_label text;
  r record;
begin
  -- "for update" enfileira os dois relatos da mesma sala
  select * into m from public.online_matches om where om.id = p_match_id for update;
  if m.id is null or not m.ranked or m.guest_id is null then
    return;
  end if;

  is_host := (m.host_id = me);
  if not is_host and m.guest_id <> me then
    return;
  end if;
  opp := case when is_host then m.guest_id else m.host_id end;

  -- conta de teste (#23) de qualquer lado: a partida não vale ponto nem conta no placar
  if public.is_test_user(me) or public.is_test_user(opp) then
    return;
  end if;

  select c.season_key, c.name into szn, szn_label from public.current_season() c;
  season_name := szn_label;

  -- ranqueada só para quem aderiu à temporada (Passe Gratuito ou Passe de Temporada)
  if to_regclass('public.season_passes') is not null
     and not exists (select 1 from public.season_passes sp where sp.user_id = me and sp.season_key = szn) then
    return;
  end if;

  if (case when is_host then m.host_result_recorded else m.guest_result_recorded end) then
    -- relato repetido (reabriu a tela, retomou o app, ou o ausente de um WO que voltou):
    -- devolve o estado atual e os pontos já gravados
    select sn2.points, sn2.matches, sn2.wins, sn2.pos
      into season_points, season_matches, season_wins, season_position
      from public.ranked_snapshot(me, szn) sn2;
    points_delta := coalesce(case when is_host then m.host_delta_s else m.guest_delta_s end, 0);
    base_points := points_delta;
    bonus_points := 0;
    accepted := false;
    return next;
    return;
  end if;

  -- 1. o primeiro relato fixa o vencedor; o segundo só confere
  if m.winner_id is null then
    m.winner_id := case when p_won then me else opp end;
    update public.online_matches om set winner_id = m.winner_id where om.id = p_match_id;
  elsif (m.winner_id = me) <> p_won then
    update public.online_matches om set result_conflict = true where om.id = p_match_id;
  end if;
  won := (m.winner_id = me);

  if is_host then
    update public.online_matches om set host_result_recorded = true where om.id = p_match_id;
  else
    update public.online_matches om set guest_result_recorded = true where om.id = p_match_id;
  end if;

  select * into r from public.apply_ranked_side(p_match_id, me, won, false);

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

revoke all on function public.record_ranked_result(uuid, boolean, integer, integer) from public, anon;
grant execute on function public.record_ranked_result(uuid, boolean, integer, integer) to authenticated;

-- ------------------------------------------------------------------ vitória por ausência

-- Chamada por quem ficou (o app novo no fim da contagem, o online_heartbeat a cada batida depois do
-- prazo). Devolve uma linha só, com `outcome`:
--   resolved     o WO acabou de ser gravado: quem chamou venceu
--   already      a sala já terminou em WO (vale para quem ficou e para o ausente que voltou)
--   too_early    ainda não passaram `walkover_seconds` da entrada
--   signal       o adversário deu sinal: sem WO (reconexão cancela)
--   no_self_signal  quem chamou também não deu sinal depois da entrada (não pode reivindicar)
--   waiting      a sala ainda não tem adversário
--   closed       a sala já terminou de outro jeito (QUIT, resultado relatado)
--   unsupported  sala anterior a esta migração, sem `joined_at`
-- Sem linha: a sala não existe ou o chamador não está nela. Nos casos resolved/already vêm também o
-- vencedor, se a sala é ranqueada, os pontos do chamador (mesmos campos de record_ranked_result) e se
-- a milha voltou.
drop function if exists public.resolve_walkover(uuid);
create function public.resolve_walkover(p_match_id uuid)
returns table (
  outcome text, winner uuid, was_ranked boolean,
  points_delta integer, season_points integer, season_position integer, season_name text,
  season_matches integer, season_wins integer, accepted boolean,
  base_points integer, bonus_points integer, miles_refunded boolean
)
language plpgsql security definer set search_path = public as $$
declare
  m public.online_matches%rowtype;
  me uuid := auth.uid();
  absent uuid;
  is_host boolean;
  t integer := greatest(public.config_int('walkover_seconds', 60), 10);
  szn text;
  szn_label text;
  scored boolean;
  n integer;
begin
  if me is null then
    return;
  end if;
  -- a mesma trava de record_ranked_result: heartbeat, claim e relato da mesma sala viram fila
  select * into m from public.online_matches om where om.id = p_match_id for update;
  if m.id is null or (m.host_id <> me and m.guest_id is distinct from me) then
    return;
  end if;

  is_host := (m.host_id = me);
  absent := case when is_host then m.guest_id else m.host_id end;
  was_ranked := m.ranked;
  accepted := false;
  miles_refunded := false;

  if m.walkover then
    outcome := 'already';
  elsif m.guest_id is null or m.status = 'waiting' then
    outcome := 'waiting';
  elsif m.status <> 'active' or m.winner_id is not null then
    outcome := 'closed';
  elsif m.joined_at is null then
    outcome := 'unsupported';
  elsif now() < m.joined_at + make_interval(secs => t) then
    outcome := 'too_early';
  elsif not public.room_has_signal(m.id, me) then
    outcome := 'no_self_signal';
  elsif public.room_has_signal(m.id, absent) then
    outcome := 'signal';
  else
    outcome := 'resolved';
    update public.online_matches om
       set status = 'abandoned', winner_id = me, walkover = true,
           host_result_recorded = true, guest_result_recorded = true
     where om.id = p_match_id;
    -- QUIT do ausente: o app de quem ficou (inclusive o antigo) fecha a partida; DROP do vencedor: o
    -- app do ausente, ao voltar, fecha com derrota. O sufixo |WO só o app novo lê.
    insert into public.online_messages (match_id, sender_id, body)
    values (p_match_id, absent, 'QUIT|WO'), (p_match_id, me, 'DROP|WO');

    -- ranqueada: Elo normal para quem ficou e derrota cheia para o ausente (soma zero). Conta de
    -- teste de qualquer lado, ou sem passe de temporada, é vitória sem pontos para os dois.
    if m.ranked then
      select c.season_key into szn from public.current_season() c;
      scored := not (public.is_test_user(me) or public.is_test_user(absent))
        and (to_regclass('public.season_passes') is null
             or (select count(*) from public.season_passes sp
                  where sp.season_key = szn and sp.user_id in (me, absent)) = 2);
      if scored then
        perform public.apply_ranked_side(p_match_id, me, true, true);
        perform public.apply_ranked_side(p_match_id, absent, false, true);
      end if;
    end if;

    -- a milha gasta para entrar volta a quem ficou (uma vez por sala, só se ela foi gasta)
    insert into public.mile_ledger (user_id, match_id, kind, amount)
    select me, p_match_id, 'refund', 1
     where exists (select 1 from public.mile_ledger l
                    where l.user_id = me and l.match_id = p_match_id and l.kind = 'spend')
    on conflict do nothing;
    get diagnostics n = row_count;
    if n > 0 then
      update public.profiles p
         set nautical_miles = greatest(p.nautical_miles,
               least(public.config_int('miles_cap', 50), p.nautical_miles + 1))
       where p.id = me;
    end if;
    select * into m from public.online_matches om where om.id = p_match_id;
  end if;

  if outcome in ('resolved', 'already') then
    winner := m.winner_id;
    miles_refunded := exists (select 1 from public.mile_ledger l
                               where l.user_id = me and l.match_id = p_match_id and l.kind = 'refund');
    if m.ranked then
      select c.season_key, c.name into szn, szn_label from public.current_season() c;
      season_name := szn_label;
      select sn2.points, sn2.matches, sn2.wins, sn2.pos
        into season_points, season_matches, season_wins, season_position
        from public.ranked_snapshot(me, szn) sn2;
      points_delta := coalesce(case when is_host then m.host_delta_s else m.guest_delta_s end, 0);
      base_points := points_delta;
      bonus_points := 0;
      accepted := (outcome = 'resolved');
    end if;
  end if;
  return next;
end $$;

revoke all on function public.resolve_walkover(uuid) from public, anon;
grant execute on function public.resolve_walkover(uuid) to authenticated;

-- ------------------------------------------------------------------ presença

-- substitui online_heartbeat (online.sql): mesma assinatura e mesmo retorno. A novidade: depois do
-- prazo, se o adversário ainda não deu sinal desde a entrada, a própria batida de quem ficou resolve o
-- WO (sem cron, e o app antigo também resolve). O teste barato (presença do adversário nula ou anterior
-- à entrada) evita travar a sala a cada batida de uma partida normal; um erro na resolução nunca
-- derruba a batida.
create or replace function public.online_heartbeat(p_match_id uuid)
returns table (opponent_seen_secs integer)
language plpgsql security definer set search_path = public as $$
declare
  m public.online_matches%rowtype;
  me uuid := auth.uid();
  other_seen timestamptz;
begin
  select * into m from public.online_matches om where om.id = p_match_id;
  if m.id is null or me is null then
    return;
  end if;
  if m.host_id = me then
    update public.online_matches om set host_seen_at = now() where om.id = p_match_id;
    other_seen := m.guest_seen_at;
  elsif m.guest_id = me then
    update public.online_matches om set guest_seen_at = now() where om.id = p_match_id;
    other_seen := m.host_seen_at;
  else
    return;
  end if;

  if m.status = 'active' and m.joined_at is not null
     and now() >= m.joined_at + make_interval(secs => greatest(public.config_int('walkover_seconds', 60), 10))
     and (other_seen is null or other_seen <= m.joined_at) then
    begin
      perform public.resolve_walkover(p_match_id);
    exception when others then
      null;
    end;
  end if;

  opponent_seen_secs := case when other_seen is null then null
    else greatest(0, extract(epoch from (now() - other_seen))::integer) end;
  return next;
end $$;

revoke all on function public.online_heartbeat(uuid) from public, anon;
grant execute on function public.online_heartbeat(uuid) to authenticated;

-- ------------------------------------------------------------------ entrada e partida rápida

-- substitui join_online_match (safety.sql): grava o instante da entrada (`joined_at`), o início do
-- prazo do WO. Nada mais muda.
create or replace function public.join_online_match(p_match_id uuid, p_guest_name text)
returns setof public.online_matches
language plpgsql security invoker as $$
begin
  return query
    update public.online_matches
    set guest_id = auth.uid(), guest_name = p_guest_name, status = 'active', joined_at = now()
    where id = p_match_id and guest_id is null and status = 'waiting'
      and (invited_id is null or invited_id = auth.uid())
      -- partida rápida: conta de teste só com conta de teste (#23)
      and (not is_quick_match or public.is_test_user(host_id) = public.my_is_test())
      -- bloqueio nos dois sentidos e suspensão (#58)
      and not public.blocked_between(host_id, auth.uid())
    returning *;
end $$;
revoke all on function public.join_online_match(uuid, text) from public, anon;
grant execute on function public.join_online_match(uuid, text) to authenticated;

-- Sala `active` sem sinal de NENHUM dos dois por 2x o prazo: ninguém entrou. Fecha sem vencedor nem
-- pontos (a sala fantasma que alguém aceitou e que nunca teve ninguém do outro lado). Interna.
create or replace function public.sweep_empty_rooms()
returns void language sql security definer set search_path = public as $$
  update public.online_matches m
     set status = 'abandoned'
   where m.status = 'active' and m.joined_at is not null
     and m.joined_at < now() - make_interval(secs => greatest(public.config_int('walkover_seconds', 60), 10) * 2)
     and m.joined_at > now() - interval '1 day'
     and not public.room_has_signal(m.id, m.host_id)
     and not public.room_has_signal(m.id, m.guest_id);
$$;
revoke all on function public.sweep_empty_rooms() from public, anon, authenticated;

-- substitui find_quick_offer (safety.sql): varre as salas vazias e só oferece a sala cujo anfitrião
-- ainda aparece (`host_seen_at` recente) ou é de app antigo (nulo: segue a regra de 10 minutos).
create or replace function public.find_quick_offer(p_casual boolean, p_ranked boolean)
returns setof public.online_matches
language plpgsql security definer set search_path = public as $$
declare
  me uuid := auth.uid();
  v_id uuid;
begin
  if me is null then return; end if;
  perform public.sweep_empty_rooms();
  select m.id into v_id
  from public.online_matches m
  where m.status = 'waiting'
    and m.is_quick_match
    and m.guest_id is null
    and m.invited_id is null
    and m.host_id <> me
    -- conta de teste só recebe sala de conta de teste, e vice-versa (#23)
    and public.is_test_user(m.host_id) = public.is_test_user(me)
    -- bloqueio nos dois sentidos e suspensão (#58)
    and not public.blocked_between(m.host_id, me)
    and not (me = any(m.declined_by))
    and ((m.ranked and p_ranked) or (not m.ranked and p_casual))
    and m.created_at > now() - interval '10 minutes'
    -- anfitrião que fechou o app deixa a sala fantasma: some da oferta em poucos segundos (#85)
    and (m.host_seen_at is null
         or m.host_seen_at > now() - make_interval(secs => greatest(public.config_int('quick_host_fresh_seconds', 20), 5)))
    and (m.offered_to is null or m.offered_to = me or m.offered_at < now() - interval '60 seconds')
  order by m.created_at asc
  limit 1
  for update skip locked;
  if v_id is null then return; end if;
  update public.online_matches set offered_to = me, offered_at = now() where id = v_id;
  return query select * from public.online_matches where id = v_id;
end $$;
revoke all on function public.find_quick_offer(boolean, boolean) from public, anon;
grant execute on function public.find_quick_offer(boolean, boolean) to authenticated;
