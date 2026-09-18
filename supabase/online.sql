-- Naval Battle · modo Online (partida pela internet)
-- Rode este script uma vez no SQL Editor do projeto, depois de `schema.sql`.
--
-- Duas peças novas:
-- 1. `online_matches` + `online_messages` — a sala da partida e as jogadas trocadas
--    nela. O corpo de cada mensagem é o mesmo protocolo de texto que já roda na
--    rede local (`HELLO|`, `FLEET|`, `ACT|x|y`, `ABIL|code|charge` — ver
--    `data/LanLink.kt`), só o transporte muda: em vez de socket TCP, cada lado
--    grava uma linha nesta tabela e o outro lado consulta (`GET .../online_messages
--    ?match_id=eq.X&id=gt.Y`) a cada 1-2 segundos. Simples, sem WebSocket, e o
--    atraso de um round-trip HTTP é imperceptível num jogo por turnos.
-- 2. `friendships` — lista de amigos de verdade (pedido, aceitar/recusar), para
--    convidar quem já é amigo direto de dentro do jogo. Continua existindo também
--    o convite por código de sala (`invite_code` em `online_matches`), que não
--    precisa de amizade nenhuma — é só compartilhar o código por fora do jogo.

-- ------------------------------------------------------------------ online_matches

create table if not exists public.online_matches (
  id             uuid        primary key default gen_random_uuid(),
  host_id        uuid        not null references auth.users(id) on delete cascade,
  guest_id       uuid        references auth.users(id) on delete cascade,
  host_name      text        not null,
  guest_name     text,
  mode           text        not null default 'CLASSIC' check (mode in ('CLASSIC', 'TACTICAL')),
  status         text        not null default 'waiting' check (status in ('waiting', 'active', 'finished', 'abandoned')),
  is_quick_match boolean     not null default false,
  invite_code    text        unique,
  created_at     timestamptz not null default now(),
  updated_at     timestamptz not null default now()
);

-- convite mirado num amigo específico (em vez de partida rápida ou código
-- solto): nulo é convite aberto; preenchido, só esse comandante pode entrar,
-- e é ele quem recebe o banner "fulano te convidou" fora da tela Online
alter table public.online_matches add column if not exists invited_id uuid references auth.users(id) on delete cascade;

alter table public.online_matches enable row level security;

-- quem está dentro da sala vê a sala; quem procura partida rápida ou tem o
-- código vê salas ainda abertas (sem isso não dá para encontrar/entrar); quem
-- foi convidado precisa ver a própria sala mirada mesmo antes de entrar nela
drop policy if exists "sala online: ler" on public.online_matches;
create policy "sala online: ler"
  on public.online_matches for select
  using (
    auth.uid() = host_id
    or auth.uid() = guest_id
    or auth.uid() = invited_id
    or status = 'waiting'
  );

drop policy if exists "sala online: criar" on public.online_matches;
create policy "sala online: criar"
  on public.online_matches for insert
  with check (auth.uid() = host_id);

-- host e guest podem atualizar; quem ainda não é ninguém na sala só pode
-- mirar numa sala 'waiting' (é como se entra: escrevendo o próprio id em
-- guest_id) — o with check trava o resultado a sempre citar quem mexeu; o
-- convidado mirado também pode mexer (recusar sem virar guest)
drop policy if exists "sala online: atualizar" on public.online_matches;
create policy "sala online: atualizar"
  on public.online_matches for update
  using (auth.uid() = host_id or auth.uid() = guest_id or auth.uid() = invited_id or status = 'waiting')
  with check (auth.uid() = host_id or auth.uid() = guest_id or auth.uid() = invited_id);

create or replace function public.touch_online_match()
returns trigger language plpgsql as $$
begin
  new.updated_at = now();
  return new;
end $$;

drop trigger if exists online_matches_touch on public.online_matches;
create trigger online_matches_touch
  before update on public.online_matches
  for each row execute function public.touch_online_match();

-- ------------------------------------------------------------------ online_messages

create table if not exists public.online_messages (
  id         bigint      generated always as identity primary key,
  match_id   uuid        not null references public.online_matches(id) on delete cascade,
  sender_id  uuid        not null references auth.users(id) on delete cascade,
  body       text        not null,
  created_at timestamptz not null default now()
);

create index if not exists online_messages_match_id_idx on public.online_messages (match_id, id);

alter table public.online_messages enable row level security;

drop policy if exists "jogada online: ler" on public.online_messages;
create policy "jogada online: ler"
  on public.online_messages for select
  using (
    exists (
      select 1 from public.online_matches m
      where m.id = match_id and (m.host_id = auth.uid() or m.guest_id = auth.uid())
    )
  );

drop policy if exists "jogada online: criar" on public.online_messages;
create policy "jogada online: criar"
  on public.online_messages for insert
  with check (
    auth.uid() = sender_id
    and exists (
      select 1 from public.online_matches m
      where m.id = match_id and (m.host_id = auth.uid() or m.guest_id = auth.uid())
    )
  );

-- ------------------------------------------------------------------ friendships

-- nome dos dois lados vai gravado na própria linha: `profiles` de outra
-- pessoa é ilegível por RLS, então não dá para "juntar" o nome do amigo numa
-- consulta comum — o cliente já sabe os dois nomes no momento do pedido
-- (o seu, e o de quem apareceu na busca por `search_commander`)
create table if not exists public.friendships (
  id                  uuid        primary key default gen_random_uuid(),
  requester_id        uuid        not null references auth.users(id) on delete cascade,
  addressee_id        uuid        not null references auth.users(id) on delete cascade,
  requester_username  text        not null,
  addressee_username  text        not null,
  status              text        not null default 'pending' check (status in ('pending', 'accepted', 'declined')),
  created_at          timestamptz not null default now(),
  updated_at          timestamptz not null default now(),
  unique (requester_id, addressee_id),
  check (requester_id <> addressee_id)
);

alter table public.friendships add column if not exists requester_username text;
alter table public.friendships add column if not exists addressee_username text;
update public.friendships set requester_username = coalesce(requester_username, '?') where requester_username is null;
update public.friendships set addressee_username = coalesce(addressee_username, '?') where addressee_username is null;
alter table public.friendships alter column requester_username set not null;
alter table public.friendships alter column addressee_username set not null;

alter table public.friendships enable row level security;

drop policy if exists "amizade: ler" on public.friendships;
create policy "amizade: ler"
  on public.friendships for select
  using (auth.uid() = requester_id or auth.uid() = addressee_id);

drop policy if exists "amizade: pedir" on public.friendships;
create policy "amizade: pedir"
  on public.friendships for insert
  with check (auth.uid() = requester_id);

-- quem recebeu o pedido aceita/recusa; quem pediu pode cancelar (mesma linha,
-- por isso os dois lados entram no using — o with check trava o resultado)
drop policy if exists "amizade: responder" on public.friendships;
create policy "amizade: responder"
  on public.friendships for update
  using (auth.uid() = requester_id or auth.uid() = addressee_id)
  with check (auth.uid() = requester_id or auth.uid() = addressee_id);

drop policy if exists "amizade: desfazer" on public.friendships;
create policy "amizade: desfazer"
  on public.friendships for delete
  using (auth.uid() = requester_id or auth.uid() = addressee_id);

create or replace function public.touch_friendship()
returns trigger language plpgsql as $$
begin
  new.updated_at = now();
  return new;
end $$;

drop trigger if exists friendships_touch on public.friendships;
create trigger friendships_touch
  before update on public.friendships
  for each row execute function public.touch_friendship();

-- busca de comandante por nome, para mandar pedido de amizade — `profiles` é
-- trancada por RLS a "só a própria linha", então a busca precisa de uma função
-- com `security definer` que devolve só o mínimo (id e nome), nunca e-mail
create or replace function public.search_commander(query text)
returns table (id uuid, username text)
language sql security definer set search_path = public stable as $$
  select p.id, p.username
  from public.profiles p
  where p.username ilike query || '%'
    and p.id <> auth.uid()
  order by p.username
  limit 10;
$$;

revoke all on function public.search_commander(text) from public;
grant execute on function public.search_commander(text) to authenticated;

-- ------------------------------------------------------------------ escritas via RPC
--
-- HttpURLConnection (cliente Android, sem biblioteca de rede extra) não sabe
-- mandar PATCH — só GET/POST/PUT/DELETE. Em vez de reflection para forçar o
-- método, as três atualizações do modo online viram função chamada por POST.
-- `security invoker` (o padrão, explícito aqui) mantém a chamada sujeita às
-- mesmas políticas de RLS de quem chamou — a função não abre porta nenhuma.

create or replace function public.join_online_match(p_match_id uuid, p_guest_name text)
returns setof public.online_matches
language plpgsql security invoker as $$
begin
  -- a condição "guest_id is null" é a trava contra dois convidados entrando
  -- na mesma sala ao mesmo tempo: só o primeiro UPDATE acerta a linha; quando
  -- a sala é um convite mirado (invited_id preenchido), só quem foi convidado
  -- pode ocupar a vaga de guest — outro comandante nem acha, mas trava aqui
  -- também caso ele já tenha o id da sala por algum outro caminho
  return query
    update public.online_matches
    set guest_id = auth.uid(), guest_name = p_guest_name, status = 'active'
    where id = p_match_id and guest_id is null and status = 'waiting'
      and (invited_id is null or invited_id = auth.uid())
    returning *;
end $$;

grant execute on function public.join_online_match(uuid, text) to authenticated;

create or replace function public.decline_online_invite(p_match_id uuid)
returns void
language sql security invoker as $$
  -- recusa sem virar guest: o anfitrião para de esperar em vez de ficar
  -- preso indefinidamente numa sala que ninguém mais vai aceitar
  update public.online_matches
  set status = 'abandoned'
  where id = p_match_id and invited_id = auth.uid() and status = 'waiting';
$$;

grant execute on function public.decline_online_invite(uuid) to authenticated;

create or replace function public.close_online_match(p_match_id uuid, p_status text)
returns void
language sql security invoker as $$
  update public.online_matches set status = p_status where id = p_match_id;
$$;

grant execute on function public.close_online_match(uuid, text) to authenticated;

create or replace function public.respond_friend_request(p_friendship_id uuid, p_accept boolean)
returns void
language sql security invoker as $$
  update public.friendships
  set status = case when p_accept then 'accepted' else 'declined' end
  where id = p_friendship_id;
$$;

grant execute on function public.respond_friend_request(uuid, boolean) to authenticated;
