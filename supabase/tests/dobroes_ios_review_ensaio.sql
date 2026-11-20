-- Ensaio que desfaz tudo (#65 Plano B): real_ios_enabled = flag OR (beta_eligible AND beta_store_on).
-- Rodar depois de dobroes_ios_review.sql. O raise no fim cancela a transação (nada fica gravado).
-- Sem e-mail no resultado. Usa a conta QA01 (beta_eligible) e uma conta comum (primeira não elegível).
--
-- Resultado esperado:
--   publico_sem_flag=false | beta_vitrine_on=true | beta_vitrine_off=false | publico_flag_on=true
--   beta_flag_on=true | android_intacto=true

do $$
declare
  qa uuid; comum uuid; st record; out text := '';
  android_antes boolean; flag_antes text; vit_antes text;
begin
  select id into qa from auth.users where email = 'aigamesfactory.qa01@gmail.com';
  select id into comum from auth.users u where not public.beta_eligible(u.id) limit 1;
  if qa is null or comum is null then raise exception 'ENSAIO: faltam contas (qa=% comum=%)', qa, comum; end if;
  select coalesce((select value = 'true' from public.app_config where key = 'real_billing_enabled'), false) into android_antes;

  insert into public.app_config (key, value) values ('real_billing_ios_enabled', 'false'), ('beta_store_enabled', 'true')
    on conflict (key) do update set value = excluded.value;

  -- público, flag desligada, vitrine beta ligada: desligado
  perform set_config('request.jwt.claims', json_build_object('sub', comum, 'role', 'authenticated')::text, true);
  select * into st from public.dobroes_store_status();
  out := out || 'publico_sem_flag=' || st.real_ios_enabled || ' | ';

  -- beta_eligible + vitrine ligada: ligada
  perform set_config('request.jwt.claims', json_build_object('sub', qa, 'role', 'authenticated')::text, true);
  select * into st from public.dobroes_store_status();
  out := out || 'beta_vitrine_on=' || st.real_ios_enabled || ' | ';

  -- beta_eligible + vitrine desligada: desligada
  update public.app_config set value = 'false' where key = 'beta_store_enabled';
  select * into st from public.dobroes_store_status();
  out := out || 'beta_vitrine_off=' || st.real_ios_enabled || ' | ';

  -- flag ligada: todos
  update public.app_config set value = 'true' where key = 'real_billing_ios_enabled';
  perform set_config('request.jwt.claims', json_build_object('sub', comum, 'role', 'authenticated')::text, true);
  select * into st from public.dobroes_store_status();
  out := out || 'publico_flag_on=' || st.real_ios_enabled || ' | ';
  perform set_config('request.jwt.claims', json_build_object('sub', qa, 'role', 'authenticated')::text, true);
  select * into st from public.dobroes_store_status();
  out := out || 'beta_flag_on=' || st.real_ios_enabled || ' | ';

  out := out || 'android_intacto=' || (st.real_enabled = android_antes);
  raise exception 'ENSAIO: %', out;   -- desfaz a transação inteira
end $$;
