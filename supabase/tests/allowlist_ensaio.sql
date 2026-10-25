-- Ensaio que desfaz tudo (#35, 0.34.5): regra de is_test por robô da Google, QA fora da rotina,
-- lista da Play sem efeito em is_test. Rodar no SQL Editor do projeto cwtslesnthbenxswdcbv depois
-- de supabase/test_accounts.sql. O raise no fim cancela a transação: nada fica gravado. O
-- interruptor allowlist_enforced é ligado só dentro do bloco (já está 'true' em produção).
-- A passada de produção NÃO roda aqui: a contagem usa refresh_all_is_test(false), que só conta.
--
-- Resultado esperado (cada item vira um trecho de "RESULTADO"; sem e-mail, só contagens):
--   antes_teste / antes_jogador     profiles.is_test antes do ensaio (real)
--   passada_virariam_teste          contas que a passada tiraria de jogador (p_apply = false)
--   passada_voltariam_jogador       contas de teste que a passada devolveria a jogador
--   jogadores_depois / testes_depois totais depois da passada, em conta
--   robo_novo_teste=true            e-mail nome.00000@gmail.com nasce is_test true
--   comum_novo_teste=false          conta comum nasce is_test false (aparece no ranking)
--   play_nao_decide_teste=false     linha 'play' na lista não muda is_test
--   play_beta_eligible=true         beta_eligible segue igual (lista da Play)
--   qa_fica_intacta=true            conta QA não muda com refresh_is_test
--   mismatch_depois=0               nenhuma conta (fora QA) fora da regra depois

do $$
declare
  rid uuid := gen_random_uuid();
  cid uuid := gen_random_uuid();
  qa uuid;
  pa record;
  rb boolean; cm boolean; cm2 boolean; qa_antes boolean; qa_depois boolean;
  bt int; bj int; mism int;
  out text := '';
begin
  insert into public.app_config (key, value) values ('allowlist_enforced', 'true')
  on conflict (key) do update set value = 'true';

  select count(*) filter (where is_test), count(*) filter (where not is_test)
    into bt, bj from public.profiles;
  out := out || format('antes_teste=%s antes_jogador=%s | ', bt, bj);

  -- passada só de conta: não aplica nada
  select * into pa from public.refresh_all_is_test(false);
  out := out || format('passada_virariam_teste=%s passada_voltariam_jogador=%s jogadores_depois=%s testes_depois=%s | ',
                       pa.to_test, pa.to_player, pa.players_after, pa.tests_after);

  -- contas de ensaio (somem no rollback); o trigger classify_new_profile classifica ao criar o perfil
  insert into auth.users (id, aud, role, email)
    values (rid, 'authenticated', 'authenticated', 'ensaio.12345@gmail.com');
  insert into auth.users (id, aud, role, email)
    values (cid, 'authenticated', 'authenticated', 'ensaio.comum@gmail.com');

  select is_test into rb from public.profiles where id = rid;
  select is_test into cm from public.profiles where id = cid;
  out := out || format('robo_novo_teste=%s comum_novo_teste=%s | ', rb, cm);

  -- lista da Play: inserir não muda is_test, mas benefício segue
  insert into public.allowed_players (email, source, note) values ('ensaio.comum@gmail.com', 'play', 'ensaio');
  select is_test into cm2 from public.profiles where id = cid;
  out := out || format('play_nao_decide_teste=%s play_beta_eligible=%s | ', cm2, public.beta_eligible(cid));

  -- conta QA: inverte só dentro do bloco e confere que refresh_is_test não mexe
  select u.id into qa
    from public.allowed_players a join auth.users u on lower(trim(u.email)) = a.email
   where a.source = 'qa' limit 1;
  if qa is not null then
    update public.profiles set is_test = not is_test where id = qa;
    select is_test into qa_antes from public.profiles where id = qa;
    perform public.refresh_is_test(qa);
    select is_test into qa_depois from public.profiles where id = qa;
    out := out || format('qa_fica_intacta=%s | ', qa_antes = qa_depois);
  else
    out := out || 'qa_fica_intacta=sem_conta_qa | ';
  end if;

  -- depois de tudo: quem está fora da regra (QA de fora)
  select count(*) into mism from public.profiles p
   where not public.is_qa_account(p.id)
     and p.is_test is distinct from public.is_robot_account(p.id);
  out := out || format('mismatch_depois=%s', mism);

  raise exception 'RESULTADO (rollback): %', out;
end $$;
