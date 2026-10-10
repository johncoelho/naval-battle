-- Presença dos amigos, convites e ranqueada entre amigos (beta).
--
-- * profiles.last_seen_at / presence / accept_invites: o app bate o ponto
--   (touch_presence) a cada minuto enquanto está aberto e com conta — "online"
--   (jogo aberto), "in_match" (jogando: não recebe convite) ou "away" (fechou ou
--   minimizou, avisado na hora). accept_invites é o interruptor "Receber convites
--   de amigos" dos Ajustes (padrão ligado).
-- * friend_presence(): para cada amigo (com o XP, para a patente no cartão), segundos desde o último ponto (nulo =
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

-- Aviso "amigo online" (0.31.0, #19): quando um jogador passa de fora (away, sem ponto
-- ou ponto com mais de 2 min) para online, cada amigo com friend_online_push e
-- accept_invites ligados, fora do jogo e com token de push recebe um push. Anti-spam
-- no servidor: 1 aviso por par a cada friend_online_pair_hours (3) e no máximo
-- friend_online_daily_cap (5) por destinatário em 24 h. friend_online_push_log só é
-- mexida pela função (RLS ligado, sem políticas, como push_tokens).
alter table public.profiles add column if not exists friend_online_push boolean not null default true;

create table if not exists public.friend_online_push_log (
  recipient_id uuid        not null references auth.users(id) on delete cascade,
  friend_id    uuid        not null references auth.users(id) on delete cascade,
  sent_at      timestamptz not null default now()
);
create index if not exists friend_online_push_log_recipient_idx
  on public.friend_online_push_log (recipient_id, sent_at);
alter table public.friend_online_push_log enable row level security;

insert into public.app_config (key, value)
values ('friend_online_pair_hours', '3'), ('friend_online_daily_cap', '5')
on conflict (key) do nothing;

-- p_friend_online_push nulo (app antigo, que não manda o campo) não muda o valor gravado:
-- o app antigo não pode religar a opção de quem a desligou. A assinatura (text, boolean)
-- sai para o PostgREST não ficar com duas funções ambíguas; a chamada antiga com dois
-- argumentos nomeados cai nesta pelo default.
drop function if exists public.touch_presence();
drop function if exists public.touch_presence(text);
drop function if exists public.touch_presence(text, boolean);
create or replace function public.touch_presence(
  p_state text default 'online',
  p_accept_invites boolean default true,
  p_friend_online_push boolean default null
)
returns table (ok boolean)
language plpgsql security definer set search_path = public as $$
declare
  me uuid := auth.uid();
  v_state text := case when p_state in ('online', 'in_match', 'away') then p_state else 'online' end;
  v_old_presence text;
  v_old_seen timestamptz;
  v_name text;
  v_pair_hours integer;
  v_cap integer;
  r record;
begin
  if me is null then
    return query select false;
    return;
  end if;
  select presence, last_seen_at, username into v_old_presence, v_old_seen, v_name
    from public.profiles where id = me;
  update public.profiles
     set last_seen_at = now(),
         presence = v_state,
         accept_invites = coalesce(p_accept_invites, true),
         friend_online_push = coalesce(p_friend_online_push, friend_online_push)
   where id = me;

  -- só a transição fora -> online dispara; o ponto de cada minuto nunca
  if v_state = 'online'
     and (v_old_presence = 'away' or v_old_seen is null or v_old_seen < now() - interval '2 minutes') then
    begin
      v_pair_hours := public.config_int('friend_online_pair_hours', 3);
      v_cap := public.config_int('friend_online_daily_cap', 5);
      for r in
        select p.id
          from public.friendships f
          join public.profiles p
            on p.id = case when f.requester_id = me then f.addressee_id else f.requester_id end
         where (f.requester_id = me or f.addressee_id = me)
           and f.status = 'accepted'
           and p.friend_online_push
           and p.accept_invites
           and (p.presence = 'away' or p.last_seen_at is null or p.last_seen_at < now() - interval '2 minutes')
           and exists (select 1 from public.push_tokens t where t.user_id = p.id)
           and not exists (
             select 1 from public.friend_online_push_log l
              where l.recipient_id = p.id and l.friend_id = me
                and l.sent_at > now() - make_interval(hours => v_pair_hours))
           and (select count(*) from public.friend_online_push_log l
                 where l.recipient_id = p.id and l.sent_at > now() - interval '24 hours') < v_cap
      loop
        perform public.queue_push(r.id, 'friend_online',
          coalesce(v_name, 'Um amigo') || ' está online', 'Chame para uma batalha.');
        insert into public.friend_online_push_log (recipient_id, friend_id) values (r.id, me);
      end loop;
    exception when others then
      -- erro de push nunca pode derrubar a presença
      null;
    end;
  end if;
  return query select true;
end $$;
revoke all on function public.touch_presence(text, boolean, boolean) from public;
grant execute on function public.touch_presence(text, boolean, boolean) to authenticated;

drop function if exists public.friend_presence();
create function public.friend_presence()
returns table (user_id uuid, seen_secs integer, state text, accepts_invites boolean, xp integer)
language sql security definer set search_path = public stable as $$
  select p.id,
         case when p.presence = 'away' and p.last_seen_at is not null
              then greatest(extract(epoch from (now() - p.last_seen_at))::integer, 121)
              else extract(epoch from (now() - p.last_seen_at))::integer end,
         p.presence,
         p.accept_invites,
         p.xp
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
