-- Ensaio que desfaz tudo (#65 fatia 2): dobroes_store_status() devolve real_ios_enabled conforme a flag.
-- Rodar no SQL Editor depois de dobroes_ios_status.sql. O raise no fim cancela a transação.
--
-- Resultado esperado: ios_desligada=false | ios_ligada=true | android_intacto=true

do $$
declare
  st record;
  out text := '';
  antes boolean;
begin
  select coalesce((select value = 'true' from public.app_config where key = 'real_billing_enabled'), false) into antes;

  update public.app_config set value = 'false' where key = 'real_billing_ios_enabled';
  select * into st from public.dobroes_store_status();
  out := out || 'ios_desligada=' || st.real_ios_enabled || ' | ';

  update public.app_config set value = 'true' where key = 'real_billing_ios_enabled';
  select * into st from public.dobroes_store_status();
  out := out || 'ios_ligada=' || st.real_ios_enabled || ' | ';
  out := out || 'android_intacto=' || (st.real_enabled = antes);

  raise exception 'ENSAIO: %', out;   -- desfaz a transação inteira
end $$;
