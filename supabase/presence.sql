-- Presença dos amigos e ranqueada entre amigos (beta).
--
-- * profiles.last_seen_at: o app bate o ponto (touch_presence) a cada minuto
--   enquanto está aberto e com conta. Online = visto há menos de 2 minutos.
-- * friend_presence(): segundos desde a última vez que cada amigo foi visto
--   (nulo = nunca registrado). A tela de Amigos põe online primeiro e só deixa
--   convidar quem está online — convite para quem está fora nunca seria visto.
-- * friend_ranked_allowed(): chave app_config 'friend_ranked_enabled' (1 = liga).
--   Ligada durante o teste fechado para dar para testar a ranqueada entre amigos
--   sem depender da partida rápida; desligar no lançamento (convite volta a ser
--   sempre casual, como no matchmaking de verdade).

alter table public.profiles add column if not exists last_seen_at timestamptz;

create or replace function public.touch_presence()
returns table (ok boolean)
language plpgsql security definer set search_path = public as $$
begin
  if auth.uid() is null then
    return query select false;
    return;
  end if;
  update public.profiles set last_seen_at = now() where id = auth.uid();
  return query select true;
end $$;
revoke all on function public.touch_presence() from public;
grant execute on function public.touch_presence() to authenticated;

create or replace function public.friend_presence()
returns table (user_id uuid, seen_secs integer)
language sql security definer set search_path = public stable as $$
  select p.id, extract(epoch from (now() - p.last_seen_at))::integer
  from public.friendships f
  join public.profiles p
    on p.id = case when f.requester_id = auth.uid() then f.addressee_id else f.requester_id end
  where (f.requester_id = auth.uid() or f.addressee_id = auth.uid()) and f.status = 'accepted';
$$;
revoke all on function public.friend_presence() from public;
grant execute on function public.friend_presence() to authenticated;

insert into public.app_config (key, value)
values ('friend_ranked_enabled', '1')
on conflict (key) do nothing;

create or replace function public.friend_ranked_allowed()
returns table (allowed boolean)
language sql security definer set search_path = public stable as $$
  select public.config_int('friend_ranked_enabled', 0) = 1;
$$;
revoke all on function public.friend_ranked_allowed() from public;
grant execute on function public.friend_ranked_allowed() to authenticated;
