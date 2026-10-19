-- Ensaio que desfaz tudo (#29, 0.34.0): anúncios premiados (vale, SSV, limites).
-- Rodar no SQL Editor do projeto cwtslesnthbenxswdcbv depois de supabase/ads.sql. O raise no fim
-- cancela a transação: nada fica gravado. ads_enabled é ligado só dentro do bloco; o rollback
-- devolve o valor real ('false').
--
-- Resultado esperado (cada item vira um trecho de "RESULTADO"):
--   desligado=off                  ads_enabled 'false': nenhum vale                     (ADS-01)
--   status_off=f                   ads_status com o interruptor desligado               (ADS-01)
--   test_ads_qa=t                  conta QA usa a unidade de teste                      (ADS-15)
--   win_130=ok/130                 vitória de 130 dobrões                               (ADS-02)
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

do $$
declare
  a uuid; b uuid; r record; s record; n int; vb uuid; out text := '';
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
  select * into r from public.ad_reward_start('win', 'ai:ensaio0000000001', 130);
  out := out || 'win_130=' || r.status || '/' || r.amount || ' | ';
  select * into s from public.apply_ad_reward(r.vale, 'ensaio-tx-1', a::text);
  out := out || 'aplica_1=' || s.status || '/' || s.amount || ' | ';
  select * into s from public.apply_ad_reward(r.vale, 'ensaio-tx-1', a::text);
  out := out || 'repetido=' || s.status || '/' || s.amount || ' | ';
  select count(*) into n from public.dobroes_pending() p where p.purchase_token like 'ad:%';
  out := out || 'pendente_ad=' || n || ' | ';
  select * into s from public.apply_ad_reward(r.vale, 'ensaio-tx-1b', a::text);
  out := out || 'vale_usado=' || s.status || '/' || s.amount || ' | ';
  select * into r from public.ad_reward_start('win', 'ai:ensaio0000000001', 130);
  out := out || 'mesma_vitoria=' || r.status || ' | ';

  -- ADS-03: teto de 150
  select * into r from public.ad_reward_start('win', 'ai:ensaio0000000002', 205);
  out := out || 'win_205=' || r.status || '/' || r.amount || ' | ';
  perform public.apply_ad_reward(r.vale, 'ensaio-tx-2', a::text);

  -- ADS-04: terceira vitória
  select * into r from public.ad_reward_start('win', 'ai:ensaio0000000003', 100);
  out := out || 'win_3=' || r.status || ' | ';

  -- ADS-12: vale de outra conta, vencido, inventado
  perform set_config('request.jwt.claims', json_build_object('sub', b, 'role', 'authenticated')::text, true);
  select * into r from public.ad_reward_start('win', 'ai:ensaio0000000009', 100);
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
  out := out || 'online_sem_sala=' || r.status;

  raise exception 'RESULTADO (rollback): %', out;
end $$;
