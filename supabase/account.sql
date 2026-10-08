-- Exclusão de conta pelo próprio app (exigência da App Store, regra 5.1.1(v), e boa
-- prática na Play). Apaga o usuário do Auth; perfil, amizades, partidas, passes,
-- tokens de push e o resto caem em cascata. As duas referências sem cascata
-- (vencedor gravado e reserva de partida rápida) são zeradas antes.
create or replace function public.delete_my_account()
returns table (ok boolean)
language plpgsql security definer set search_path = public, auth as $$
declare
  me uuid := auth.uid();
begin
  if me is null then
    return query select false;
    return;
  end if;
  update public.online_matches set winner_id = null where winner_id = me;
  update public.online_matches set offered_to = null, offered_at = null where offered_to = me;
  delete from auth.users where id = me;
  return query select true;
end $$;
revoke all on function public.delete_my_account() from public, anon;
grant execute on function public.delete_my_account() to authenticated;
