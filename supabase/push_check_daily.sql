-- Conferência diária de instalação (#26, 2ª rodada): passo 0 da rodada da esteira.
-- Rode depois de push.sql, push_broadcast.sql e push_check.sql. Idempotente.
--
-- 1. push_check_daily(): duas fases, porque o pg_net só envia a requisição depois do commit
--    (a conferência não cabe na mesma transação que a dispara). Fase 1 (primeira rodada do dia,
--    a partir das 8 h de Brasília): chama push_check_run() e guarda o início. Fase 2 (rodada
--    seguinte, ~30 min depois): devolve o resumo quando todos os aparelhos foram conferidos, ou
--    depois de 2 h (aí o resumo diz que a conferência ficou incompleta). Devolve null quando não
--    há nada a dizer. Estado em app_config (chaves push_check_day, push_check_started_at,
--    push_check_reported_on).
-- 2. register_push_token(): redefinida (mesma assinatura, cópia de push_broadcast.sql mais uma
--    cláusula). A função `push` agora MARCA o token como 'unregistered' em vez de apagar. Quando o
--    aparelho volta com um token que estava 'unregistered', zera check_status e checked_at, para o
--    relatório não ficar preso em 'desinstalado'. Token 'ok' ou 'erro' não é mexido.
--
-- O resumo tem contagens e username, nunca e-mail. O e-mail só aparece em testers_install_report().

-- ------------------------------------------------------------------ registro do aparelho

create or replace function public.register_push_token(p_token text, p_platform text default 'android')
returns table (ok boolean)
language plpgsql security definer set search_path = public as $$
begin
  if coalesce(length(p_token), 0) < 20 then
    return query select false;
    return;
  end if;
  insert into public.push_tokens as pt (token, user_id, platform, updated_at)
  values (p_token, auth.uid(), coalesce(p_platform, 'android'), now())
  on conflict (token) do update
    set user_id = excluded.user_id,
        platform = excluded.platform,
        updated_at = now(),
        check_status = case when pt.check_status = 'unregistered' then null else pt.check_status end,
        checked_at = case when pt.check_status = 'unregistered' then null else pt.checked_at end;
  return query select true;
end $$;
grant execute on function public.register_push_token(text, text) to anon, authenticated;

-- ------------------------------------------------------------------ conferência diária

create or replace function public.push_check_daily()
returns text
language plpgsql security definer set search_path = public, extensions as $$
declare
  v_today date := public.brt_today();
  v_hour integer := extract(hour from (now() at time zone 'America/Sao_Paulo'))::integer;
  v_reported text;
  v_day text;
  v_started timestamptz;
  v_pending integer;
  v_ok integer;
  v_gone integer;
  v_noapp integer;
  v_noacc integer;
  v_err integer;
  v_nochk integer;
  v_total integer;
  v_gone_names text;
  v_msg text;
begin
  select value into v_reported from public.app_config where key = 'push_check_reported_on';
  if v_reported = v_today::text then
    return null;
  end if;

  select value into v_day from public.app_config where key = 'push_check_day';
  if v_day is distinct from v_today::text then
    -- fase 1: a conferência de hoje ainda não saiu
    if v_hour < 8 then
      return null;
    end if;
    perform public.push_check_run();
    insert into public.app_config (key, value) values ('push_check_day', v_today::text)
      on conflict (key) do update set value = excluded.value;
    insert into public.app_config (key, value) values ('push_check_started_at', now()::text)
      on conflict (key) do update set value = excluded.value;
    return 'Conferência de instalação disparada; resumo na próxima rodada.';
  end if;

  -- fase 2: a conferência já saiu; ainda há aparelho sem resposta?
  select value::timestamptz into v_started from public.app_config where key = 'push_check_started_at';
  select count(*)::integer into v_pending
    from public.push_tokens
   where platform = 'android'
     and (check_status is null or check_status <> 'unregistered')
     and updated_at < v_started
     and (checked_at is null or checked_at < v_started);
  if v_pending > 0 and now() < v_started + interval '2 hours' then
    return null;
  end if;

  -- resumo: só contagens da lista da Play e nomes de usuário (nunca e-mail)
  select count(*) filter (where status = 'instalado')::integer,
         count(*) filter (where status = 'desinstalado')::integer,
         count(*) filter (where status = 'sem aparelho registrado')::integer,
         count(*) filter (where status = 'sem conta no jogo')::integer,
         count(*) filter (where status = 'erro na conferencia')::integer,
         count(*) filter (where status = 'ainda nao conferido')::integer,
         count(*)::integer,
         coalesce(string_agg(coalesce(username, 'sem nome'), ', ' order by username)
                  filter (where status = 'desinstalado'), '')
    into v_ok, v_gone, v_noapp, v_noacc, v_err, v_nochk, v_total, v_gone_names
    from public.testers_install_report();

  v_msg := 'Conferência ' || to_char(v_today, 'DD/MM') || ': ' || v_total || ' testadores da Play: '
        || v_ok || ' instalados, '
        || v_gone || ' desinstalado' || case when v_gone = 1 then '' else 's' end
        || case when v_gone_names <> '' then ' (' || v_gone_names || ')' else '' end
        || ', ' || v_noapp || ' sem aparelho registrado, '
        || v_noacc || ' sem conta no jogo, '
        || v_err || ' com erro'
        || case when v_nochk > 0 then ', ' || v_nochk || ' sem conferir' else '' end
        || '.';
  if v_pending > 0 then
    v_msg := 'Conferência incompleta: ' || v_pending || ' aparelhos sem resposta. ' || v_msg;
  end if;

  insert into public.app_config (key, value) values ('push_check_reported_on', v_today::text)
    on conflict (key) do update set value = excluded.value;
  return v_msg;
end $$;
revoke all on function public.push_check_daily() from public, anon, authenticated;

-- Ensaio que desfaz tudo (raise exception no fim): ver docs/QA_TEST_PLAN.md, casos CHK-05 e CHK-06.
