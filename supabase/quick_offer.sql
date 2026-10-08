-- Balão de partida rápida ("Disponível para partida rápida" nos Ajustes) vai para
-- UM comandante disponível por vez (substitui o find_quick_offer de online.sql).
--
-- * find_quick_offer reserva a sala para quem chamou por 60s (offered_to/offered_at)
--   e devolve só ela. Enquanto a reserva vale, ninguém mais recebe o balão.
-- * Aceitou: a sala sai de 'waiting' e o balão some para todos.
-- * Recusou ("Agora não") ou deixou o balão passar (o app fecha sozinho em 50s):
--   decline_quick_offer anota em declined_by e libera a sala para o próximo
--   disponível — nunca volta para quem já recusou.

alter table public.online_matches add column if not exists offered_to uuid references auth.users(id) on delete set null;
alter table public.online_matches add column if not exists offered_at timestamptz;
alter table public.online_matches add column if not exists declined_by uuid[] not null default '{}';

create or replace function public.find_quick_offer(p_casual boolean, p_ranked boolean)
returns setof public.online_matches
language plpgsql security definer set search_path = public as $$
declare
  me uuid := auth.uid();
  v_id uuid;
begin
  if me is null then return; end if;
  select m.id into v_id
  from public.online_matches m
  where m.status = 'waiting'
    and m.is_quick_match
    and m.guest_id is null
    and m.invited_id is null
    and m.host_id <> me
    and not (me = any(m.declined_by))
    and ((m.ranked and p_ranked) or (not m.ranked and p_casual))
    and m.created_at > now() - interval '10 minutes'
    and (m.offered_to is null or m.offered_to = me or m.offered_at < now() - interval '60 seconds')
  order by m.created_at asc
  limit 1
  for update skip locked;
  if v_id is null then return; end if;
  update public.online_matches set offered_to = me, offered_at = now() where id = v_id;
  return query select * from public.online_matches where id = v_id;
end $$;
grant execute on function public.find_quick_offer(boolean, boolean) to authenticated;

create or replace function public.decline_quick_offer(p_match_id uuid)
returns table (ok boolean)
language plpgsql security definer set search_path = public as $$
begin
  update public.online_matches
     set declined_by = array_append(declined_by, auth.uid()),
         offered_to = null,
         offered_at = null
   where id = p_match_id and status = 'waiting' and not (auth.uid() = any(declined_by));
  return query select true;
end $$;
revoke all on function public.decline_quick_offer(uuid) from public;
grant execute on function public.decline_quick_offer(uuid) to authenticated;
