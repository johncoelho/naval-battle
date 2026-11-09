-- Ensaio que desfaz tudo (#85, 0.44.0): vitória por ausência (supabase/walkover.sql).
-- Rode no SQL Editor do projeto cwtslesnthbenxswdcbv ANTES de aplicar a migração `walkover`: cole o
-- texto inteiro de walkover.sql e este bloco na MESMA execução. O raise no fim cancela a transação
-- inteira, DDL inclusive, e devolve o resultado na mensagem de erro.
-- Usuários e salas fictícios criados dentro do bloco (nenhuma conta real nem QA é tocada). As salas
-- `waiting` reais de produção são fechadas só dentro do bloco, para a busca do ensaio não pegar uma
-- delas; o rollback devolve tudo. O tempo é simulado recuando `joined_at` e os carimbos de presença.
--
-- Resultado esperado (cada item vira um trecho de "RESULTADO"):
--   join_joined_at=ok                     join_online_match grava joined_at
--   w1=too_early                          30 s depois da entrada ainda não resolve
--   w2=resolved vencedor_host=true        90 s sem sinal do adversário: o anfitrião vence
--   w2_sala=abandoned/walkover=true/flags=true,true
--   w2_msgs=QUIT|WO(guest) DROP|WO(host)  as duas linhas do protocolo, com os senders certos
--   w2_de_novo=already vencedor_host=true segunda chamada é idempotente (nada regravado)
--   w2_ausente=already vencedor_host=true o ausente que volta lê o mesmo resultado
--   w2_terceiro=0 linhas                  quem não está na sala não resolve
--   w3=signal                             presença do adversário depois da entrada cancela
--   w3_msg=signal                         uma mensagem dele depois da entrada também (app antigo)
--   w4=no_self_signal                     quem reivindica também precisa ter dado sinal
--   w5_heartbeat=abandoned                o heartbeat de quem ficou resolve sem cliente novo
--   w6_reconexao=active                   o heartbeat do ausente antes do claim impede o WO
--   w6_claim=signal
--   w6b_antigo_sem_wo=active              partida normal depois do prazo, os dois com sinal: nada
--   w7 ranqueada: a=+16 b=-16 soma_zero=ok geral=1016/984 temporada=1016/984 winner_id=a deltas=16/-16
--   w7_tardio_ausente accepted=false points_delta=-16    relato tardio do ausente (app antigo)
--   w7_tardio_vencedor accepted=false points_delta=16
--   w8 forca_de_antes: 1200 x 1000 -> a=+8 b=-8
--   w9_segundo_wo_do_par_a=0 b=0          2º WO pontuado do mesmo par no dia vale 0 para os dois
--   w10_teste=resolved sem_pontos=true    conta de teste: WO resolve, sem pontos
--   w11_sem_passe=resolved sem_pontos=true
--   w12_casual sem pontos=true winner=host   casual: vitória registrada, sem pontos
--   w13_award=not_eligible                award_online_match sem ACT dos dois lados
--   w14_milha 9->10 refund=1 depois_de_novo=10   milha devolvida uma vez
--   w14b_sem_gasto=sem refund             sem milha gasta, nada volta
--   w15_fantasma_invisivel=0 fresco_visivel=1 antigo_nulo_visivel=1   sala fantasma fora de find_quick_offer
--   w16_varredura=abandoned/winner nulo   sala ativa sem sinal de ninguém vira abandoned sem vencedor
--   w16_com_sinal=active
do $$
declare
  a uuid := gen_random_uuid(); b uuid := gen_random_uuid(); c uuid := gen_random_uuid();
  d uuid := gen_random_uuid(); e uuid := gen_random_uuid(); f uuid := gen_random_uuid();
  g uuid := gen_random_uuid(); h uuid := gen_random_uuid(); k uuid := gen_random_uuid(); l uuid := gen_random_uuid();
  room uuid := gen_random_uuid(); r uuid;
  out text := ''; szn text; x record; y record; n integer; v text; bal integer;
  t_old timestamptz := now() - interval '90 seconds';
begin
  select c0.season_key into szn from public.current_season() c0;
  insert into auth.users (id, email)
    select u, 'ensaio-' || u || '@ensaio.invalid' from unnest(array[a, b, c, d, e, f, g, h, k, l]) u;
  update public.profiles set is_test = false where id in (a, b, c, d, f, g, h, k, l);
  update public.profiles set is_test = true where id = e;
  -- todos com passe de temporada, menos f
  insert into public.season_passes (user_id, season_key, tier)
    select u, szn, 'free' from unnest(array[a, b, c, d, e, g, h, k, l]) u;

  -- o ensaio de find_quick_offer não pode pegar sala real de produção
  update public.online_matches set status = 'abandoned' where status = 'waiting';

  -- ------------------------------------------------------------ join grava joined_at
  insert into public.online_matches (id, host_id, host_name, is_quick_match, status)
    values (room, a, 'Alfa', true, 'waiting');
  perform set_config('request.jwt.claims', json_build_object('sub', b, 'role', 'authenticated')::text, true);
  perform * from public.join_online_match(room, 'Bravo');
  out := out || format('join_joined_at=%s | ',
    case when (select om.joined_at from public.online_matches om where om.id = room) is not null then 'ok' else 'FALHOU' end);

  -- ------------------------------------------------------------ W1: cedo demais
  update public.online_matches set joined_at = now() - interval '30 seconds', host_seen_at = now() - interval '5 seconds'
   where id = room;
  perform set_config('request.jwt.claims', json_build_object('sub', a, 'role', 'authenticated')::text, true);
  select * into x from public.resolve_walkover(room);
  out := out || format('w1=%s | ', x.outcome);

  -- ------------------------------------------------------------ W4: quem reivindica também sem sinal
  update public.online_matches set joined_at = t_old, host_seen_at = t_old - interval '10 seconds' where id = room;
  select * into x from public.resolve_walkover(room);
  out := out || format('w4=%s | ', x.outcome);

  -- ------------------------------------------------------------ W3: sinal do adversário cancela
  update public.online_matches set host_seen_at = now() - interval '2 seconds', guest_seen_at = t_old + interval '10 seconds'
   where id = room;
  select * into x from public.resolve_walkover(room);
  out := out || format('w3=%s | ', x.outcome);
  update public.online_matches set guest_seen_at = null where id = room;
  insert into public.online_messages (match_id, sender_id, body, created_at)
    values (room, b, 'FLEET|x', t_old + interval '20 seconds');
  select * into x from public.resolve_walkover(room);
  out := out || format('w3_msg=%s | ', x.outcome);
  delete from public.online_messages where match_id = room;

  -- ------------------------------------------------------------ W2: resolve
  select * into x from public.resolve_walkover(room);
  out := out || format('w2=%s vencedor_host=%s | ', x.outcome, x.winner = a);
  select om.status, om.walkover, om.host_result_recorded, om.guest_result_recorded into y
    from public.online_matches om where om.id = room;
  out := out || format('w2_sala=%s/walkover=%s/flags=%s,%s | ', y.status, y.walkover, y.host_result_recorded, y.guest_result_recorded);
  select string_agg(om.body || '(' || case when om.sender_id = b then 'guest' else 'host' end || ')', ' ' order by om.id)
    into v from public.online_messages om where om.match_id = room;
  out := out || format('w2_msgs=%s | ', v);
  select * into x from public.resolve_walkover(room);
  out := out || format('w2_de_novo=%s vencedor_host=%s | ', x.outcome, x.winner = a);
  perform set_config('request.jwt.claims', json_build_object('sub', b, 'role', 'authenticated')::text, true);
  select * into x from public.resolve_walkover(room);
  out := out || format('w2_ausente=%s vencedor_host=%s | ', x.outcome, x.winner = a);
  perform set_config('request.jwt.claims', json_build_object('sub', c, 'role', 'authenticated')::text, true);
  select count(*) into n from public.resolve_walkover(room);
  out := out || format('w2_terceiro=%s linhas | ', n);

  -- ------------------------------------------------------------ W5: o heartbeat de quem ficou resolve
  r := gen_random_uuid();
  insert into public.online_matches (id, host_id, guest_id, host_name, guest_name, status, joined_at)
    values (r, a, b, 'A', 'B', 'active', t_old);
  perform set_config('request.jwt.claims', json_build_object('sub', a, 'role', 'authenticated')::text, true);
  perform * from public.online_heartbeat(r);
  out := out || format('w5_heartbeat=%s | ', (select om.status from public.online_matches om where om.id = r));

  -- ------------------------------------------------------------ W6: reconexão antes do claim
  r := gen_random_uuid();
  insert into public.online_matches (id, host_id, guest_id, host_name, guest_name, status, joined_at, host_seen_at)
    values (r, a, b, 'A', 'B', 'active', t_old, t_old + interval '3 seconds');
  perform set_config('request.jwt.claims', json_build_object('sub', b, 'role', 'authenticated')::text, true);
  perform * from public.online_heartbeat(r);                       -- o ausente aparece
  perform set_config('request.jwt.claims', json_build_object('sub', a, 'role', 'authenticated')::text, true);
  perform * from public.online_heartbeat(r);                       -- o heartbeat de A não resolve mais
  out := out || format('w6_reconexao=%s | ', (select om.status from public.online_matches om where om.id = r));
  select * into x from public.resolve_walkover(r);
  out := out || format('w6_claim=%s | ', x.outcome);

  -- partida normal depois do prazo, os dois com sinal: nada a resolver
  out := out || format('w6b_antigo_sem_wo=%s | ', (select om.status from public.online_matches om where om.id = r));

  -- ------------------------------------------------------------ W7: ranqueada, soma zero
  r := gen_random_uuid();
  insert into public.online_matches (id, host_id, guest_id, host_name, guest_name, status, ranked, joined_at, host_seen_at)
    values (r, a, b, 'A', 'B', 'active', true, t_old, now() - interval '1 second');
  perform set_config('request.jwt.claims', json_build_object('sub', a, 'role', 'authenticated')::text, true);
  select * into x from public.resolve_walkover(r);
  select om.host_delta_s, om.guest_delta_s, (om.winner_id = a) as a_venceu into y from public.online_matches om where om.id = r;
  out := out || format('w7 outcome=%s a=%s b=%s soma_zero=%s geral=%s/%s temporada=%s/%s winner_id_a=%s deltas=%s/%s | ',
    x.outcome, x.points_delta, y.guest_delta_s,
    case when y.host_delta_s + y.guest_delta_s = 0 then 'ok' else 'FALHOU' end,
    (select p.ranked_rating from public.profiles p where p.id = a), (select p.ranked_rating from public.profiles p where p.id = b),
    (select s.points from public.ranked_season_stats s where s.user_id = a and s.season_key = szn),
    (select s.points from public.ranked_season_stats s where s.user_id = b and s.season_key = szn),
    y.a_venceu, y.host_delta_s, y.guest_delta_s);
  -- relatos tardios (app antigo): accepted = false, com os pontos gravados
  perform set_config('request.jwt.claims', json_build_object('sub', b, 'role', 'authenticated')::text, true);
  select * into x from public.record_ranked_result(r, false, 0, 0);
  out := out || format('w7_tardio_ausente accepted=%s points_delta=%s | ', x.accepted, x.points_delta);
  perform set_config('request.jwt.claims', json_build_object('sub', a, 'role', 'authenticated')::text, true);
  select * into x from public.record_ranked_result(r, true, 0, 5);
  out := out || format('w7_tardio_vencedor accepted=%s points_delta=%s | ', x.accepted, x.points_delta);

  -- ------------------------------------------------------------ W9: o segundo WO pontuado do par no dia
  r := gen_random_uuid();
  insert into public.online_matches (id, host_id, guest_id, host_name, guest_name, status, ranked, joined_at, host_seen_at)
    values (r, a, b, 'A', 'B', 'active', true, t_old, now() - interval '1 second');
  select * into x from public.resolve_walkover(r);
  select om.host_delta_s, om.guest_delta_s into y from public.online_matches om where om.id = r;
  out := out || format('w9_segundo_wo_do_par_a=%s b=%s outcome=%s | ', y.host_delta_s, y.guest_delta_s, x.outcome);

  -- ------------------------------------------------------------ W8: força de antes (1200 x 1000)
  update public.profiles set ranked_rating = 1200 where id = c;
  insert into public.ranked_season_stats (user_id, season_key, username, points, matches, wins) values (c, szn, 'EnsC', 1200, 5, 3);
  r := gen_random_uuid();
  insert into public.online_matches (id, host_id, guest_id, host_name, guest_name, status, ranked, joined_at, host_seen_at)
    values (r, c, d, 'C', 'D', 'active', true, t_old, now() - interval '1 second');
  perform set_config('request.jwt.claims', json_build_object('sub', c, 'role', 'authenticated')::text, true);
  select * into x from public.resolve_walkover(r);
  select om.host_delta_s, om.guest_delta_s into y from public.online_matches om where om.id = r;
  out := out || format('w8 forca_de_antes: 1200 x 1000 -> a=%s b=%s | ', y.host_delta_s, y.guest_delta_s);

  -- ------------------------------------------------------------ W10 e W11: teste e sem passe
  r := gen_random_uuid();
  insert into public.online_matches (id, host_id, guest_id, host_name, guest_name, status, ranked, joined_at, host_seen_at)
    values (r, e, g, 'E', 'G', 'active', true, t_old, now() - interval '1 second');
  perform set_config('request.jwt.claims', json_build_object('sub', e, 'role', 'authenticated')::text, true);
  select * into x from public.resolve_walkover(r);
  out := out || format('w10_teste=%s sem_pontos=%s | ', x.outcome,
    (select om.host_delta_s is null and om.guest_delta_s is null from public.online_matches om where om.id = r));
  r := gen_random_uuid();
  insert into public.online_matches (id, host_id, guest_id, host_name, guest_name, status, ranked, joined_at, host_seen_at)
    values (r, f, h, 'F', 'H', 'active', true, t_old, now() - interval '1 second');
  perform set_config('request.jwt.claims', json_build_object('sub', f, 'role', 'authenticated')::text, true);
  select * into x from public.resolve_walkover(r);
  out := out || format('w11_sem_passe=%s sem_pontos=%s | ', x.outcome,
    (select om.host_delta_s is null and om.guest_delta_s is null from public.online_matches om where om.id = r));

  -- ------------------------------------------------------------ W12: casual
  r := gen_random_uuid();
  insert into public.online_matches (id, host_id, guest_id, host_name, guest_name, status, ranked, joined_at, host_seen_at)
    values (r, g, h, 'G', 'H', 'active', false, t_old, now() - interval '1 second');
  perform set_config('request.jwt.claims', json_build_object('sub', g, 'role', 'authenticated')::text, true);
  select * into x from public.resolve_walkover(r);
  out := out || format('w12_casual sem_pontos=%s winner=%s | ',
    (select om.host_delta_s is null and om.guest_delta_s is null from public.online_matches om where om.id = r),
    case when x.winner = g then 'host' else 'OUTRO' end);

  -- ------------------------------------------------------------ W13: sem ACT, sem dobrões
  insert into public.dobroes_wallets (user_id, opening, source) values (g, 0, 'admin');
  select * into x from public.award_online_match(r, 5, 3, 1, 10, true);
  out := out || format('w13_award=%s | ', x.status);

  -- ------------------------------------------------------------ W14: milha devolvida uma vez
  r := gen_random_uuid();
  insert into public.online_matches (id, host_id, guest_id, host_name, guest_name, status, ranked, joined_at, host_seen_at)
    values (r, k, l, 'K', 'L', 'active', false, t_old, now() - interval '1 second');
  update public.profiles set nautical_miles = 9, miles_day = public.brt_today() where id = k;
  insert into public.mile_ledger (user_id, match_id, kind, amount) values (k, r, 'spend', -1);
  perform set_config('request.jwt.claims', json_build_object('sub', k, 'role', 'authenticated')::text, true);
  select * into x from public.resolve_walkover(r);
  select p.nautical_miles into bal from public.profiles p where p.id = k;
  out := out || format('w14_milha 9->%s refund=%s ', bal, (select count(*) from public.mile_ledger where user_id = k and match_id = r and kind = 'refund'));
  select * into x from public.resolve_walkover(r);
  select p.nautical_miles into bal from public.profiles p where p.id = k;
  out := out || format('depois_de_novo=%s | ', bal);
  -- sem milha gasta, nada volta
  r := gen_random_uuid();
  insert into public.online_matches (id, host_id, guest_id, host_name, guest_name, status, ranked, joined_at, host_seen_at)
    values (r, k, l, 'K', 'L', 'active', false, t_old, now() - interval '1 second');
  select * into x from public.resolve_walkover(r);
  out := out || format('w14b_sem_gasto=%s | ',
    case when (select count(*) from public.mile_ledger where user_id = k and match_id = r and kind = 'refund') = 0 then 'sem refund' else 'FALHOU' end);

  -- ------------------------------------------------------------ W15: sala fantasma fora de find_quick_offer
  insert into public.online_matches (id, host_id, host_name, is_quick_match, status, host_seen_at)
    values (gen_random_uuid(), a, 'Fantasma', true, 'waiting', now() - interval '60 seconds');
  perform set_config('request.jwt.claims', json_build_object('sub', c, 'role', 'authenticated')::text, true);
  select count(*) into n from public.find_quick_offer(true, true);
  out := out || format('w15_fantasma_invisivel=%s ', n);
  update public.online_matches set status = 'abandoned' where status = 'waiting';
  insert into public.online_matches (id, host_id, host_name, is_quick_match, status, host_seen_at)
    values (gen_random_uuid(), a, 'Fresco', true, 'waiting', now() - interval '5 seconds');
  select count(*) into n from public.find_quick_offer(true, true);
  out := out || format('fresco_visivel=%s ', n);
  update public.online_matches set status = 'abandoned' where status = 'waiting';
  insert into public.online_matches (id, host_id, host_name, is_quick_match, status)
    values (gen_random_uuid(), a, 'AppAntigo', true, 'waiting');
  select count(*) into n from public.find_quick_offer(true, true);
  out := out || format('antigo_nulo_visivel=%s | ', n);

  -- ------------------------------------------------------------ W16: varredura
  r := gen_random_uuid();
  insert into public.online_matches (id, host_id, guest_id, host_name, guest_name, status, joined_at)
    values (r, a, b, 'A', 'B', 'active', now() - interval '200 seconds');
  l := gen_random_uuid();   -- reaproveita a variável como id da sala com sinal
  insert into public.online_matches (id, host_id, guest_id, host_name, guest_name, status, joined_at, guest_seen_at)
    values (l, a, b, 'A', 'B', 'active', now() - interval '200 seconds', now() - interval '100 seconds');
  perform * from public.find_quick_offer(true, true);
  out := out || format('w16_varredura=%s/winner %s ',
    (select om.status from public.online_matches om where om.id = r),
    case when (select om.winner_id from public.online_matches om where om.id = r) is null then 'nulo' else 'FALHOU' end);
  out := out || format('w16_com_sinal=%s | ', (select om.status from public.online_matches om where om.id = l));

  raise exception 'RESULTADO (rollback): %', out;
end $$;
