-- Ensaio que desfaz tudo (#65, fatia 1): compra de dobrões no iPhone e estorno (supabase/dobroes_ios.sql).
-- Rodar no SQL Editor do projeto cwtslesnthbenxswdcbv. Para ensaiar ANTES de aplicar a migração, cole o
-- texto de supabase/dobroes_ios.sql e este bloco na mesma execução: o raise no fim cancela a transação
-- inteira, DDL inclusive, e nada fica gravado. Depois de aplicada, rode só este bloco. Usa as contas QA01
-- (testador, source 'qa') e QA02 (conta comum), nunca jogador real. As flags são ligadas e desligadas só
-- dentro do bloco; o rollback devolve os valores reais de app_config.
--
-- Resultado esperado (cada item vira um trecho de "RESULTADO"; casos DOBIOS-10 em diante do QA_TEST_PLAN):
--   flag_nasce=false              real_billing_ios_enabled nasce 'false' (seed, antes de o bloco mexer) (DOBIOS-10)
--   off_status=billing_off        Production com a flag desligada, conta comum                         (DOBIOS-10)
--   off_gravou=0                  billing_off não grava linha                                          (DOBIOS-10)
--   credita=credited/500          Production, flag ligada, 1ª vez                                      (DOBIOS-11)
--   credita_saldo=500             razão 'purchase:real' somou na carteira                              (DOBIOS-11)
--   repete=already/0              mesmo transactionId de novo                                          (DOBIOS-11)
--   repete_saldo=500              não soma 2x                                                          (DOBIOS-11)
--   pendente=1                    dobroes_pending mostra a compra uma vez                              (DOBIOS-11)
--   revogada=not_purchased        transação com revocationDate                                         (DOBIOS-12)
--   produto_ruim=unknown_product                                                                       (DOBIOS-12)
--   conta_ruim=account_mismatch   appAccountToken != usuário                                           (DOBIOS-12)
--   revogada_gravou=0             nenhuma das três grava nem credita                                   (DOBIOS-12)
--   sbx_comum=sandbox_refused     Sandbox em conta comum, mesmo com a flag ligada                      (DOBIOS-13)
--   sbx_comum_saldo=500           sem crédito                                                          (DOBIOS-13)
--   sbx_comum_repete=already      não volta (finish true) e não grava de novo                          (DOBIOS-13)
--   sbx_qa_loja_off=sandbox_refused QA com beta_store_enabled desligado                                (DOBIOS-13)
--   sbx_qa=credited/500           Sandbox em QA com a vitrine beta ligada                              (DOBIOS-14)
--   sbx_qa_kind=test/ios/Sandbox  linha gravada como teste                                             (DOBIOS-14)
--   sbx_qa_razao=purchase:test    razão do crédito de teste                                            (DOBIOS-14)
--   prod_qa=credited/1100         Production, QA, flag geral desligada, vitrine ligada                 (DOBIOS-14)
--   prod_qa_loja_off=billing_off  mesma compra com as duas flags desligadas                            (DOBIOS-10)
--   play_intocado=0               nenhuma linha 'play' criada: o Android não é tocado                  (DOBIOS-15)
--   estorno_ok=refunded/500/0     estorno com saldo suficiente                                         (DOBIOS-16)
--   estorno_saldo=1100            saldo desceu só os 500 da compra                                     (DOBIOS-16)
--   estorno_repete=already_refunded/0/0                                                                (DOBIOS-16)
--   estorno_repete_saldo=1100     estorno repetido não debita de novo                                  (DOBIOS-16)
--   estorno_curto=refunded/300/800 saldo 300 para uma compra de 1100: debita até zerar                 (DOBIOS-17)
--   estorno_curto_saldo=0         nunca negativo                                                       (DOBIOS-17)
--   divida=800                    dobroes_refund_shortfall guarda a diferença                          (DOBIOS-17)
--   razao_estornos=2              duas linhas 'refund:ios' no razão                                    (DOBIOS-16)
--   estorno_sem_credito=refunded/0/0 estorno de compra sandbox_refused: só marca, sem débito           (DOBIOS-16)
--   estorno_desconhecido=unknown_purchase                                                              (DOBIOS-16)
--   service_only=false/false      authenticated e anon não executam as duas RPCs                       (DOBIOS-18)

do $$
declare
  a uuid; b uuid; r record; n int; bal int; tk text; out text := '';
begin
  select id into a from auth.users where email = 'aigamesfactory.qa01@gmail.com';
  select id into b from auth.users where email = 'aigamesfactory.qa02@gmail.com';

  out := out || 'flag_nasce=' || coalesce((select value from public.app_config where key = 'real_billing_ios_enabled'), 'ausente') || ' | ';

  -- cenário limpo para as contas QA (tudo volta no rollback)
  delete from public.dobroes_refund_shortfall where user_id in (a, b);
  delete from public.dobroes_ledger where user_id in (a, b);
  delete from public.dobroes_wallets where user_id in (a, b);
  delete from public.dobroes_purchases where user_id in (a, b);
  delete from public.allowed_players where user_id in (a, b) or email in ('aigamesfactory.qa01@gmail.com', 'aigamesfactory.qa02@gmail.com');
  insert into public.allowed_players (user_id, source, note) values (a, 'qa', 'ensaio-dobroes-ios');
  update public.profiles set credits = 0 where id in (a, b);
  insert into public.dobroes_wallets (user_id, opening, source) values (a, 0, 'admin'), (b, 0, 'admin');
  insert into public.app_config (key, value) values
    ('real_billing_ios_enabled', 'false'), ('beta_store_enabled', 'false'), ('beta_gift_emails', '')
    on conflict (key) do update set value = excluded.value;

  -- DOBIOS-10: flag desligada, conta comum
  select * into r from public.apply_dobroes_purchase_ios(b, 'dobroes_bolsa', '1001', 'Production', true, false);
  out := out || 'off_status=' || r.status || ' | ';
  select count(*) into n from public.dobroes_purchases where user_id = b;
  out := out || 'off_gravou=' || n || ' | ';

  -- DOBIOS-11: flag ligada, credita uma vez
  update public.app_config set value = 'true' where key = 'real_billing_ios_enabled';
  select * into r from public.apply_dobroes_purchase_ios(b, 'dobroes_bolsa', '1001', 'Production', true, false);
  out := out || 'credita=' || r.status || '/' || r.doubloons || ' | ';
  select credits into bal from public.profiles where id = b;
  out := out || 'credita_saldo=' || bal || ' | ';
  select * into r from public.apply_dobroes_purchase_ios(b, 'dobroes_bolsa', '1001', 'Production', true, false);
  out := out || 'repete=' || r.status || '/' || r.doubloons || ' | ';
  select credits into bal from public.profiles where id = b;
  out := out || 'repete_saldo=' || bal || ' | ';
  perform set_config('request.jwt.claims', json_build_object('sub', b, 'role', 'authenticated')::text, true);
  select count(*) into n from public.dobroes_pending();
  out := out || 'pendente=' || n || ' | ';

  -- DOBIOS-12: recusas que não gravam
  select * into r from public.apply_dobroes_purchase_ios(b, 'dobroes_bau', '1002', 'Production', true, true);
  out := out || 'revogada=' || r.status || ' | ';
  select * into r from public.apply_dobroes_purchase_ios(b, 'dobroes_inexistente', '1002', 'Production', true, false);
  out := out || 'produto_ruim=' || r.status || ' | ';
  select * into r from public.apply_dobroes_purchase_ios(b, 'dobroes_bau', '1002', 'Production', false, false);
  out := out || 'conta_ruim=' || r.status || ' | ';
  select count(*) into n from public.dobroes_purchases where purchase_token = 'ios:1002';
  out := out || 'revogada_gravou=' || n || ' | ';

  -- DOBIOS-13: Sandbox nunca credita conta comum (flag geral ligada)
  select * into r from public.apply_dobroes_purchase_ios(b, 'dobroes_bolsa', '2001', 'Sandbox', true, false);
  out := out || 'sbx_comum=' || r.status || ' | ';
  select credits into bal from public.profiles where id = b;
  out := out || 'sbx_comum_saldo=' || bal || ' | ';
  select * into r from public.apply_dobroes_purchase_ios(b, 'dobroes_bolsa', '2001', 'Sandbox', true, false);
  out := out || 'sbx_comum_repete=' || r.status || ' | ';
  select * into r from public.apply_dobroes_purchase_ios(a, 'dobroes_bolsa', '2002', 'Sandbox', true, false);
  out := out || 'sbx_qa_loja_off=' || r.status || ' | ';

  -- DOBIOS-14: QA com a vitrine beta ligada
  update public.app_config set value = 'true' where key = 'beta_store_enabled';
  select * into r from public.apply_dobroes_purchase_ios(a, 'dobroes_bolsa', '2003', 'Sandbox', true, false);
  out := out || 'sbx_qa=' || r.status || '/' || r.doubloons || ' | ';
  select d.kind || '/' || d.store || '/' || d.environment into tk from (select kind, store, environment from public.dobroes_purchases where purchase_token = 'ios:2003') d;
  out := out || 'sbx_qa_kind=' || tk || ' | ';
  out := out || 'sbx_qa_razao=' || (select l.reason from public.dobroes_ledger l where l.user_id = a and l.ref = 'ios:2003') || ' | ';
  update public.app_config set value = 'false' where key = 'real_billing_ios_enabled';
  select * into r from public.apply_dobroes_purchase_ios(a, 'dobroes_bau', '3001', 'Production', true, false);
  out := out || 'prod_qa=' || r.status || '/' || r.doubloons || ' | ';
  update public.app_config set value = 'false' where key = 'beta_store_enabled';
  select * into r from public.apply_dobroes_purchase_ios(a, 'dobroes_bau', '3002', 'Production', true, false);
  out := out || 'prod_qa_loja_off=' || r.status || ' | ';

  -- DOBIOS-15: o Android não é tocado
  select count(*) into n from public.dobroes_purchases where user_id in (a, b) and store = 'play';
  out := out || 'play_intocado=' || n || ' | ';

  -- DOBIOS-16: estorno com saldo suficiente (b tem 500 da 1001; soma uma compra de 1100 para ter folga)
  update public.app_config set value = 'true' where key = 'real_billing_ios_enabled';
  select * into r from public.apply_dobroes_purchase_ios(b, 'dobroes_bau', '1003', 'Production', true, false);
  select * into r from public.apply_dobroes_refund_ios('1001', 'Production');
  out := out || 'estorno_ok=' || r.status || '/' || r.debited || '/' || r.shortfall || ' | ';
  select credits into bal from public.profiles where id = b;
  out := out || 'estorno_saldo=' || bal || ' | ';
  select * into r from public.apply_dobroes_refund_ios('1001', 'Production');
  out := out || 'estorno_repete=' || r.status || '/' || r.debited || '/' || r.shortfall || ' | ';
  select credits into bal from public.profiles where id = b;
  out := out || 'estorno_repete_saldo=' || bal || ' | ';

  -- DOBIOS-17: saldo menor que a compra: debita até zerar, nunca negativo, e registra a dívida
  update public.profiles set credits = 300 where id = b;
  select * into r from public.apply_dobroes_refund_ios('1003', 'Production');
  out := out || 'estorno_curto=' || r.status || '/' || r.debited || '/' || r.shortfall || ' | ';
  select credits into bal from public.profiles where id = b;
  out := out || 'estorno_curto_saldo=' || bal || ' | ';
  out := out || 'divida=' || (select s.shortfall from public.dobroes_refund_shortfall s where s.purchase_token = 'ios:1003') || ' | ';
  out := out || 'razao_estornos=' || (select count(*) from public.dobroes_ledger l where l.user_id = b and l.reason = 'refund:ios') || ' | ';

  -- casos de borda do estorno
  select * into r from public.apply_dobroes_refund_ios('2001', 'Sandbox');
  out := out || 'estorno_sem_credito=' || r.status || '/' || r.debited || '/' || r.shortfall || ' | ';
  select * into r from public.apply_dobroes_refund_ios('99999', 'Production');
  out := out || 'estorno_desconhecido=' || r.status || ' | ';

  -- DOBIOS-18: só service_role executa
  out := out || 'service_only='
       || has_function_privilege('authenticated', 'public.apply_dobroes_purchase_ios(uuid,text,text,text,boolean,boolean)', 'execute') || '/'
       || has_function_privilege('anon', 'public.apply_dobroes_refund_ios(text,text)', 'execute') || ' | ';

  raise exception 'RESULTADO (rollback): %', out;
end $$;
