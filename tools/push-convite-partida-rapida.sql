-- #117: envio único do push "convite para partida rápida". NÃO RODAR sem o OK do John na hora
-- e sem a 0.45.1 servida na produção. Quem roda: Release, no SQL do painel do Supabase.
-- Assinatura real: public.push_broadcast(p_title text, p_body text, p_kind text default 'announcement').
-- Vai para todos os tokens (user_id nulo = broadcast), só em português.
--
-- Travas (o bloco aborta com aviso, sem enviar, se alguma falhar):
--   * janela 11h-15h de Brasília;
--   * não duplica o daily_invite do dia: se ele já saiu hoje, aborta; se não saiu, este envio
--     o substitui e a tarefa agendada push-diario-naval-battle NÃO pode rodar hoje (desligar antes);
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
             where kind = 'daily_invite' and (created_at at time zone 'America/Sao_Paulo')::date = v_dia) then
    raise notice 'O daily_invite de hoje ja saiu. Nada enviado.';
    return;
  end if;
  if exists (select 1 from public.push_outbox
             where kind = 'quick_match_invite' and created_at > now() - interval '7 days') then
    raise notice 'Ja houve quick_match_invite nos ultimos 7 dias. Nada enviado.';
    return;
  end if;
  perform public.push_broadcast('Tem adversário esperando você', 'Entre e jogue uma partida rápida agora.', 'quick_match_invite');
  raise notice 'Enviado.';
end $$;

-- Conferência depois (QA): uma linha com sent_at preenchido.
-- select id, kind, title, created_at, sent_at, result from public.push_outbox
--  where kind = 'quick_match_invite' order by id desc limit 3;
