-- Ensaio que desfaz tudo (#28, 0.33.3): compras de dobrões, Prêmio Beta e gate da licença.
-- Rodar no SQL Editor do projeto cwtslesnthbenxswdcbv depois de supabase/dobroes.sql.
-- O raise no fim cancela a transação: nada fica gravado. As flags são ligadas e desligadas só
-- dentro do bloco; o rollback devolve os valores reais de app_config.
--
-- Resultado esperado (cada item vira um trecho de "RESULTADO"):
--   claim_off=off                 Prêmio com premio_beta_enabled 'false'           (DOB-19)
--   claim_1=premio                Prêmio com a flag ligada, 1ª vez no dia          (DOB-19)
--   claim_2=already               mesmo dia, de novo                               (DOB-19)
--   pendente_premio=1             dobroes_pending mostra o Prêmio uma vez          (DOB-19)
--   vitrine_1=credited            compra de teste da vitrine, testador, store on   (DOB-13, DOB-16)
--   vitrine_repetido=already      mesmo token de novo                              (DOB-13)
--   vitrine_novo_token=credited   vitrine não tem limite por dia                   (DOB-16)
--   legado_novo_token=premio_done origem nula (app 0.33.0-0.33.2), Prêmio já pego   (DOB-13, DOB-21)
--   legado_outro_produto=premio_only pacote que não é o Tesouro no legado          (DOB-21)
--   nao_beta_teste=not_beta       conta fora da licença, compra de teste           (DOB-14)
--   nao_beta_real=billing_off     conta fora da licença, compra real, real off     (DOB-14)
--   store_off_teste=billing_off   testador, vitrine desligada                      (DOB-17)
--   estado_pendente=not_purchased Play em pendente (2)                             (DOB-15)
--   estado_cancelado=not_purchased Play cancelada (1)                              (DOB-15)
--   produto_desconhecido=unknown_product                                           (DOB-15)
--   conta_nao_confere=account_mismatch                                             (DOB-15)
--   promocao=promo_refused                                                         (DOB-15)
--   real_store_on=credited        compra real de testador com vitrine ligada       (DOB-18)
--   real_store_off=billing_off    mesma compra com as duas flags desligadas        (DOB-18)
--   gate_antes=1                  conta sem 0.33.3 aparece no gate                 (DOB-20)
--   gate_depois=0                 com device_check 0.33.3 some                     (DOB-20)
--   versao_10_vs_9=true           app_version_at_least('0.33.10','0.33.3')         (DOB-20)
--   versao_antiga=false           app_version_at_least('0.33.2','0.33.3')          (DOB-20)

do $$
declare
  a uuid; b uuid; tk text; st text; n int; p2 text; out text := '';
begin
  select id into a from auth.users where email = 'aigamesfactory.qa01@gmail.com';
  select id into b from auth.users where email = 'aigamesfactory.qa02@gmail.com';

  -- cenário limpo para as contas QA (tudo volta no rollback)
  delete from public.allowed_players where user_id in (a, b) or email in ('aigamesfactory.qa01@gmail.com', 'aigamesfactory.qa02@gmail.com');
  delete from public.device_checks where user_id in (a, b);
  delete from public.dobroes_purchases where user_id in (a, b);
  insert into public.app_config (key, value) values
    ('real_billing_enabled', 'false'), ('premio_beta_enabled', 'false'), ('beta_store_enabled', 'false')
    on conflict (key) do update set value = excluded.value;
  insert into public.allowed_players (user_id, source, note) values (a, 'manual', 'ensaio-dobroes');
  select d.product_id into p2 from public.dobroes_products d where d.product_id <> 'dobroes_tesouro' order by d.doubloons limit 1;

  -- DOB-19: Prêmio com a flag desligada, depois ligada, duas vezes
  perform set_config('request.jwt.claims', json_build_object('sub', a, 'role', 'authenticated')::text, true);
  st := public.claim_premio_beta(); out := out || 'claim_off=' || st || ' | ';
  update public.app_config set value = 'true' where key = 'premio_beta_enabled';
  st := public.claim_premio_beta(); out := out || 'claim_1=' || st || ' | ';
  st := public.claim_premio_beta(); out := out || 'claim_2=' || st || ' | ';
  select count(*) into n from public.dobroes_pending() p where p.premio;
  out := out || 'pendente_premio=' || n || ' | ';

  -- DOB-13 / DOB-16: vitrine com a loja beta ligada (testador)
  update public.app_config set value = 'true' where key = 'beta_store_enabled';
  tk := 'ensaio-dob-v1-' || replace(gen_random_uuid()::text, '-', '');
  select s.status into st from public.apply_dobroes_purchase(a, 'dobroes_tesouro', tk, 'o-' || tk, 0, 0, true, 'vitrine') s;
  out := out || 'vitrine_1=' || st || ' | ';
  select s.status into st from public.apply_dobroes_purchase(a, 'dobroes_tesouro', tk, 'o-' || tk, 0, 0, true, 'vitrine') s;
  out := out || 'vitrine_repetido=' || st || ' | ';
  select s.status into st from public.apply_dobroes_purchase(a, 'dobroes_tesouro', 'ensaio-dob-v2-' || replace(gen_random_uuid()::text, '-', ''), 'o2', 0, 0, true, 'vitrine') s;
  out := out || 'vitrine_novo_token=' || st || ' | ';

  -- DOB-13 / DOB-21: legado (origem nula) com o Prêmio de hoje já pego, e pacote de outro produto
  select s.status into st from public.apply_dobroes_purchase(a, 'dobroes_tesouro', 'ensaio-dob-l1-' || replace(gen_random_uuid()::text, '-', ''), 'o3', 0, 0, true, null) s;
  out := out || 'legado_novo_token=' || st || ' | ';
  select s.status into st from public.apply_dobroes_purchase(a, p2, 'ensaio-dob-l2-' || replace(gen_random_uuid()::text, '-', ''), 'o4', 0, 0, true, null) s;
  out := out || 'legado_outro_produto=' || st || ' | ';

  -- DOB-14: conta fora da licença (b não está em allowed_players)
  select s.status into st from public.apply_dobroes_purchase(b, 'dobroes_tesouro', 'ensaio-dob-n1-' || replace(gen_random_uuid()::text, '-', ''), 'o5', 0, 0, true, 'vitrine') s;
  out := out || 'nao_beta_teste=' || st || ' | ';
  select s.status into st from public.apply_dobroes_purchase(b, 'dobroes_tesouro', 'ensaio-dob-n2-' || replace(gen_random_uuid()::text, '-', ''), 'o6', null, 0, true, null) s;
  out := out || 'nao_beta_real=' || st || ' | ';

  -- DOB-17: testador com vitrine desligada
  update public.app_config set value = 'false' where key = 'beta_store_enabled';
  select s.status into st from public.apply_dobroes_purchase(a, 'dobroes_tesouro', 'ensaio-dob-s1-' || replace(gen_random_uuid()::text, '-', ''), 'o7', 0, 0, true, 'vitrine') s;
  out := out || 'store_off_teste=' || st || ' | ';

  -- DOB-15: estados e conferências que não creditam
  select s.status into st from public.apply_dobroes_purchase(a, 'dobroes_tesouro', 'ensaio-dob-e1-' || replace(gen_random_uuid()::text, '-', ''), 'o8', null, 2, true, null) s;
  out := out || 'estado_pendente=' || st || ' | ';
  select s.status into st from public.apply_dobroes_purchase(a, 'dobroes_tesouro', 'ensaio-dob-e2-' || replace(gen_random_uuid()::text, '-', ''), 'o9', null, 1, true, null) s;
  out := out || 'estado_cancelado=' || st || ' | ';
  select s.status into st from public.apply_dobroes_purchase(a, 'produto_fantasma', 'ensaio-dob-e3-' || replace(gen_random_uuid()::text, '-', ''), 'o10', null, 0, true, null) s;
  out := out || 'produto_desconhecido=' || st || ' | ';
  select s.status into st from public.apply_dobroes_purchase(a, 'dobroes_tesouro', 'ensaio-dob-e4-' || replace(gen_random_uuid()::text, '-', ''), 'o11', null, 0, false, null) s;
  out := out || 'conta_nao_confere=' || st || ' | ';
  select s.status into st from public.apply_dobroes_purchase(a, 'dobroes_tesouro', 'ensaio-dob-e5-' || replace(gen_random_uuid()::text, '-', ''), 'o12', 1, 0, true, null) s;
  out := out || 'promocao=' || st || ' | ';

  -- DOB-18: compra real de testador (a, manual, fora da licença da Play)
  update public.app_config set value = 'true' where key = 'beta_store_enabled';
  select s.status into st from public.apply_dobroes_purchase(a, 'dobroes_tesouro', 'ensaio-dob-r1-' || replace(gen_random_uuid()::text, '-', ''), 'o13', null, 0, true, null) s;
  out := out || 'real_store_on=' || st || ' | ';
  update public.app_config set value = 'false' where key = 'beta_store_enabled';
  select s.status into st from public.apply_dobroes_purchase(a, 'dobroes_tesouro', 'ensaio-dob-r2-' || replace(gen_random_uuid()::text, '-', ''), 'o14', null, 0, true, null) s;
  out := out || 'real_store_off=' || st || ' | ';

  -- DOB-20: gate da licença e comparação de versão
  select count(*) into n from public.dobroes_gate_licenca() g where g.user_id = a;
  out := out || 'gate_antes=' || n || ' | ';
  insert into public.device_checks (user_id, app_version, platform, test_lab) values (a, '0.33.3', 'android', true);
  select count(*) into n from public.dobroes_gate_licenca() g where g.user_id = a;
  out := out || 'gate_depois=' || n || ' | ';
  out := out || 'versao_10_vs_9=' || public.app_version_at_least('0.33.10', '0.33.3') || ' | ';
  out := out || 'versao_antiga=' || public.app_version_at_least('0.33.2', '0.33.3') || ' | ';

  raise exception 'RESULTADO (rollback): %', out;
end $$;
