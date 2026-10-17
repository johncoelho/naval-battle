-- Conferência de instalação por push de validação (#26).
-- Rode no SQL Editor depois de push.sql. Idempotente.
--
-- Para cada aparelho Android registrado, a Edge Function `push-check` pede ao FCM uma
-- validação (validate_only = true): o Google confere o token sem entregar nada a aparelho
-- nenhum. 404/UNREGISTERED = app desinstalado; 200 = app instalado. Só grava o resultado
-- (checked_at, check_status) e NÃO apaga o token: quem tira o token dos envios é a função
-- `push`, que já faz isso com 404.
--
-- Relatório (só para o John, por SQL): select * from public.testers_install_report();
-- Só conta a lista da Play (allowed_players.source = 'play'); manual, qa e iPhone ficam fora.

-- ------------------------------------------------------------------ colunas novas

alter table public.push_tokens
  add column if not exists checked_at timestamptz,
  add column if not exists check_status text
    check (check_status in ('ok', 'unregistered', 'erro'));

-- ------------------------------------------------------------------ chave da conferência

-- só o banco e a Edge Function leem (RLS ligado, sem política). A função exige o cabeçalho
-- x-push-check com este valor; sem ele, 401 e nada é feito.
insert into public.app_config (key, value)
values ('push_check_key', replace(gen_random_uuid()::text, '-', '') || replace(gen_random_uuid()::text, '-', ''))
on conflict (key) do nothing;

-- ------------------------------------------------------------------ disparo

-- Dispara a conferência (chamada pela tarefa agendada via SQL, como o push diário; sem pg_cron).
-- A Edge tem verify_jwt ligado: o gateway exige um JWT válido, então a chamada leva a chave
-- anônima pública (a mesma do app) no Authorization; a proteção de verdade é o x-push-check.
create or replace function public.push_check_run()
returns integer language plpgsql security definer set search_path = public, extensions as $$
declare
  v_key text;
begin
  select value into v_key from public.app_config where key = 'push_check_key';
  if v_key is null then raise exception 'push_check_key ausente'; end if;
  perform net.http_post(
    url     := 'https://cwtslesnthbenxswdcbv.supabase.co/functions/v1/push-check',
    body    := '{}'::jsonb,
    headers := jsonb_build_object(
      'Content-Type', 'application/json',
      'Authorization', 'Bearer eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJpc3MiOiJzdXBhYmFzZSIsInJlZiI6ImN3dHNsZXNudGhiZW54c3dkY2J2Iiwicm9sZSI6ImFub24iLCJpYXQiOjE3ODkyNjc4MDksImV4cCI6MjEwNDg0MzgwOX0.nNN323ZRPocbcRVc4DPHNoWtgP2nVBjjojWIMNSZI_g',
      'x-push-check', v_key
    ),
    timeout_milliseconds := 60000
  );
  return (select count(*)::integer from public.push_tokens where platform = 'android');
end $$;
revoke all on function public.push_check_run() from public, anon, authenticated;

-- ------------------------------------------------------------------ relatório

-- uma linha por testador da lista da Play (sem iPhone, manual nem qa)
create or replace function public.testers_install_report()
returns table (email text, username text, status text, last_app_open timestamptz, checked_at timestamptz)
language sql stable security definer set search_path = public as $$
  with testers as (
    select a.email, u.id as user_id
      from public.allowed_players a
      left join auth.users u on lower(trim(u.email)) = a.email
     where a.source = 'play'
  ), last_token as (
    select distinct on (t.user_id) t.user_id, t.check_status, t.checked_at, t.updated_at
      from public.push_tokens t
     where t.user_id is not null
     order by t.user_id, t.updated_at desc
  )
  select te.email,
         p.username,
         case when te.user_id is null then 'sem conta no jogo'
              when lt.user_id is null then 'sem aparelho registrado'
              when lt.check_status = 'ok' then 'instalado'
              when lt.check_status = 'unregistered' then 'desinstalado'
              when lt.check_status = 'erro' then 'erro na conferencia'
              else 'ainda nao conferido' end,
         lt.updated_at,
         lt.checked_at
    from testers te
    left join last_token lt on lt.user_id = te.user_id
    left join public.profiles p on p.id = te.user_id
   order by 3, 1;
$$;
revoke all on function public.testers_install_report() from public, anon, authenticated;
