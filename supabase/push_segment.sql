-- Push segmentado por versão e plataforma (#117). Rode depois de push.sql, push_broadcast.sql, push_check_daily.sql,
-- test_accounts.sql (device_checks, is_robot_account) e dobroes.sql (app_version_at_least). Idempotente.
-- Migração no banco: `push_segment` (mesmo conteúdo). Ensaio que desfaz tudo: supabase/tests/push_segment_ensaio.sql.
--
-- O problema: push_tokens não guarda a versão do app. O convite da partida rápida só vale para quem tem a 0.45.0+
-- (adversário virtual) e o aviso "atualize" só para quem ainda não tem. A versão vem de device_checks
-- (user_id, app_version, platform), gravada 1x por conta e versão na abertura logada.
--
-- Colunas novas em push_outbox (todas nulas = sem filtro, como todo aviso de antes):
--   min_version        só quem tem esta versão ou mais nova; versão DESCONHECIDA fica de fora
--   max_version_excl   só quem tem versão menor que esta; versão DESCONHECIDA ENTRA
--   platform           'android' | 'ios'
-- Desconhecida = token sem conta (anônimo) ou conta sem linha em device_checks para a plataforma do token.
-- Versão do token = a maior versão da conta em device_checks na MESMA plataforma do token (conta com Android novo e
-- iPhone velho não mistura). Convite (min 0.45.0) e atualização (max_excl 0.45.0) nunca pegam o mesmo token.
--
-- A seleção mora aqui (push_segment_tokens), não na Edge, para o ensaio e a contagem usarem a mesma regra.
-- Segmentado nunca vai para conta de robô/QA (is_robot_account). Sem filtro (os três nulos), a Edge segue o caminho
-- antigo (todos os tokens), então nada muda para os avisos que já existem.

alter table public.push_outbox add column if not exists min_version text;
alter table public.push_outbox add column if not exists max_version_excl text;
alter table public.push_outbox add column if not exists platform text;

-- tokens que o filtro alcança (a Edge lê o resultado com service_role)
create or replace function public.push_segment_tokens(p_min text, p_max_excl text, p_platform text)
returns table (token text, platform text)
language sql stable security definer set search_path = public as $$
  select t.token, t.platform
    from public.push_tokens t
    left join lateral (
      select (array_agg(d.app_version order by string_to_array(d.app_version, '.')::int[] desc))[1] as v
        from public.device_checks d
       where d.user_id = t.user_id
         and d.platform = t.platform
         and d.app_version ~ '^[0-9]+(\.[0-9]+)*$'
    ) ver on true
   where t.platform in ('android', 'ios')
     and (t.check_status is null or t.check_status <> 'unregistered')
     and (p_platform is null or t.platform = p_platform)
     and (p_min is null or (ver.v is not null and public.app_version_at_least(ver.v, p_min)))
     and (p_max_excl is null or ver.v is null or not public.app_version_at_least(ver.v, p_max_excl))
     and (t.user_id is null or not public.is_robot_account(t.user_id))
$$;
revoke all on function public.push_segment_tokens(text, text, text) from public, anon, authenticated;
grant execute on function public.push_segment_tokens(text, text, text) to service_role;

-- enfileira e dispara a Edge `push`; sem token no segmento, não grava nada
create or replace function public.queue_push_segment(
  p_kind text, p_title text, p_body text, p_min text, p_max_excl text, p_platform text)
returns void
language plpgsql security definer set search_path = public, extensions as $$
declare
  v_id bigint;
begin
  if not exists (select 1 from public.push_segment_tokens(p_min, p_max_excl, p_platform)) then return; end if;
  insert into public.push_outbox (user_id, kind, title, body, min_version, max_version_excl, platform)
  values (null, p_kind, p_title, p_body, p_min, p_max_excl, p_platform)
  returning id into v_id;
  perform net.http_post(
    url     := 'https://cwtslesnthbenxswdcbv.supabase.co/functions/v1/push',
    body    := jsonb_build_object('id', v_id),
    headers := jsonb_build_object('Content-Type', 'application/json'),
    timeout_milliseconds := 30000
  );
exception when others then
  null;
end $$;
revoke all on function public.queue_push_segment(text, text, text, text, text, text) from public, anon, authenticated;

-- push_broadcast ganha os filtros (3 argumentos continuam valendo: os novos têm padrão nulo)
drop function if exists public.push_broadcast(text, text, text);
create or replace function public.push_broadcast(
  p_title text, p_body text, p_kind text default 'announcement',
  p_min_version text default null, p_max_version_excl text default null, p_platform text default null)
returns void
language plpgsql security definer set search_path = public as $$
begin
  if p_min_version is null and p_max_version_excl is null and p_platform is null then
    perform public.queue_push(null, p_kind, p_title, p_body);
  else
    perform public.queue_push_segment(p_kind, p_title, p_body, p_min_version, p_max_version_excl, p_platform);
  end if;
end $$;
revoke all on function public.push_broadcast(text, text, text, text, text, text) from public, anon, authenticated;
