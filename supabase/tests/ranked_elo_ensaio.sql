-- Ensaio que desfaz tudo (ON-11): fórmula do Elo de record_ranked_result (supabase/online.sql).
-- Rode no SQL Editor do projeto cwtslesnthbenxswdcbv. Serve para provar que o refactor da #85
-- (supabase/walkover.sql: record_ranked_result passa a usar apply_ranked_side) não muda a pontuação:
--   ANTES: cole só este bloco (a função de produção ainda é a de online.sql).
--   DEPOIS: cole o texto de walkover.sql e este bloco na MESMA execução (o raise no fim cancela a
--   transação inteira, DDL inclusive) e compare o RESULTADO com o do ANTES.
-- Usuários fictícios fora de teste, criados dentro do bloco; nenhuma conta real nem QA é tocada.
--
-- Resultado esperado, igual antes e depois (exceto o item relato_repetido):
--   s1_a=+16 s1_b=-16            1000 x 1000, vencedor relata primeiro
--   s2_c=-24 s2_d=+24            1200 x 1000, o PERDEDOR relata primeiro (força de antes, mesma conta)
--   s3_zero_sum=ok               ranked_rating e temporada somam o mesmo antes e depois
--   s4_sala4_a=0                 a 4ª ranqueada do dia contra o mesmo adversário não vale (limite 3)
--   s5_conflito=true             os dois dizem que venceram: o primeiro relato fixa o vencedor
--   s6_teste=0 linhas            conta de teste não pontua
--   s7_sem_passe=0 linhas        sem passe de temporada não pontua
--   relato_repetido accepted=false; points_delta: 0 ANTES do refactor, +16 DEPOIS (a #85 devolve o
--                                que ficou gravado na sala; é a única diferença esperada)
do $$
declare
  a uuid := gen_random_uuid(); b uuid := gen_random_uuid();
  c uuid := gen_random_uuid(); d uuid := gen_random_uuid();
  e uuid := gen_random_uuid(); f uuid := gen_random_uuid();
  g uuid := gen_random_uuid(); h uuid := gen_random_uuid();
  r1 uuid := gen_random_uuid(); r2 uuid := gen_random_uuid(); r3 uuid := gen_random_uuid();
  r4 uuid := gen_random_uuid(); r5 uuid := gen_random_uuid(); r6 uuid := gen_random_uuid();
  r7 uuid := gen_random_uuid(); r8 uuid := gen_random_uuid();
  out text := ''; szn text; ra record; rb record; n integer;
  sum_after integer;
begin
  select c0.season_key into szn from public.current_season() c0;
  insert into auth.users (id, email)
    select u, 'ensaio-' || u || '@ensaio.invalid' from unnest(array[a, b, c, d, e, f, g, h]) u;
  update public.profiles set is_test = false where id in (a, b, c, d, g, h);
  update public.profiles set is_test = true where id = e;
  update public.profiles set is_test = false where id = f;
  insert into public.season_passes (user_id, season_key, tier)
    select u, szn, 'free' from unnest(array[a, b, c, d, e, g, h]) u;  -- f fica sem passe

  -- c começa 1200 (temporada e geral), d em 1000
  insert into public.ranked_season_stats (user_id, season_key, username, points, matches, wins)
    values (c, szn, 'EnsE-c', 1200, 5, 3);
  update public.profiles set ranked_rating = 1200 where id = c;

  insert into public.online_matches (id, host_id, guest_id, host_name, guest_name, status, ranked)
    values (r1, a, b, 'A', 'B', 'active', true), (r2, c, d, 'C', 'D', 'active', true),
           (r3, a, b, 'A', 'B', 'active', true), (r4, a, b, 'A', 'B', 'active', true),
           (r5, a, b, 'A', 'B', 'active', true), (r6, g, h, 'G', 'H', 'active', true),
           (r7, e, a, 'E', 'A', 'active', true), (r8, f, a, 'F', 'A', 'active', true);

  -- S1: 1000 x 1000, A vence e relata primeiro
  perform set_config('request.jwt.claims', json_build_object('sub', a, 'role', 'authenticated')::text, true);
  select * into ra from public.record_ranked_result(r1, true, 70, 3);
  perform set_config('request.jwt.claims', json_build_object('sub', b, 'role', 'authenticated')::text, true);
  select * into rb from public.record_ranked_result(r1, false, 40, 0);
  out := out || format('s1_a=%s s1_b=%s (aceitos %s/%s) | ', ra.points_delta, rb.points_delta, ra.accepted, rb.accepted);

  -- relato repetido de A na mesma sala
  perform set_config('request.jwt.claims', json_build_object('sub', a, 'role', 'authenticated')::text, true);
  select * into ra from public.record_ranked_result(r1, true, 70, 3);
  out := out || format('relato_repetido accepted=%s points_delta=%s | ', ra.accepted, ra.points_delta);

  -- S2: 1200 x 1000, o perdedor (C) relata primeiro
  perform set_config('request.jwt.claims', json_build_object('sub', c, 'role', 'authenticated')::text, true);
  select * into ra from public.record_ranked_result(r2, false, 30, 0);
  perform set_config('request.jwt.claims', json_build_object('sub', d, 'role', 'authenticated')::text, true);
  select * into rb from public.record_ranked_result(r2, true, 60, 2);
  out := out || format('s2_c=%s s2_d=%s | ', ra.points_delta, rb.points_delta);

  -- S3: soma zero (temporada e geral) nas duas salas
  select coalesce(sum(s.points), 0) - 4200 into sum_after
    from public.ranked_season_stats s where s.user_id in (a, b, c, d) and s.season_key = szn;
  out := out || format('s3_zero_sum=%s | ',
    case when sum_after = 0
          and (select sum(p.ranked_rating) from public.profiles p where p.id in (a, b, c, d)) = 4200
         then 'ok' else 'FALHOU(' || sum_after || ')' end);

  -- S4: limite diário do par A x B (3): r1, r3 e r4 valem; a 4ª ranqueada do dia (r5) não
  perform set_config('request.jwt.claims', json_build_object('sub', a, 'role', 'authenticated')::text, true);
  select * into ra from public.record_ranked_result(r3, true, 50, 3);
  perform set_config('request.jwt.claims', json_build_object('sub', b, 'role', 'authenticated')::text, true);
  perform * from public.record_ranked_result(r3, false, 50, 3);
  perform set_config('request.jwt.claims', json_build_object('sub', a, 'role', 'authenticated')::text, true);
  perform * from public.record_ranked_result(r4, true, 50, 3);
  perform set_config('request.jwt.claims', json_build_object('sub', b, 'role', 'authenticated')::text, true);
  perform * from public.record_ranked_result(r4, false, 50, 3);
  perform set_config('request.jwt.claims', json_build_object('sub', a, 'role', 'authenticated')::text, true);
  select * into ra from public.record_ranked_result(r5, true, 50, 3);
  out := out || format('s4_sala4_a=%s | ', ra.points_delta);

  -- S5: os dois dizem que venceram -> conflito, o primeiro relato fixa o vencedor
  perform set_config('request.jwt.claims', json_build_object('sub', g, 'role', 'authenticated')::text, true);
  perform * from public.record_ranked_result(r6, true, 50, 3);
  perform set_config('request.jwt.claims', json_build_object('sub', h, 'role', 'authenticated')::text, true);
  perform * from public.record_ranked_result(r6, true, 50, 3);
  out := out || format('s5_conflito=%s vencedor_g=%s | ',
    (select om.result_conflict from public.online_matches om where om.id = r6),
    (select om.winner_id = g from public.online_matches om where om.id = r6));

  -- S6: conta de teste de um dos lados
  perform set_config('request.jwt.claims', json_build_object('sub', a, 'role', 'authenticated')::text, true);
  select count(*) into n from public.record_ranked_result(r7, true, 50, 3);
  out := out || format('s6_teste=%s linhas | ', n);

  -- S7: sem passe de temporada (f) — quem não tem passe é barrado
  perform set_config('request.jwt.claims', json_build_object('sub', f, 'role', 'authenticated')::text, true);
  select count(*) into n from public.record_ranked_result(r8, true, 50, 3);
  out := out || format('s7_sem_passe=%s linhas | ', n);

  raise exception 'RESULTADO (rollback): %', out;
end $$;
