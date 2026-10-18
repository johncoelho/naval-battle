-- Ensaio que desfaz tudo (#26, 0.33.2): register_push_token e push_check_daily.
-- Rodar no SQL Editor do projeto cwtslesnthbenxswdcbv depois de supabase/push_check_daily.sql.
-- O raise no fim cancela a transação: nada fica gravado, e o pg_net não envia nada (só depois do commit).
--
-- Resultado esperado (cada item vira uma linha de "RESULTADO"):
--   dead_apos_registro=NULL   token 'unregistered' que volta pelo registro perde a marca
--   ok_apos_registro=ok       token 'ok' não é mexido pelo registro
--   fase2_pendente=NULL       fase 2 com aparelho sem resposta e dentro das 2 h: espera (null)
--   fase2_resumo=Conferência ...  depois de todos conferidos: devolve o resumo, sem e-mail
--   repetido=NULL             mesmo dia, já reportado: null
--   fase1=Conferência de instalação disparada...  dia novo, fase 1 (entre 8 h e 23 h de Brasília)
--                             (fora dessa janela a fase 1 devolve null, o esperado nesse horário)

do $$
declare
  q1 uuid;
  tk_dead text := 'ensaio-chk-dead-' || replace(gen_random_uuid()::text, '-', '');
  tk_ok text := 'ensaio-chk-ok-' || replace(gen_random_uuid()::text, '-', '');
  st text; r text; out text := '';
begin
  select id into q1 from auth.users where email = 'aigamesfactory.qa01@gmail.com';
  perform set_config('request.jwt.claims', json_build_object('sub', q1, 'role', 'authenticated')::text, true);

  -- 1. token marcado unregistered volta pelo registro: zera a marca
  insert into public.push_tokens (token, user_id, platform, updated_at, check_status, checked_at)
  values (tk_dead, null, 'android', now(), 'unregistered', now());
  perform public.register_push_token(tk_dead, 'android');
  select check_status into st from public.push_tokens where token = tk_dead;
  out := out || 'dead_apos_registro=' || coalesce(st, 'NULL') || ' | ';

  -- 2. token ok continua ok
  insert into public.push_tokens (token, user_id, platform, updated_at, check_status, checked_at)
  values (tk_ok, null, 'android', now(), 'ok', now());
  perform public.register_push_token(tk_ok, 'android');
  select check_status into st from public.push_tokens where token = tk_ok;
  out := out || 'ok_apos_registro=' || coalesce(st, 'NULL') || ' | ';

  -- 3. fase 2 com pendentes: null (em andamento)
  perform set_config('request.jwt.claims', '', true);
  insert into public.app_config (key, value) values ('push_check_reported_on', '1900-01-01')
    on conflict (key) do update set value = excluded.value;
  insert into public.app_config (key, value) values ('push_check_day', public.brt_today()::text)
    on conflict (key) do update set value = excluded.value;
  insert into public.app_config (key, value) values ('push_check_started_at', now()::text)
    on conflict (key) do update set value = excluded.value;
  r := public.push_check_daily();
  out := out || 'fase2_pendente=' || coalesce(r, 'NULL') || ' | ';

  -- 4. todos conferidos depois do início: resumo
  update public.push_tokens set checked_at = now() where platform = 'android';
  r := public.push_check_daily();
  out := out || 'fase2_resumo=' || coalesce(r, 'NULL') || ' | ';

  -- 5. mesmo dia, já reportado: null
  r := public.push_check_daily();
  out := out || 'repetido=' || coalesce(r, 'NULL') || ' | ';

  -- 6. dia novo, fase 1
  update public.app_config set value = '1900-01-02' where key = 'push_check_reported_on';
  update public.app_config set value = '1900-01-01' where key = 'push_check_day';
  r := public.push_check_daily();
  out := out || 'fase1=' || coalesce(r, 'NULL') || ' | ';

  raise exception 'RESULTADO (rollback): %', out;
end $$;
