-- #117: envio único do push "convite para partida rápida", SEGMENTADO. NÃO RODAR sem o OK do John na hora e
-- sem a 0.45.1 servida na produção. Quem roda: Release, no SQL do painel do Supabase, depois de aplicar
-- supabase/push_segment.sql (migração `push_segment`) e fazer o deploy da Edge `push`.
-- Assinatura: public.push_broadcast(p_title, p_body, p_kind, p_min_version, p_max_version_excl, p_platform).
-- Quem recebe: Android com versão CONHECIDA >= 0.45.0 (device_checks). Versão desconhecida (sem conta ou conta sem
-- registro) fica de fora: o convite promete o que só a 0.45+ tem. iPhone fora (a pública é 0.43.6). Conta robô/Test Lab
-- nunca recebe. Texto sem "bot" nem "jogadores humanos". É o par do push de atualização (tools/push-atualize-0451.sql):
-- os dois nunca pegam o mesmo token.
--
-- Prévia (só conta, não envia): descomente e rode sozinha antes do envio.
-- select count(*) from public.push_segment_tokens('0.45.0', null, 'android');
--
-- Travas (o bloco aborta com aviso, sem enviar, se alguma falhar):
--   * janela 11h-15h de Brasília;
--   * 1 push por dia: se hoje já saiu daily_invite, quick_match_invite ou new_version, aborta. Se o daily_invite
--     de hoje ainda não saiu, este envio o substitui e a tarefa agendada push-diario-naval-battle NÃO pode rodar
--     hoje (desligar antes);
--   * intervalo mínimo de 7 dias desde o último quick_match_invite.
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
             where kind = 'quick_match_invite' and created_at > now() - interval '7 days') then
    raise notice 'Ja houve quick_match_invite nos ultimos 7 dias. Nada enviado.';
    return;
  end if;
  perform public.push_broadcast('Tem adversário esperando você', 'Entre e jogue uma partida rápida agora.',
                                'quick_match_invite', '0.45.0', null, 'android');
  raise notice 'Enviado.';
end $$;

-- Conferência depois (QA): uma linha com sent_at preenchido e result com um código por token (200 = entregue ao FCM).
-- select id, kind, title, min_version, platform, created_at, sent_at, result from public.push_outbox
--  where kind = 'quick_match_invite' order by id desc limit 3;
