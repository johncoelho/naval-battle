-- Ensaio que desfaz tudo (#35, 0.34.5): brinde (Prêmio Beta, vitrine beta, Prêmio legado) só para
-- as contas QA e os e-mails de app_config.beta_gift_emails (a conta do John). Rodar no SQL Editor do
-- projeto cwtslesnthbenxswdcbv depois de supabase/dobroes.sql. O raise no fim cancela a transação:
-- nada fica gravado (nem o resgate do Prêmio feito aqui dentro).
-- Sem e-mail no resultado, só contagens e booleanos. Se beta_gift_emails ainda estiver vazio no
-- banco, o bloco usa ads_test_emails só dentro da transação (mesma conta).
--
-- Resultado esperado:
--   qa_contas=2 qa_elegiveis=2           QA01 e QA02 seguem com brinde
--   john_contas=1 john_elegivel=1        a conta do John segue com brinde
--   testadores_antigos=N elegiveis=0     lista da Play e manual (fora o John) perdem o brinde novo
--   conta_nova_elegivel=false            conta comum nova da loja não tem brinde
--   premio_testador=not_beta             claim_premio_beta() recusado para testador antigo
--   premio_qa=premio|already             QA segue pegando (already se já pegou hoje)
--   loja_testador beta=f vitrine=f       dobroes_store_status() esconde Prêmio e vitrine
--   badges_iguais=true premios_iguais=true   medalha Beta Tester e Prêmios já pagos intactos

do $$
declare
  cid uuid := gen_random_uuid();
  qa uuid; tester uuid;
  qa_n int; qa_ok int; jo_n int; jo_ok int; tt_n int; tt_ok int;
  badges_antes int; badges_depois int; pr_antes int; pr_depois int;
  st record;
  out text := '';
begin
  select count(*) into badges_antes from public.user_badges where badge_code = 'beta_tester';
  select count(*) into pr_antes from public.dobroes_purchases where premio and created_at < now();

  -- e-mail do John: só no banco (copiado de ads_test_emails dentro do bloco, se faltar)
  if coalesce((select value from public.app_config where key = 'beta_gift_emails'), '') = '' then
    insert into public.app_config (key, value)
      select 'beta_gift_emails', value from public.app_config where key = 'ads_test_emails'
    on conflict (key) do update set value = excluded.value;
  end if;

  -- contas QA
  select count(*), count(*) filter (where public.beta_eligible(u.id)) into qa_n, qa_ok
    from public.allowed_players a join auth.users u on u.id = a.user_id or lower(trim(u.email)) = a.email
   where a.source = 'qa';
  out := out || format('qa_contas=%s qa_elegiveis=%s | ', qa_n, qa_ok);

  -- conta do John
  select count(*), count(*) filter (where public.beta_eligible(u.id)) into jo_n, jo_ok
    from auth.users u
   where lower(trim(u.email)) = any (string_to_array(replace(lower(coalesce(
           (select value from public.app_config where key = 'beta_gift_emails'), '')), ' ', ''), ','));
  out := out || format('john_contas=%s john_elegivel=%s | ', jo_n, jo_ok);

  -- testadores antigos (play/manual), fora QA e John
  select count(distinct u.id), count(distinct u.id) filter (where public.beta_eligible(u.id))
    into tt_n, tt_ok
    from public.allowed_players a join auth.users u on u.id = a.user_id or lower(trim(u.email)) = a.email
   where a.source in ('play', 'manual')
     and not public.is_qa_account(u.id)
     and lower(trim(u.email)) <> all (string_to_array(replace(lower(coalesce(
           (select value from public.app_config where key = 'beta_gift_emails'), '')), ' ', ''), ','));
  out := out || format('testadores_antigos=%s elegiveis=%s | ', tt_n, tt_ok);

  -- conta comum nova (some no rollback)
  insert into auth.users (id, aud, role, email)
    values (cid, 'authenticated', 'authenticated', 'ensaio.brinde@gmail.com');
  out := out || format('conta_nova_elegivel=%s | ', public.beta_eligible(cid));

  -- resgate do Prêmio como testador antigo e como QA (o insert do QA some no rollback)
  select u.id into tester
    from public.allowed_players a join auth.users u on u.id = a.user_id or lower(trim(u.email)) = a.email
   where a.source in ('play', 'manual') and not public.beta_eligible(u.id) limit 1;
  select u.id into qa
    from public.allowed_players a join auth.users u on u.id = a.user_id or lower(trim(u.email)) = a.email
   where a.source = 'qa' limit 1;
  perform set_config('request.jwt.claims', json_build_object('sub', tester, 'role', 'authenticated')::text, true);
  out := out || format('premio_testador=%s | ', public.claim_premio_beta());
  select * into st from public.dobroes_store_status();
  out := out || format('loja_testador beta=%s vitrine=%s | ', st.beta, st.vitrine_beta);
  perform set_config('request.jwt.claims', json_build_object('sub', qa, 'role', 'authenticated')::text, true);
  out := out || format('premio_qa=%s | ', public.claim_premio_beta());

  -- nada já ganho mudou (o Prêmio do QA de agora não conta)
  -- (a conta de ensaio ganha a medalha pelo gatilho grant_beta_badge enquanto beta_ends_at for nulo)
  select count(*) into badges_depois from public.user_badges where badge_code = 'beta_tester' and user_id <> cid;
  -- (now() é o início da transação: a linha criada aqui dentro tem created_at = now())
  select count(*) into pr_depois from public.dobroes_purchases where premio and created_at < now();
  out := out || format('badges_iguais=%s premios_iguais=%s', badges_antes = badges_depois, pr_antes = pr_depois);

  raise exception 'RESULTADO (rollback): %', out;
end $$;
