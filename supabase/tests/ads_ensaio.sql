-- Ensaio que desfaz tudo (#29, 0.34.0): anúncios premiados (vale, SSV, limites).
-- Rodar no SQL Editor do projeto cwtslesnthbenxswdcbv depois de supabase/ads.sql. O raise no fim
-- cancela a transação: nada fica gravado. ads_enabled é ligado só dentro do bloco; o rollback
-- devolve o valor real ('test').
-- As vitórias dos casos ADS-02/03/04/12/13 usam salas ONLINE de ensaio (`online:<sala>`, tiro dos dois lados
-- e crédito da sala no razão): com carteira (#34) a vitória contra a IA (`ai:`) devolve `not_eligible`, a IA não paga (#74).
--
-- Resultado esperado (cada item vira um trecho de "RESULTADO"):
--   desligado=off                  ads_enabled 'false': nenhum vale                     (ADS-01)
--   status_off=f                   ads_status com o interruptor desligado               (ADS-01)
--   test_ads_qa=t                  conta QA usa a unidade de teste                      (ADS-15)
--   win_130=ok/130                 vitória online de 130 dobrões                               (ADS-02)
--   aplica_1=applied/130                                                                (ADS-02)
--   repetido=already/0             mesmo transaction_id de novo (Google repete)         (ADS-13)
--   pendente_ad=1                  dobroes_pending mostra o crédito uma vez (token ad:) (ADS-13)
--   vale_usado=used/0              mesmo vale com outro transaction_id                  (ADS-12)
--   mesma_vitoria=already          a mesma partida não dobra duas vezes                 (ADS-02)
--   win_205=ok/150                 teto ads_win_double_cap                              (ADS-03)
--   win_3=limit                    terceira vitória do dia                              (ADS-04)
--   outra_conta=user_mismatch/0    vale da QA02 com user_id da QA01                     (ADS-12)
--   vencido=expired/0              vale de mais de 15 min                               (ADS-12)
--   desconhecido=unknown_vale/0    vale inventado                                       (ADS-12)
--   checkin_sem_fazer=not_eligible check-in de hoje ainda não feito                     (ADS-06)
--   checkin=ok/25                  só os 25 do check-in; o bônus de 300 do 7º dia não dobra (ADS-06)
--   checkin_2=limit                1 por dia                                            (ADS-06)
--   desafio=ok/50 aplica=applied/50                                                     (ADS-07)
--   milha_com_saldo=not_eligible   só com saldo zero                                    (ADS-08)
--   milha_1=applied saldo=1                                                             (ADS-08)
--   milha_3=limit mile_limit=t     terceira milha do dia: aviso de limite               (ADS-09)
--   total_hoje=6 dobroes_hoje=355  teto geral                                           (ADS-14)
--   ios=f                          iPhone não recebe anúncio                             (ADS-18)
--   online_sem_sala=not_eligible   sala que não existe ou sem tiro                      (ADS-05)
--   fechou_nao_gasta=0             vale pedido e não aplicado não conta no limite       (ADS-10)
--   gate_vazio=t                   ads_min_app_version vazia: sem exigência de versão   (ADS-19, #49)
--   gate_0346=off/f                min 0.34.7 e último aparelho 0.34.6: sem anúncio     (ADS-19)
--   gate_0347=t                    min 0.34.7 e aparelho 0.34.7: libera                 (ADS-19)
--   gate_sem_registro=f            min preenchida e conta sem device_checks: fora       (ADS-19)
--   gate_ios=f                     registro só de iPhone não vale para o gate           (ADS-19)
--   --- #40 (0.43.1): robos fora e gate na virada para 'true' (ADS-23, ADS-25) ---
--   teste_qa=t/t                   'test': QA com is_test continua com anuncio de teste
--   teste_robo=f                   'test': conta is_test fora do QA segue sem anuncio
--   teste_comum=f                  'test': conta comum segue sem anuncio (inerte)
--   qa_is_test_on=t/t              'true': QA com is_test -> on e test_ads
--   robo_off=f/off                 'true': conta is_test fora do QA -> off
--   comum_true=t/f                 'true': conta comum -> on e test_ads = false
--   gate_0430=f/off                'true' com gate 0.43.1 (versao da #40) e ultimo aparelho 0.43.0 -> off
--   gate_0431=t                    ultimo aparelho 0.43.1 -> on
--   --- #95 (0.44.2): o que o aparelho viu no anuncio (ad_reward_report) ---
--   report_dono=ok/earned          dono grava o desfecho de um vale pendente
--   report_repetido=ignored/earned segunda gravacao nao sobrescreve
--   report_invalido=invalid        desfecho fora de earned|closed_early|unavailable
--   report_alheio=ignored/(nulo)   outra conta nao grava no vale de quem nao e dela
--   report_aplicado=ignored/(nulo) vale ja aplicado nao e tocado
--   report_status=pending/pending  o relatorio nao muda o status do vale nem o valor
--   report_anon=f report_auth=t    anon sem execute; authenticated com execute

do $$
declare
  a uuid; b uuid; r record; s record; n int; vb uuid; out text := '';
  m1 uuid; m2 uuid; m3 uuid; m9 uuid;
begin
  select id into a from auth.users where email = 'aigamesfactory.qa01@gmail.com';
  select id into b from auth.users where email = 'aigamesfactory.qa02@gmail.com';

  -- cenário limpo para as contas QA (tudo volta no rollback)
  delete from public.ad_vales where user_id in (a, b);
  delete from public.dobroes_purchases where user_id in (a, b);
  delete from public.daily_claims where user_id in (a, b) and day = public.brt_today();
  insert into public.app_config (key, value) values ('ads_enabled', 'false')
    on conflict (key) do update set value = excluded.value;
  update public.profiles set nautical_miles = 10, miles_day = public.brt_today() where id in (a, b);

  -- salas online de ensaio entre a e b, com tiro dos dois lados e o crédito da sala no razão (valor que o anúncio dobra)
  insert into public.online_matches (host_id, guest_id, host_name, guest_name, status) values (a, b, 'qa01', 'qa02', 'finished') returning id into m1;
  insert into public.online_matches (host_id, guest_id, host_name, guest_name, status) values (a, b, 'qa01', 'qa02', 'finished') returning id into m2;
  insert into public.online_matches (host_id, guest_id, host_name, guest_name, status) values (a, b, 'qa01', 'qa02', 'finished') returning id into m3;
  insert into public.online_matches (host_id, guest_id, host_name, guest_name, status) values (b, a, 'qa02', 'qa01', 'finished') returning id into m9;
  insert into public.online_messages (match_id, sender_id, body)
    select m, u, 'ACT|1,1' from unnest(array[m1, m2, m3, m9]) m, unnest(array[a, b]) u;
  insert into public.dobroes_ledger (user_id, delta, reason, ref, balance_after) values
    (a, 130, 'online', m1::text, 0), (a, 205, 'online', m2::text, 0), (a, 100, 'online', m3::text, 0), (b, 100, 'online', m9::text, 0);

  perform set_config('request.jwt.claims', json_build_object('sub', a, 'role', 'authenticated')::text, true);

  -- ADS-01: desligado
  select * into r from public.ad_reward_start('mile');
  out := out || 'desligado=' || r.status || ' | ';
  select * into s from public.ads_status('android');
  out := out || 'status_off=' || s.enabled || ' | ';

  update public.app_config set value = 'true' where key = 'ads_enabled';
  select * into s from public.ads_status('android');
  out := out || 'test_ads_qa=' || s.test_ads || ' | ';

  -- ADS-02 / ADS-13: vitória de 130, callback repetido, vale reaproveitado, mesma partida
  select * into r from public.ad_reward_start('win', 'online:' || m1::text, 130);
  out := out || 'win_130=' || r.status || '/' || r.amount || ' | ';
  select * into s from public.apply_ad_reward(r.vale, 'ensaio-tx-1', a::text);
  out := out || 'aplica_1=' || s.status || '/' || s.amount || ' | ';
  select * into s from public.apply_ad_reward(r.vale, 'ensaio-tx-1', a::text);
  out := out || 'repetido=' || s.status || '/' || s.amount || ' | ';
  select count(*) into n from public.dobroes_pending() p where p.purchase_token like 'ad:%';
  out := out || 'pendente_ad=' || n || ' | ';
  select * into s from public.apply_ad_reward(r.vale, 'ensaio-tx-1b', a::text);
  out := out || 'vale_usado=' || s.status || '/' || s.amount || ' | ';
  select * into r from public.ad_reward_start('win', 'online:' || m1::text, 130);
  out := out || 'mesma_vitoria=' || r.status || ' | ';

  -- ADS-03: teto de 150
  select * into r from public.ad_reward_start('win', 'online:' || m2::text, 205);
  out := out || 'win_205=' || r.status || '/' || r.amount || ' | ';
  perform public.apply_ad_reward(r.vale, 'ensaio-tx-2', a::text);

  -- ADS-04: terceira vitória
  select * into r from public.ad_reward_start('win', 'online:' || m3::text, 100);
  out := out || 'win_3=' || r.status || ' | ';

  -- ADS-12: vale de outra conta, vencido, inventado
  perform set_config('request.jwt.claims', json_build_object('sub', b, 'role', 'authenticated')::text, true);
  select * into r from public.ad_reward_start('win', 'online:' || m9::text, 100);
  vb := r.vale;
  select * into s from public.apply_ad_reward(vb, 'ensaio-tx-b1', a::text);
  out := out || 'outra_conta=' || s.status || '/' || s.amount || ' | ';
  update public.ad_vales set expires_at = now() - interval '1 minute' where id = vb;
  select * into s from public.apply_ad_reward(vb, 'ensaio-tx-b2', b::text);
  out := out || 'vencido=' || s.status || '/' || s.amount || ' | ';
  select * into s from public.apply_ad_reward(gen_random_uuid(), 'ensaio-tx-x', b::text);
  out := out || 'desconhecido=' || s.status || '/' || s.amount || ' | ';
  -- ADS-10: o vale da QA02 nunca foi aplicado: não conta no limite dela
  out := out || 'fechou_nao_gasta=' || public.ads_used_today(b) || ' | ';

  perform set_config('request.jwt.claims', json_build_object('sub', a, 'role', 'authenticated')::text, true);

  -- ADS-06: check-in (o bônus de 300 do 7º dia fica de fora)
  select * into r from public.ad_reward_start('checkin');
  out := out || 'checkin_sem_fazer=' || r.status || ' | ';
  insert into public.daily_claims (user_id, day, kind, doubloons) values
    (a, public.brt_today(), 'checkin', 25), (a, public.brt_today(), 'week', 300);
  select * into r from public.ad_reward_start('checkin');
  out := out || 'checkin=' || r.status || '/' || r.amount || ' | ';
  perform public.apply_ad_reward(r.vale, 'ensaio-tx-3', a::text);
  select * into r from public.ad_reward_start('checkin');
  out := out || 'checkin_2=' || r.status || ' | ';

  -- ADS-07: desafio
  insert into public.daily_claims (user_id, day, kind, doubloons) values (a, public.brt_today(), 'challenge', 50);
  select * into r from public.ad_reward_start('challenge');
  out := out || 'desafio=' || r.status || '/' || r.amount || ' ';
  select * into s from public.apply_ad_reward(r.vale, 'ensaio-tx-4', a::text);
  out := out || 'aplica=' || s.status || '/' || s.amount || ' | ';

  -- ADS-08 / ADS-09: milha só com saldo zero, 2 por dia
  select * into r from public.ad_reward_start('mile');
  out := out || 'milha_com_saldo=' || r.status || ' | ';
  update public.profiles set nautical_miles = 0 where id = a;
  select * into r from public.ad_reward_start('mile');
  select * into s from public.apply_ad_reward(r.vale, 'ensaio-tx-5', a::text);
  select nautical_miles into n from public.profiles where id = a;
  out := out || 'milha_1=' || s.status || ' saldo=' || n || ' | ';
  update public.profiles set nautical_miles = 0 where id = a;
  select * into r from public.ad_reward_start('mile');
  perform public.apply_ad_reward(r.vale, 'ensaio-tx-6', a::text);
  update public.profiles set nautical_miles = 0 where id = a;
  select * into r from public.ad_reward_start('mile');
  select * into s from public.ads_status('android');
  out := out || 'milha_3=' || r.status || ' mile_limit=' || s.mile_limit || ' | ';

  -- ADS-14: teto geral
  select sum(v.amount) into n from public.ad_vales v where v.user_id = a and v.status = 'applied' and v.place <> 'mile';
  out := out || 'total_hoje=' || public.ads_used_today(a) || ' dobroes_hoje=' || n || ' | ';

  -- ADS-18 e ADS-05
  select * into s from public.ads_status('ios');
  out := out || 'ios=' || s.enabled || ' | ';
  perform set_config('request.jwt.claims', json_build_object('sub', b, 'role', 'authenticated')::text, true);
  select * into r from public.ad_reward_start('win', 'online:' || gen_random_uuid()::text, 100);
  out := out || 'online_sem_sala=' || r.status || ' | ';

  -- ADS-19 (#49): gate de versão. Cenário de aparelhos próprio da QA01 (volta no rollback)
  perform set_config('request.jwt.claims', json_build_object('sub', a, 'role', 'authenticated')::text, true);
  delete from public.device_checks where user_id = a;
  update public.app_config set value = '' where key = 'ads_min_app_version';  -- o valor real (gate da #40) não pode vazar para o caso
  select * into s from public.ads_status('android');
  out := out || 'gate_vazio=' || s.enabled || ' | ';
  insert into public.app_config (key, value) values ('ads_min_app_version', '0.34.7')
    on conflict (key) do update set value = excluded.value;
  select * into s from public.ads_status('android');
  out := out || 'gate_sem_registro=' || s.enabled || ' | ';
  insert into public.device_checks (user_id, app_version, platform, test_lab, created_at)
    values (a, '0.34.6', 'android', false, now() - interval '2 hours');
  select * into r from public.ad_reward_start('mile');
  select * into s from public.ads_status('android');
  out := out || 'gate_0346=' || r.status || '/' || s.enabled || ' | ';
  insert into public.device_checks (user_id, app_version, platform, test_lab, created_at)
    values (a, '0.34.7', 'android', false, now());
  select * into s from public.ads_status('android');
  out := out || 'gate_0347=' || s.enabled || ' | ';
  delete from public.device_checks where user_id = a;
  insert into public.device_checks (user_id, app_version, platform, test_lab)
    values (a, '0.34.7', 'ios', false);
  select * into s from public.ads_status('android');
  out := out || 'gate_ios=' || s.enabled;

  -- #40: robos da Play sem anuncio. A QA02 vira "outra conta" dentro do bloco (sai do allowed_players
  -- qa e do ranking de teste; tudo volta no rollback).
  delete from public.device_checks where user_id in (a, b);
  update public.app_config set value = '' where key = 'ads_min_app_version';
  delete from public.allowed_players where source = 'qa' and (user_id = b or email = 'aigamesfactory.qa02@gmail.com');
  update public.profiles set is_test = true where id in (a, b);
  update public.app_config set value = 'test' where key = 'ads_enabled';
  perform set_config('request.jwt.claims', json_build_object('sub', a, 'role', 'authenticated')::text, true);
  select * into s from public.ads_status('android');
  out := out || ' | teste_qa=' || s.enabled || '/' || s.test_ads;
  perform set_config('request.jwt.claims', json_build_object('sub', b, 'role', 'authenticated')::text, true);
  select * into s from public.ads_status('android');
  out := out || ' | teste_robo=' || s.enabled;
  update public.profiles set is_test = false where id = b;
  select * into s from public.ads_status('android');
  out := out || ' | teste_comum=' || s.enabled;

  update public.app_config set value = 'true' where key = 'ads_enabled';
  perform set_config('request.jwt.claims', json_build_object('sub', a, 'role', 'authenticated')::text, true);
  select * into s from public.ads_status('android');
  out := out || ' | qa_is_test_on=' || s.enabled || '/' || s.test_ads;
  perform set_config('request.jwt.claims', json_build_object('sub', b, 'role', 'authenticated')::text, true);
  update public.profiles set is_test = true where id = b;
  select * into r from public.ad_reward_start('mile');
  select * into s from public.ads_status('android');
  out := out || ' | robo_off=' || s.enabled || '/' || r.status;
  update public.profiles set is_test = false where id = b;
  select * into s from public.ads_status('android');
  out := out || ' | comum_true=' || s.enabled || '/' || s.test_ads;

  update public.app_config set value = '0.43.1' where key = 'ads_min_app_version';
  insert into public.device_checks (user_id, app_version, platform, test_lab, created_at)
    values (b, '0.43.0', 'android', false, now() - interval '2 hours');
  select * into r from public.ad_reward_start('mile');
  select * into s from public.ads_status('android');
  out := out || ' | gate_0430=' || s.enabled || '/' || r.status;
  insert into public.device_checks (user_id, app_version, platform, test_lab, created_at)
    values (b, '0.43.1', 'android', false, now());
  select * into s from public.ads_status('android');
  out := out || ' | gate_0431=' || s.enabled;

  -- #95: relatorio do desfecho no aparelho (vales inseridos direto; o bloco desfaz tudo)
  declare va uuid; vc uuid; vd uuid; vx text; oc text;
  begin
    insert into public.ad_vales (user_id, place, ref, amount) values (a, 'mile', 'ensaio-rep-1', 1) returning id into va;
    insert into public.ad_vales (user_id, place, ref, amount, status, transaction_id, applied_day, applied_at)
      values (a, 'mile', 'ensaio-rep-2', 1, 'applied', 'ensaio-rep-tx', public.brt_today(), now()) returning id into vc;
    perform set_config('request.jwt.claims', json_build_object('sub', b, 'role', 'authenticated')::text, true);
    vx := public.ad_reward_report(va, 'earned');
    select client_outcome into oc from public.ad_vales where id = va;
    out := out || ' | report_alheio=' || vx || '/' || coalesce(oc, '(nulo)');
    perform set_config('request.jwt.claims', json_build_object('sub', a, 'role', 'authenticated')::text, true);
    vx := public.ad_reward_report(va, 'earned');
    select client_outcome into oc from public.ad_vales where id = va;
    out := out || ' | report_dono=' || vx || '/' || oc;
    vx := public.ad_reward_report(va, 'closed_early');
    select client_outcome into oc from public.ad_vales where id = va;
    out := out || ' | report_repetido=' || vx || '/' || oc;
    insert into public.ad_vales (user_id, place, ref, amount) values (a, 'mile', 'ensaio-rep-3', 1) returning id into vd;
    out := out || ' | report_invalido=' || public.ad_reward_report(vd, 'banana');
    vx := public.ad_reward_report(vc, 'unavailable');
    select client_outcome into oc from public.ad_vales where id = vc;
    out := out || ' | report_aplicado=' || vx || '/' || coalesce(oc, '(nulo)');
    select s0.status into vx from public.ad_reward_status(va) s0;
    out := out || ' | report_status=' || vx || '/' || (select v.status from public.ad_vales v where v.id = va);
    out := out || ' | report_anon=' || has_function_privilege('anon', 'public.ad_reward_report(uuid,text)', 'execute')
              || ' report_auth=' || has_function_privilege('authenticated', 'public.ad_reward_report(uuid,text)', 'execute');
  end;

  raise exception 'RESULTADO (rollback): %', out;
end $$;
