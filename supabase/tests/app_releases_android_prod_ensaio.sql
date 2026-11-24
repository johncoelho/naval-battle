-- Ensaio que desfaz tudo (#178): coluna app_releases.android_prod (supabase/releases.sql).
-- Rodar no SQL Editor do projeto cwtslesnthbenxswdcbv. Antes de aplicar a migração, cole logo acima
-- deste bloco, na MESMA execução, o `alter table ... add column if not exists android_prod` e o
-- `update ... where version_code = 128` de releases.sql: o raise no fim cancela a transação inteira.
-- Depois de aplicada, basta rodar só este bloco.
--
-- Resultado esperado:
--   coluna=boolean not_null=NO default=false
--   v128=true outras_true=0
--   insert_sem_coluna=false
--   gatilho_ignora_android_prod=true   (push_on_release é "after insert or update of notify, android_live")
--   update_android_prod_ok=true

do $$
declare
  out text := '';
  r record;
  n int;
  v_def text;
  v_ok boolean;
begin
  select data_type, is_nullable, column_default into r
    from information_schema.columns
   where table_schema = 'public' and table_name = 'app_releases' and column_name = 'android_prod';
  out := out || format('coluna=%s not_null=%s default=%s | ', r.data_type,
                       case r.is_nullable when 'NO' then 'NO' else 'SIM' end, r.column_default);

  out := out || format('v128=%s ', (select android_prod from public.app_releases where version_code = 128));
  select count(*) into n from public.app_releases where android_prod and version_code <> 128;
  out := out || format('outras_true=%s | ', n);

  insert into public.app_releases (version_name, version_code, notes_pt)
  values ('0.0.0-ensaio', 999999, 'ensaio');
  out := out || format('insert_sem_coluna=%s | ',
                       (select android_prod from public.app_releases where version_code = 999999));

  select pg_get_triggerdef(t.oid) into v_def from pg_trigger t
   where t.tgrelid = 'public.app_releases'::regclass and t.tgname = 'push_on_release';
  out := out || format('gatilho_ignora_android_prod=%s | ',
                       v_def is not null and v_def not like '%android_prod%');

  update public.app_releases set android_prod = true where version_code = 999999;
  get diagnostics n = row_count;
  v_ok := n = 1 and (select android_prod from public.app_releases where version_code = 999999);
  out := out || format('update_android_prod_ok=%s', v_ok);

  raise exception 'RESULTADO (rollback): %', out;
end $$;
