-- Ensaio que desfaz tudo (#105, 0.44.6): adversário virtual (bot) na partida rápida (supabase/bots.sql).
-- Rode no SQL Editor do projeto cwtslesnthbenxswdcbv: cole o texto inteiro de bots.sql e este bloco na MESMA
-- execução (o raise no fim cancela a transação inteira, DDL e dados inclusive; nada fica gravado).
-- Usuários, salas e a conta "QA" são fictícios, criados dentro do bloco (nenhuma conta real nem as QA01/QA02 são
-- tocadas). As chaves de app_config mudadas aqui voltam com o rollback.
--
-- Resultado esperado (cada item vira um trecho de "RESULTADO"):
--   chave_off=off                          bots_enabled 'false': nem a conta QA entra; wait_info bot_on=false
--   chave_test: qa=ok outro=off            'test' liga só a conta QA
--   rating_do_bot=ok (|dev|<=100)          rating do bot em [rating-100, rating+100]
--   nivel_novo=easy                        jogador sem histórico enfrenta o nível fácil
--   nivel_veterano=hard nivel_medio=medium nível pelo histórico, calculado no servidor
--   start_sem_cancelar=invalid             sala ainda waiting: o servidor não emite o bot
--   cancel_outro=invalid cancel=cancelled cancel_de_novo=cancelled   só o anfitrião cancela; idempotente
--   cancel_com_convidado=joined            um humano entrou na frente: o app não inicia o bot (A1)
--   cancel_sala_amigo=invalid              convite mirado nunca vira bot (critério 15)
--   join_apos_cancel=0 linhas              a sala cancelada não aceita mais ninguém
--   sala_reusada=invalid                   a mesma sala não gera dois bots
--   busy=busy                              outra sala viva do mesmo jogador: nada de bot junto
--   r_outra_conta=invalid                  outra conta chamando record_ranked_bot_result
--   r_curta_vitoria=too_short Elo igual    vitória antes de 75 s: nada muda e a linha segue aberta
--   r_curta_derrota=ok delta<0             derrota vale em qualquer duração
--   r_vitoria=ok +8 geral/temporada        vitória depois de 75 s: metade do Elo normal (16 x 50 %)
--   r_repetido=repeat delta igual          relato repetido devolve o gravado e não mexe em nada
--   cap=ok delta<=cap                      teto duro por partida
--   equiv_vitoria=ok equiv_derrota=ok      com pct = 100 o delta bate com apply_ranked_side
--   cota_diaria=limit                      ranqueadas contra bot por dia (padrão 3)
--   cota_total=limit                       ranqueadas contra bot na vida da conta (padrão 10)
--   cota_max_dia=limit                     total do dia (casual + ranqueada)
--   vencida=derrota delta<0 status=expired ranqueada aberta há mais de 30 min fecha como derrota no próximo start
--   conta_teste=off  sem_passe_ranq=off sem_passe_casual=ok
--   chave_off_no_meio=ok                   partida aberta com a chave desligada fecha normalmente (critério 12)
--   casual_report=ok repetido=repeat outra=invalid ranked=ranked
--   purge=1                                só a linha de 100 dias sai
--   outros_intactos=ok                     contas que não jogaram (e a de teste e a sem passe) ficam sem mexer
--   rls=negado  grants=ok                  authenticated não lê a tabela; anon não executa nada
--   view=ok                                behavior_dias_so_bot existe quando activity_days existe
create function pg_temp.nb_room(h uuid, rk boolean, md text default 'CLASSIC') returns uuid
language plpgsql as $$
declare rid uuid := gen_random_uuid();
begin
  insert into public.online_matches (id, host_id, host_name, status, is_quick_match, ranked, mode)
  values (rid, h, 'ens', 'waiting', true, rk, md);
  return rid;
end $$;

create function pg_temp.nb_as(u uuid) returns void language plpgsql as $$
begin
  perform set_config('request.jwt.claims', json_build_object('sub', u, 'role', 'authenticated')::text, true);
end $$;

-- sala nova + cancelamento + bot, como o app faz; devolve a linha do start
create function pg_temp.nb_start(u uuid, rk boolean, md text default 'CLASSIC')
returns table (outcome text, bot_id uuid, bot_rating integer, level text, ranked boolean)
language plpgsql as $$
declare rid uuid;
begin
  perform pg_temp.nb_as(u);
  rid := pg_temp.nb_room(u, rk, md);
  perform public.cancel_quick_room(rid);
  return query select * from public.bot_match_start(md, rid, 'Marujo', '0.45.0');
end $$;

do $$
declare
  a uuid := gen_random_uuid();  -- conta QA, com passe, jogador novo
  b uuid := gen_random_uuid();  -- jogador comum, com passe, veterano (hard)
  c uuid := gen_random_uuid();  -- conta de teste (robô)
  d uuid := gen_random_uuid();  -- sem passe
  e uuid := gen_random_uuid();  -- adversário humano do lado de apply_ranked_side
  f uuid := gen_random_uuid();  -- comparação de equivalência (humano)
  g uuid := gen_random_uuid();  -- comparação de equivalência (bot)
  h uuid := gen_random_uuid();  -- nível médio
  k uuid := gen_random_uuid();  -- teto duro / vencida
  o uuid := gen_random_uuid();  -- chave desligada no meio da partida
  z uuid := gen_random_uuid();  -- conta que não faz nada (intacta)
  szn text; out text := ''; s record; s2 record; n integer; v text; rid uuid; rid2 uuid; bid uuid;
  before_o integer; before_s integer; r1000 integer; sum_o_before integer; sum_o_after integer;
  dv integer; pts_h integer; pts_b integer; lvl_b text; i integer;
begin
  select c0.season_key into szn from public.current_season() c0;
  insert into auth.users (id, email)
    select u, 'ensaio-' || u || '@ensaio.invalid' from unnest(array[a, b, c, d, e, f, g, h, k, o, z]) u;
  update public.profiles set is_test = false where id in (a, b, d, e, f, g, h, k, o, z);
  update public.profiles set is_test = true where id = c;
  insert into public.season_passes (user_id, season_key, tier)
    select u, szn, 'free' from unnest(array[a, b, c, e, f, g, h, k, o, z]) u;  -- d fica sem passe
  insert into public.allowed_players (user_id, source, note) values (a, 'qa', 'ensaio bots');
  -- a partida contra o bot custa 1 milha (#112): milhas de sobra para as cotas do ensaio
  update public.profiles set nautical_miles = 50, miles_day = public.brt_today() where id in (a, b, c, d, e, f, g, h, k, o, z);
  -- veterano de 20 partidas, 14 vitórias (70 %); médio de 8 partidas
  update public.profiles set matches = 20, wins = 14 where id = b;
  update public.profiles set matches = 8, wins = 3 where id = h;
  sum_o_before := (select sum(p.ranked_rating) from public.profiles p where p.id in (z, c, d));

  -- chave desligada: nem a QA entra
  update public.app_config set value = 'false' where key = 'bots_enabled';
  perform pg_temp.nb_as(a);
  select * into s from pg_temp.nb_start(a, false);
  select * into s2 from public.bot_wait_info();
  out := out || format('chave_off=%s bot_on=%s | ', s.outcome, s2.bot_on);

  -- 'test': só a QA
  update public.app_config set value = 'test' where key = 'bots_enabled';
  select * into s from pg_temp.nb_start(a, false);
  v := s.outcome;
  select * into s from pg_temp.nb_start(b, false);
  out := out || format('chave_test: qa=%s outro=%s | ', v, s.outcome);

  -- 'true' liga para todos, menos o robô de teste
  update public.app_config set value = 'true' where key = 'bots_enabled';
  select * into s from pg_temp.nb_start(c, false);
  out := out || format('chave_true_robo=%s | ', s.outcome);

  -- salas: ordem do A1
  perform pg_temp.nb_as(a);
  rid := pg_temp.nb_room(a, false);
  select * into s from public.bot_match_start('CLASSIC', rid, 'Marujo', '0.45.0');
  out := out || format('start_sem_cancelar=%s | ', s.outcome);
  perform pg_temp.nb_as(b);
  out := out || format('cancel_outro=%s | ', (select c0.outcome from public.cancel_quick_room(rid) c0));
  perform pg_temp.nb_as(a);
  out := out || format('cancel=%s ', (select c0.outcome from public.cancel_quick_room(rid) c0));
  out := out || format('cancel_de_novo=%s | ', (select c0.outcome from public.cancel_quick_room(rid) c0));
  -- a sala cancelada não aceita mais ninguém (a mesma condição de join_online_match)
  select count(*) into n from public.online_matches om where om.id = rid and om.guest_id is null and om.status = 'waiting';
  out := out || format('join_apos_cancel=%s linhas | ', n);
  select * into s from public.bot_match_start('CLASSIC', rid, 'Marujo', '0.45.0');
  out := out || format('start1=%s nivel=%s ranked=%s | ', s.outcome, s.level, s.ranked);
  bid := s.bot_id;
  select p.ranked_rating into r1000 from public.profiles p where p.id = a;
  dv := s.bot_rating - r1000;
  out := out || format('rating_do_bot=%s | ', case when abs(dv) <= 100 then 'ok' else 'FALHOU(' || dv || ')' end);
  select * into s from public.bot_match_start('CLASSIC', rid, 'Marujo', '0.45.0');
  out := out || format('sala_reusada=%s | ', s.outcome);
  -- fecha a casual para não pesar nas outras contas
  out := out || format('casual_report=%s ', (select c0.outcome from public.bot_match_report(bid, true) c0));
  out := out || format('repetido=%s ', (select c0.outcome from public.bot_match_report(bid, false) c0));
  perform pg_temp.nb_as(b);
  out := out || format('outra=%s | ', (select c0.outcome from public.bot_match_report(bid, true) c0));

  -- humano entrou na frente
  perform pg_temp.nb_as(a);
  rid2 := pg_temp.nb_room(a, false);
  update public.online_matches om set guest_id = b, guest_name = 'b', status = 'active' where om.id = rid2;
  out := out || format('cancel_com_convidado=%s | ', (select c0.outcome from public.cancel_quick_room(rid2) c0));
  -- sala de amigo (convite mirado) nunca vira bot
  rid2 := gen_random_uuid();
  insert into public.online_matches (id, host_id, host_name, status, is_quick_match, invited_id)
    values (rid2, a, 'a', 'waiting', false, b);
  out := out || format('cancel_sala_amigo=%s | ', (select c0.outcome from public.cancel_quick_room(rid2) c0));
  delete from public.online_matches om where om.host_id = a or om.guest_id = a;

  -- outra sala viva do mesmo jogador
  perform pg_temp.nb_as(a);
  rid := pg_temp.nb_room(a, false);
  perform public.cancel_quick_room(rid);
  perform pg_temp.nb_room(a, false);  -- outra sala waiting do mesmo anfitrião
  select * into s from public.bot_match_start('CLASSIC', rid, 'Marujo', '0.45.0');
  out := out || format('busy=%s | ', s.outcome);
  delete from public.online_matches om where om.host_id = a;

  -- níveis pelo histórico
  select * into s from pg_temp.nb_start(a, false);  lvl_b := s.level;
  out := out || format('nivel_novo=%s ', lvl_b);
  select * into s from pg_temp.nb_start(b, false);  out := out || format('nivel_veterano=%s ', s.level);
  select * into s from pg_temp.nb_start(h, false);  out := out || format('nivel_medio=%s | ', s.level);

  ------------------------------------------------------------ ranqueada: Elo do jogador (a, QA, 1000)
  delete from public.bot_matches bm where bm.user_id = a;
  select * into s from pg_temp.nb_start(a, true);
  bid := s.bot_id;
  perform pg_temp.nb_as(b);
  select * into s2 from public.record_ranked_bot_result(bid, true);
  out := out || format('r_outra_conta=%s | ', s2.outcome);
  perform pg_temp.nb_as(a);
  select p.ranked_rating into before_o from public.profiles p where p.id = a;
  select * into s2 from public.record_ranked_bot_result(bid, true);
  out := out || format('r_curta_vitoria=%s mesmo_elo=%s aberta=%s | ', s2.outcome,
    (select p.ranked_rating = before_o from public.profiles p where p.id = a),
    (select bm.status from public.bot_matches bm where bm.id = bid));
  -- vitória depois de 75 s: rating do bot fixo em 1000 para o resultado ser previsível (+8 = 16 x 50 %)
  update public.bot_matches bm set started_at = now() - interval '100 seconds', bot_rating = 1000, bot_season = 1000 where bm.id = bid;
  select * into s2 from public.record_ranked_bot_result(bid, true);
  out := out || format('r_vitoria=%s delta=%s geral=%s temporada=%s | ', s2.outcome, s2.points_delta,
    (select p.ranked_rating - before_o from public.profiles p where p.id = a),
    (select st.points - 1000 from public.ranked_season_stats st where st.user_id = a and st.season_key = szn));
  select * into s2 from public.record_ranked_bot_result(bid, false);  -- repetido, mesmo dizendo outra coisa
  out := out || format('r_repetido=%s delta=%s aceito=%s | ', s2.outcome, s2.points_delta, s2.accepted);
  out := out || format('linha: ganhou=%s delta_s=%s delta_o=%s | ',
    (select bm.won from public.bot_matches bm where bm.id = bid),
    (select bm.delta_s from public.bot_matches bm where bm.id = bid),
    (select bm.delta_o from public.bot_matches bm where bm.id = bid));

  -- derrota curta vale (sem ganho a evitar)
  select * into s from pg_temp.nb_start(a, true);
  bid := s.bot_id;
  update public.bot_matches bm set bot_rating = 1008, bot_season = 1008 where bm.id = bid;
  select * into s2 from public.record_ranked_bot_result(bid, false);
  out := out || format('r_curta_derrota=%s delta=%s | ', s2.outcome, s2.points_delta);

  -- teto duro por partida: força do bot muito acima, base grande, cap 3
  update public.app_config set value = '100' where key = 'bot_ranked_delta_pct';
  update public.app_config set value = '3' where key = 'bot_ranked_max_delta';
  delete from public.bot_matches bm where bm.user_id = k;
  select * into s from pg_temp.nb_start(k, true);
  update public.bot_matches bm set started_at = now() - interval '200 seconds', bot_rating = 1600, bot_season = 1600 where bm.id = s.bot_id;
  perform pg_temp.nb_as(k);
  select * into s2 from public.record_ranked_bot_result(s.bot_id, true);
  out := out || format('cap=%s delta=%s (teto 3) | ', s2.outcome, s2.points_delta);

  -- equivalência com apply_ranked_side (pct = 100, teto 32): 1000 x 1000 e 1000 x 1200
  update public.app_config set value = '32' where key = 'bot_ranked_max_delta';
  delete from public.bot_matches bm where bm.user_id = g;
  update public.profiles set ranked_rating = 1000 where id in (f, g, e);
  delete from public.ranked_season_stats where user_id in (f, g, e);
  -- humano f x e (1000 x 1000): f vence
  rid := gen_random_uuid();
  insert into public.online_matches (id, host_id, guest_id, host_name, guest_name, status, ranked)
    values (rid, f, e, 'F', 'E', 'active', true);
  select * into s from public.apply_ranked_side(rid, f, true, false);
  pts_h := s.points_delta;
  select * into s from pg_temp.nb_start(g, true);
  update public.bot_matches bm set started_at = now() - interval '200 seconds', bot_rating = 1000, bot_season = 1000 where bm.id = s.bot_id;
  perform pg_temp.nb_as(g);
  select * into s2 from public.record_ranked_bot_result(s.bot_id, true);
  out := out || format('equiv_vitoria=%s (humano=%s bot=%s) | ', case when pts_h = s2.points_delta then 'ok' else 'FALHOU' end, pts_h, s2.points_delta);
  -- 1000 x 1200, o jogador perde: e (rating 1000) perde para o adversário de 1200
  update public.profiles set ranked_rating = 1200 where id = f;
  delete from public.ranked_season_stats where user_id = f;
  insert into public.ranked_season_stats (user_id, season_key, username, points, matches, wins) values (f, szn, 'F', 1200, 3, 2);
  rid := gen_random_uuid();
  insert into public.online_matches (id, host_id, guest_id, host_name, guest_name, status, ranked)
    values (rid, e, f, 'E', 'F', 'active', true);
  select * into s from public.apply_ranked_side(rid, e, false, false);
  pts_h := s.points_delta;
  update public.profiles set ranked_rating = 1000 where id = g;
  delete from public.ranked_season_stats where user_id = g;
  select * into s from pg_temp.nb_start(g, true);
  update public.bot_matches bm set bot_rating = 1200, bot_season = 1200 where bm.id = s.bot_id;
  perform pg_temp.nb_as(g);
  select * into s2 from public.record_ranked_bot_result(s.bot_id, false);
  out := out || format('equiv_derrota=%s (humano=%s bot=%s) | ', case when pts_h = s2.points_delta then 'ok' else 'FALHOU' end, pts_h, s2.points_delta);
  update public.app_config set value = '50' where key = 'bot_ranked_delta_pct';
  update public.app_config set value = '16' where key = 'bot_ranked_max_delta';

  ------------------------------------------------------------ cotas (conta h)
  delete from public.bot_matches bm where bm.user_id = h;
  for i in 1..3 loop
    select * into s from pg_temp.nb_start(h, true);
    v := s.outcome;
    perform pg_temp.nb_as(h);
    update public.bot_matches bm set started_at = now() - interval '100 seconds' where bm.id = s.bot_id;
    perform * from public.record_ranked_bot_result(s.bot_id, false);
  end loop;
  select * into s from pg_temp.nb_start(h, true);
  out := out || format('cota_diaria=%s (3 ok antes) | ', s.outcome);
  -- casual ainda cabe no total do dia
  select * into s from pg_temp.nb_start(h, false);
  out := out || format('casual_apos_cota_ranqueada=%s | ', s.outcome);
  -- total na vida (10): zera o diário recuando 3 partidas para ontem e enche até 10
  update public.bot_matches bm set started_at = now() - interval '2 days', ended_at = now() - interval '2 days' where bm.user_id = h;
  insert into public.bot_matches (user_id, mode, ranked, level, bot_rating, bot_season, status, started_at, ended_at, won, delta_s, delta_o)
    select h, 'CLASSIC', true, 'easy', 1000, 1000, 'closed', now() - interval '3 days', now() - interval '3 days', false, 0, 0
      from generate_series(1, 7);
  select * into s from pg_temp.nb_start(h, true);
  out := out || format('cota_total=%s | ', s.outcome);
  -- total do dia (casual + ranqueada): 10
  delete from public.bot_matches bm where bm.user_id = h;
  insert into public.bot_matches (user_id, mode, ranked, level, bot_rating, bot_season, status, started_at, ended_at, won)
    select h, 'CLASSIC', false, 'easy', 1000, 1000, 'closed', now(), now(), true from generate_series(1, 10);
  select * into s from pg_temp.nb_start(h, false);
  out := out || format('cota_max_dia=%s | ', s.outcome);

  ------------------------------------------------------------ partida vencida vira derrota
  delete from public.bot_matches bm where bm.user_id = k;
  select p.ranked_rating into before_o from public.profiles p where p.id = k;
  insert into public.bot_matches (id, user_id, mode, ranked, level, bot_rating, bot_season, status, started_at)
    values (gen_random_uuid(), k, 'CLASSIC', true, 'easy', before_o, before_o, 'open', now() - interval '40 minutes');
  select * into s from pg_temp.nb_start(k, true);
  out := out || format('vencida: start=%s delta_geral=%s status=%s | ', s.outcome,
    (select p.ranked_rating - before_o from public.profiles p where p.id = k),
    (select bm.status from public.bot_matches bm where bm.user_id = k and bm.started_at < now() - interval '30 minutes' limit 1));

  ------------------------------------------------------------ conta de teste e sem passe
  select * into s from pg_temp.nb_start(c, false);
  out := out || format('conta_teste=%s | ', s.outcome);
  select * into s from pg_temp.nb_start(d, true);
  v := s.outcome;
  select * into s from pg_temp.nb_start(d, false);
  out := out || format('sem_passe_ranq=%s sem_passe_casual=%s | ', v, s.outcome);

  ------------------------------------------------------------ chave desligada no meio da partida
  update public.app_config set value = 'true' where key = 'bots_enabled';
  delete from public.bot_matches bm where bm.user_id = o;
  select * into s from pg_temp.nb_start(o, true);
  bid := s.bot_id;
  update public.app_config set value = 'false' where key = 'bots_enabled';
  update public.bot_matches bm set started_at = now() - interval '120 seconds' where bm.id = bid;
  perform pg_temp.nb_as(o);
  select * into s2 from public.record_ranked_bot_result(bid, true);
  out := out || format('chave_off_no_meio=%s | ', s2.outcome);
  select * into s from pg_temp.nb_start(o, false);
  out := out || format('proxima_com_chave_off=%s | ', s.outcome);
  select * into s2 from public.bot_wait_info();
  out := out || format('wait_info_off bot_on=%s | ', s2.bot_on);
  update public.app_config set value = 'true' where key = 'bots_enabled';
  perform pg_temp.nb_as(o);
  select * into s2 from public.bot_wait_info();
  out := out || format('wait_info_on bot_on=%s casual=%s ranq=%s ok=%s/%s | ', s2.bot_on, s2.wait_casual, s2.wait_ranked, s2.casual_ok, s2.ranked_ok);
  -- casual_report numa ranqueada
  select * into s from pg_temp.nb_start(o, true);
  out := out || format('ranked=%s | ', (select c0.outcome from public.bot_match_report(s.bot_id, true) c0));

  ------------------------------------------------------------ purge
  insert into public.bot_matches (user_id, mode, ranked, level, bot_rating, bot_season, status, started_at)
    values (o, 'CLASSIC', false, 'easy', 1000, 1000, 'closed', now() - interval '100 days');
  out := out || format('purge=%s | ', public.purge_bots());

  ------------------------------------------------------------ nenhuma outra conta mexida
  sum_o_after := (select sum(p.ranked_rating) from public.profiles p where p.id in (z, c, d));
  out := out || format('outros_intactos=%s | ', case when sum_o_before = sum_o_after
    and not exists (select 1 from public.ranked_season_stats st where st.user_id in (z, c, d)) then 'ok' else 'FALHOU' end);

  ------------------------------------------------------------ RLS e grants
  begin
    perform set_config('role', 'authenticated', true);
    perform 1 from public.bot_matches limit 1;
    out := out || 'rls=LEU(FALHOU) | ';
  exception when insufficient_privilege then
    out := out || 'rls=negado | ';
  end;
  execute 'reset role';
  out := out || format('grants=%s | ', case when
        not has_function_privilege('anon', 'public.record_ranked_bot_result(uuid,boolean)', 'execute')
    and not has_function_privilege('anon', 'public.bot_match_start(text,uuid,text,text)', 'execute')
    and not has_function_privilege('anon', 'public.cancel_quick_room(uuid)', 'execute')
    and not has_function_privilege('authenticated', 'public.apply_ranked_bot_side(uuid,boolean)', 'execute')
    and not has_function_privilege('authenticated', 'public.bots_on_for(uuid)', 'execute')
    and not has_function_privilege('authenticated', 'public.purge_bots()', 'execute')
    and has_function_privilege('authenticated', 'public.record_ranked_bot_result(uuid,boolean)', 'execute')
    and has_function_privilege('authenticated', 'public.bot_match_start(text,uuid,text,text)', 'execute')
    and has_function_privilege('authenticated', 'public.cancel_quick_room(uuid)', 'execute')
    then 'ok' else 'FALHOU' end);
  out := out || format('view=%s', case when to_regclass('public.activity_days') is null then 'sem activity_days (ok)'
    when to_regclass('public.behavior_dias_so_bot') is not null then 'ok' else 'FALHOU' end);

  raise exception 'RESULTADO (rollback): %', out;
end $$;
