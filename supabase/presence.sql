-- Presença dos amigos, convites e ranqueada entre amigos (beta).
--
-- * profiles.last_seen_at / presence / accept_invites: o app bate o ponto
--   (touch_presence) a cada minuto enquanto está aberto e com conta — "online"
--   (jogo aberto), "in_match" (jogando: não recebe convite) ou "away" (fechou ou
--   minimizou, avisado na hora). accept_invites é o interruptor "Receber convites
--   de amigos" dos Ajustes (padrão ligado).
-- * friend_presence(): para cada amigo, segundos desde o último ponto (nulo =
--   nunca; "away" conta no mínimo 121s, ou seja, fora), o estado e se aceita
--   convites. A tela de Amigos põe online primeiro e só deixa convidar quem está
--   online, fora de partida e aceitando convites.
-- * friend_ranked_allowed(): chave app_config 'friend_ranked_enabled' (1 = liga).
--   Ligada durante o teste fechado para testar a ranqueada entre amigos sem
--   depender da partida rápida; desligar no lançamento.
-- * push_on_match (supabase/push.sql) não manda push de convite para quem
--   desligou o recebimento.

alter table public.profiles add column if not exists last_seen_at timestamptz;
alter table public.profiles add column if not exists presence text not null default 'away'
  check (presence in ('online', 'in_match', 'away'));
alter table public.profiles add column if not exists accept_invites boolean not null default true;

drop function if exists public.touch_presence();
drop function if exists public.touch_presence(text);
create or replace function public.touch_presence(p_state text default 'online', p_accept_invites boolean default true)
returns table (ok boolean)
language plpgsql security definer set search_path = public as $$
begin
  if auth.uid() is null then
    return query select false;
    return;
  end if;
  update public.profiles
     set last_seen_at = now(),
         presence = case when p_state in ('online', 'in_match', 'away') then p_state else 'online' end,
         accept_invites = coalesce(p_accept_invites, true)
   where id = auth.uid();
  return query select true;
end $$;
revoke all on function public.touch_presence(text, boolean) from public;
grant execute on function public.touch_presence(text, boolean) to authenticated;

drop function if exists public.friend_presence();
create function public.friend_presence()
returns table (user_id uuid, seen_secs integer, state text, accepts_invites boolean)
language sql security definer set search_path = public stable as $$
  select p.id,
         case when p.presence = 'away' and p.last_seen_at is not null
              then greatest(extract(epoch from (now() - p.last_seen_at))::integer, 121)
              else extract(epoch from (now() - p.last_seen_at))::integer end,
         p.presence,
         p.accept_invites
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

create or replace function public.push_on_match()
returns trigger
language plpgsql security definer set search_path = public as $$
declare
  v_mode text := case new.mode when 'TACTICAL' then 'Tático' else 'Clássico' end;
begin
  if tg_op = 'INSERT' and new.invited_id is not null and new.status = 'waiting' then
    if coalesce((select accept_invites from public.profiles where id = new.invited_id), true) then
      perform public.queue_push(new.invited_id, 'match_invite', 'Convite para batalha',
        new.host_name || ' te chamou para uma partida (' || v_mode || '). Toque para entrar.');
    end if;
  elsif tg_op = 'UPDATE' and new.is_quick_match and old.guest_id is null and new.guest_id is not null then
    perform public.queue_push(new.host_id, 'quick_match_found', 'Adversário encontrado',
      coalesce(new.guest_name, 'Um comandante') || ' entrou na sua partida rápida. Volte para a batalha!');
  end if;
  return new;
end $$;
