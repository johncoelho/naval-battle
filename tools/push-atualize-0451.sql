-- #117: envio único do push "Atualize o Naval Battle" (kind new_version), SEGMENTADO. NÃO RODAR sem o OK do John na
-- hora e sem a 0.45.1 realmente servida na Play para esses aparelhos. Quem roda: Release, no SQL do painel do Supabase,
-- depois de aplicar supabase/push_segment.sql (migração `push_segment`) e fazer o deploy da Edge `push`.
-- Quem recebe: SÓ Android com versão MENOR que 0.45.0 ou DESCONHECIDA (anônimo ou conta sem registro em device_checks).
-- Quem tem 0.45.0+ não recebe (esse recebe o convite, tools/push-convite-partida-rapida.sql: nunca os dois no mesmo
-- token). iPhone fora (new_version nunca vai ao iPhone e a App Store ainda não tem a versão). Conta robô/Test Lab nunca
-- recebe. Texto sem "bot" nem "jogadores humanos". Só português. A política de privacidade já cobre "nova versão".
-- NÃO soma ao daily_invite: se hoje já saiu qualquer um dos pushes, aborta.
--
-- Prévia (só conta, não envia): descomente e rode sozinha antes do envio.
-- select count(*) from public.push_segment_tokens(null, '0.45.0', 'android');
--
-- Travas (o bloco aborta com aviso, sem enviar, se alguma falhar):
--   * janela 11h-15h de Brasília;
--   * 1 push por dia: se hoje já saiu daily_invite, quick_match_invite ou new_version, aborta. Se o daily_invite
--     de hoje ainda não saiu, este envio o substitui e a tarefa agendada push-diario-naval-battle NÃO pode rodar
--     hoje (desligar antes);
--   * não repete a campanha: aborta se este mesmo título já saiu nos últimos 7 dias.
do $$
declare
  v_hora int := extract(hour from (now() at time zone 'America/Sao_Paulo'));
  v_dia  date := (now() at time zone 'America/Sao_Paulo')::date;
begin
  if v_hora < 11 or v_hora >= 15 then
    raise notice 'Fora da janela 11h-15h de Brasilia (hora atual: %). Nada enviado.', v_hora;
    return;
  end if;
  if exists (select 1 from public.push_outbox
             where kind in ('daily_invite', 'quick_match_invite', 'new_version')
               and (created_at at time zone 'America/Sao_Paulo')::date = v_dia) then
    raise notice 'Ja saiu um push de convite ou de versao hoje. Nada enviado.';
    return;
  end if;
  if exists (select 1 from public.push_outbox
             where kind = 'new_version' and title = 'Atualize o Naval Battle'
               and created_at > now() - interval '7 days') then
    raise notice 'O aviso de atualizacao ja saiu nos ultimos 7 dias. Nada enviado.';
    return;
  end if;
  perform public.push_broadcast('Atualize o Naval Battle', 'Mais adversários esperando você na partida rápida.',
                                'new_version', null, '0.45.0', 'android');
  raise notice 'Enviado.';
end $$;

-- Conferência depois (QA): uma linha com sent_at preenchido e result com um código por token (200 = entregue ao FCM).
-- select id, kind, title, max_version_excl, platform, created_at, sent_at, result from public.push_outbox
--  where kind = 'new_version' order by id desc limit 3;
