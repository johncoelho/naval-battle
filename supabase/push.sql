-- Push de verdade (Android, via Firebase Cloud Messaging).
--
-- Fluxo: um gatilho no banco grava a mensagem em push_outbox e chama a Edge
-- Function `push` (pg_net) passando só o id da linha. A função lê a linha com a
-- service role, marca como enviada e manda para os tokens do destinatário. Como
-- ela só envia o que já está na fila e nunca duas vezes, não precisa de segredo
-- compartilhado entre banco e função.
--
-- Eventos que geram push:
--   * pedido de amizade recebido          -> quem recebeu
--   * pedido de amizade aceito            -> quem pediu
--   * convite de amigo para partida       -> o convidado
--   * adversário entrou na partida rápida -> o anfitrião
--
-- Com o app aberto o Android não mostra a notificação (o app já tem os balões);
-- ela aparece com o app fechado ou em segundo plano.

create extension if not exists pg_net;

create table if not exists public.push_tokens (
  token      text        primary key,
  user_id    uuid        not null references auth.users(id) on delete cascade,
  platform   text        not null default 'android' check (platform in ('android', 'ios')),
  updated_at timestamptz not null default now()
);
create index if not exists push_tokens_user_idx on public.push_tokens (user_id);
alter table public.push_tokens enable row level security;
-- sem políticas: só as funções abaixo (security definer) mexem na tabela

create table if not exists public.push_outbox (
  id         bigint      generated always as identity primary key,
  user_id    uuid        not null references auth.users(id) on delete cascade,
  kind       text        not null,
  title      text        not null,
  body       text        not null,
  created_at timestamptz not null default now(),
  sent_at    timestamptz,
  result     text
);
alter table public.push_outbox enable row level security;

-- O app chama ao entrar na conta e quando o Firebase troca o token.
create or replace function public.register_push_token(p_token text, p_platform text default 'android')
returns table (ok boolean)
language plpgsql security definer set search_path = public as $$
begin
  if auth.uid() is null or coalesce(length(p_token), 0) < 20 then
    return query select false;
    return;
  end if;
  insert into public.push_tokens (token, user_id, platform, updated_at)
  values (p_token, auth.uid(), coalesce(p_platform, 'android'), now())
  on conflict (token) do update
    set user_id = excluded.user_id, platform = excluded.platform, updated_at = now();
  return query select true;
end $$;
grant execute on function public.register_push_token(text, text) to authenticated;

-- Ao sair da conta o aparelho para de receber os avisos dela.
create or replace function public.unregister_push_token(p_token text)
returns table (ok boolean)
language plpgsql security definer set search_path = public as $$
begin
  delete from public.push_tokens where token = p_token and user_id = auth.uid();
  return query select true;
end $$;
grant execute on function public.unregister_push_token(text) to authenticated;

create or replace function public.queue_push(p_user uuid, p_kind text, p_title text, p_body text)
returns void
language plpgsql security definer set search_path = public, extensions as $$
declare
  v_id bigint;
begin
  if p_user is null or not exists (select 1 from public.push_tokens where user_id = p_user) then
    return;
  end if;
  insert into public.push_outbox (user_id, kind, title, body)
  values (p_user, p_kind, p_title, p_body)
  returning id into v_id;
  perform net.http_post(
    url     := 'https://cwtslesnthbenxswdcbv.supabase.co/functions/v1/push',
    body    := jsonb_build_object('id', v_id),
    headers := jsonb_build_object('Content-Type', 'application/json')
  );
exception when others then
  -- push nunca pode derrubar a jogada/pedido que disparou o gatilho
  null;
end $$;
revoke all on function public.queue_push(uuid, text, text, text) from public, anon, authenticated;

create or replace function public.push_on_friendship()
returns trigger
language plpgsql security definer set search_path = public as $$
begin
  if tg_op = 'INSERT' and new.status = 'pending' then
    perform public.queue_push(new.addressee_id, 'friend_request', 'Pedido de amizade',
      new.requester_username || ' quer entrar na sua lista de amigos no Naval Battle.');
  elsif tg_op = 'UPDATE' and old.status = 'pending' and new.status = 'accepted' then
    perform public.queue_push(new.requester_id, 'friend_accepted', 'Pedido aceito',
      new.addressee_username || ' aceitou seu pedido. Agora dá para chamar para a batalha.');
  end if;
  return new;
end $$;

drop trigger if exists push_on_friendship on public.friendships;
create trigger push_on_friendship
  after insert or update of status on public.friendships
  for each row execute function public.push_on_friendship();

create or replace function public.push_on_match()
returns trigger
language plpgsql security definer set search_path = public as $$
declare
  v_mode text := case new.mode when 'TACTICAL' then 'Tático' else 'Clássico' end;
begin
  if tg_op = 'INSERT' and new.invited_id is not null and new.status = 'waiting' then
    perform public.queue_push(new.invited_id, 'match_invite', 'Convite para batalha',
      new.host_name || ' te chamou para uma partida (' || v_mode || '). Toque para entrar.');
  elsif tg_op = 'UPDATE' and new.is_quick_match and old.guest_id is null and new.guest_id is not null then
    perform public.queue_push(new.host_id, 'quick_match_found', 'Adversário encontrado',
      coalesce(new.guest_name, 'Um comandante') || ' entrou na sua partida rápida. Volte para a batalha!');
  end if;
  return new;
end $$;

drop trigger if exists push_on_match on public.online_matches;
create trigger push_on_match
  after insert or update of guest_id on public.online_matches
  for each row execute function public.push_on_match();
