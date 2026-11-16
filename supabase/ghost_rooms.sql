-- Naval Battle · sala fantasma na partida rápida (issue #113, 0.44.7)
-- Rode no SQL Editor depois de online.sql, safety.sql e walkover.sql. Idempotente.
-- Teste que desfaz tudo: supabase/tests/ghost_rooms_ensaio.sql (cole este arquivo e o ensaio na MESMA execução).
--
-- O problema: o app (todas as versões até a 0.44.6) procura sala de partida rápida por leitura direta da tabela
-- (`GET online_matches?status=eq.waiting&is_quick_match=is.true&order=created_at.asc&limit=1`, `findQuickMatch`) e
-- entra com `join_online_match`. Nenhum dos dois olhava a idade da sala nem se o anfitrião ainda aparece (só a
-- `find_quick_offer`, o balão, olhava). Como a leitura é a MAIS ANTIGA primeiro, uma sala `waiting` esquecida (anfitrião
-- saiu sem fechar) era SEMPRE a primeira: a conta entrava nela, esperava 60 s e ganhava um WO falso (QA02, 16/11, sala
-- criada em 11/11). Salas `waiting` nunca eram fechadas por ninguém.
--
-- A regra, toda no servidor (vale também para o app antigo, que lê a tabela direto):
-- 1. "Sala viva" de partida rápida = criada nos últimos 10 minutos E (anfitrião sem presença registrada, app antigo, OU
--    presença nos últimos `quick_host_fresh_seconds`, 20). É a mesma regra do balão (`find_quick_offer`).
-- 2. A política de leitura `sala online: ler` só mostra a sala `waiting` de terceiros se ela estiver viva; o dono, o
--    convidado e o convidado por convite continuam vendo a própria sala. Sala morta some da busca do app antigo.
-- 3. `join_online_match` recusa a sala rápida que não está viva (zero linhas, igual a "perdeu a corrida").
-- 4. `sweep_empty_rooms` (chamada pela `find_quick_offer` a cada oferta) passa a fechar (`abandoned`) a sala rápida `waiting`
--    com mais de 10 minutos e a sala `waiting` de código ou convite com mais de 2 dias. Sem vencedor, sem pontos.

-- sala rápida viva: criada há menos de 10 minutos e anfitrião sem presença (app antigo) ou presente há pouco
create or replace function public.quick_room_alive(p_created timestamptz, p_host_seen timestamptz)
returns boolean language sql stable set search_path = public as $$
  select p_created > now() - interval '10 minutes'
     and (p_host_seen is null
          or p_host_seen > now() - make_interval(secs => greatest(public.config_int('quick_host_fresh_seconds', 20), 5)));
$$;
revoke all on function public.quick_room_alive(timestamptz, timestamptz) from public, anon;
grant execute on function public.quick_room_alive(timestamptz, timestamptz) to authenticated;

drop policy if exists "sala online: ler" on public.online_matches;
create policy "sala online: ler"
  on public.online_matches for select
  using (
    auth.uid() = host_id
    or auth.uid() = guest_id
    or auth.uid() = invited_id
    or (status = 'waiting'
        and (not is_quick_match
             or (public.is_test_user(host_id) = public.my_is_test()
                 and public.quick_room_alive(created_at, host_seen_at)))
        and not public.blocked_between(host_id, auth.uid()))
  );

create or replace function public.join_online_match(p_match_id uuid, p_guest_name text)
returns setof public.online_matches
language plpgsql security invoker as $$
begin
  return query
    update public.online_matches
    set guest_id = auth.uid(), guest_name = p_guest_name, status = 'active', joined_at = now()
    where id = p_match_id and guest_id is null and status = 'waiting'
      and (invited_id is null or invited_id = auth.uid())
      -- partida rápida: conta de teste só com conta de teste (#23)
      and (not is_quick_match or public.is_test_user(host_id) = public.my_is_test())
      -- partida rápida: só sala viva (#113); o convite e o código seguem valendo
      and (not is_quick_match or invited_id is not null or public.quick_room_alive(created_at, host_seen_at))
      -- bloqueio nos dois sentidos e suspensão (#58)
      and not public.blocked_between(host_id, auth.uid())
    returning *;
end $$;
revoke all on function public.join_online_match(uuid, text) from public, anon;
grant execute on function public.join_online_match(uuid, text) to authenticated;

create or replace function public.sweep_empty_rooms()
returns void language plpgsql security definer set search_path = public as $$
begin
  update public.online_matches m
     set status = 'abandoned'
   where m.status = 'active' and m.joined_at is not null
     and m.joined_at < now() - make_interval(secs => greatest(public.config_int('walkover_seconds', 60), 10) * 2)
     and m.joined_at > now() - interval '1 day'
     and not public.room_has_signal(m.id, m.host_id)
     and not public.room_has_signal(m.id, m.guest_id);
  -- sala `waiting` esquecida (#113): rápida com mais de 10 min; de código ou convite com mais de 2 dias
  update public.online_matches m
     set status = 'abandoned'
   where m.status = 'waiting' and m.guest_id is null
     and ((m.is_quick_match and m.created_at < now() - interval '10 minutes')
          or (not m.is_quick_match and m.created_at < now() - interval '2 days'));
end $$;
revoke all on function public.sweep_empty_rooms() from public, anon, authenticated;

-- limpa de uma vez o que já está aberto (inclui as salas 7ebaf69c e bdabaaf2 da issue #113)
select public.sweep_empty_rooms();
