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

-- partida ranqueada: só entra no pareamento de partida rápida com outro ranked
-- (convite de amigo é sempre casual, de propósito — ranqueada é matchmaking,
-- não desafio combinado, mesmo padrão de Clash Royale/jogos do gênero); os
-- dois campos de "resultado gravado" travam contra o cliente reportar duas
-- vezes o mesmo lado (reabrir a tela de resultado, retomar o app, etc.)
alter table public.online_matches add column if not exists ranked boolean not null default false;
alter table public.online_matches add column if not exists host_result_recorded boolean not null default false;
alter table public.online_matches add column if not exists guest_result_recorded boolean not null default false;

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

-- ------------------------------------------------------------------ ranqueada e temporadas
--
-- Temporadas são as 4 estações do ano (calendário do hemisfério sul, já que o
-- público é majoritariamente BR) — calculadas na hora, sem tabela de
-- temporadas pra manter e sem cron pra virar a data: `current_season()`
-- sempre responde a partir de `now()` do próprio banco, então não existe
-- fuso/relógio do aparelho para desincronizar com o servidor.

create or replace function public.current_season()
returns table (season_key text, name text)
language sql stable as $$
  select
    (case when extract(month from now()) = 12
          then (extract(year from now())::int + 1)
          else extract(year from now())::int
     end)::text || '-' ||
    (case
       when extract(month from now()) in (12, 1, 2) then 'verao'
       when extract(month from now()) in (3, 4, 5) then 'outono'
       when extract(month from now()) in (6, 7, 8) then 'inverno'
       else 'primavera'
     end) as season_key,
    (case
       when extract(month from now()) in (12, 1, 2) then 'Verão'
       when extract(month from now()) in (3, 4, 5) then 'Outono'
       when extract(month from now()) in (6, 7, 8) then 'Inverno'
       else 'Primavera'
     end) as name;
$$;

grant execute on function public.current_season() to authenticated;

-- pontuação geral (histórico completo, nunca zera) vive direto em `profiles`;
-- pontuação por temporada é acumulada à parte e reinicia sozinha a cada nova
-- `season_key` (a linha antiga fica gravada, viram os "recordes" de temporadas
-- passadas — não tem exclusão nenhuma, só para de receber pontos novos)
alter table public.profiles add column if not exists ranked_rating integer not null default 1000;

create table if not exists public.ranked_season_stats (
  user_id    uuid        not null references auth.users(id) on delete cascade,
  season_key text        not null,
  username   text        not null,
  points     integer     not null default 1000,
  matches    integer     not null default 0,
  wins       integer     not null default 0,
  updated_at timestamptz not null default now(),
  primary key (user_id, season_key)
);

alter table public.ranked_season_stats enable row level security;

-- ranking é público por natureza (todo jogo do gênero mostra o placar geral);
-- as escritas só acontecem via record_ranked_result, nunca direto do cliente
drop policy if exists "ranking temporada: ler" on public.ranked_season_stats;
create policy "ranking temporada: ler"
  on public.ranked_season_stats for select
  using (true);

-- placar geral (profiles.ranked_rating) — mesmo raciocínio de search_commander:
-- profiles é trancada a "só a própria linha", então o ranking cross-usuário
-- precisa de uma função com security definer que devolve só o mínimo público
-- retrato e insígnia entraram no placar pra mostrar o comandante de verdade
-- em vez de só um nome; o de temporada precisa de join com profiles porque
-- ranked_season_stats não guarda esses dois campos (são de perfil, não de
-- temporada) — a assinatura não mudou, mas as colunas de saída sim, por isso
-- o drop antes de recriar (create or replace não troca o formato de retorno)
drop function if exists public.leaderboard_overall(integer);
create function public.leaderboard_overall(p_limit integer default 50)
returns table (user_id uuid, username text, insignia text, avatar text, rating integer, matches integer, wins integer)
language sql security definer set search_path = public stable as $$
  select p.id, p.username, p.insignia, p.avatar, p.ranked_rating, p.matches, p.wins
  from public.profiles p
  where p.matches > 0
  order by p.ranked_rating desc
  limit p_limit;
$$;

revoke all on function public.leaderboard_overall(integer) from public;
grant execute on function public.leaderboard_overall(integer) to authenticated;

drop function if exists public.leaderboard_season(integer);
create function public.leaderboard_season(p_limit integer default 50)
returns table (user_id uuid, username text, insignia text, avatar text, points integer, matches integer, wins integer)
language sql security definer set search_path = public stable as $$
  select s.user_id, s.username, p.insignia, p.avatar, s.points, s.matches, s.wins
  from public.ranked_season_stats s
  join public.current_season() c on s.season_key = c.season_key
  left join public.profiles p on p.id = s.user_id
  order by s.points desc
  limit p_limit;
$$;

revoke all on function public.leaderboard_season(integer) from public;
grant execute on function public.leaderboard_season(integer) to authenticated;

-- posição e pontuação do próprio comandante, mesmo fora do top da lista —
-- sem isso quem não está entre os melhores nunca saberia a própria colocação.
-- "position" é palavra reservada do SQL (usada em SUBSTRING ... FROM ... FOR
-- ... e afins), por isso vai entre aspas em todo lugar que aparece como nome
create or replace function public.my_rank(p_season boolean)
returns table ("position" bigint, rating integer)
language sql security definer set search_path = public stable as $$
  select "position", rating from (
    select
      row_number() over (order by p.ranked_rating desc) as "position",
      p.ranked_rating as rating,
      p.id
    from public.profiles p
    where p.matches > 0 and not p_season
  ) ranked
  where ranked.id = auth.uid()
  union all
  select "position", rating from (
    select
      row_number() over (order by s.points desc) as "position",
      s.points as rating,
      s.user_id
    from public.ranked_season_stats s, public.current_season() c
    where s.season_key = c.season_key and p_season
  ) ranked
  where ranked.user_id = auth.uid();
$$;

revoke all on function public.my_rank(boolean) from public;
grant execute on function public.my_rank(boolean) to authenticated;

-- folha de serviço pública de um amigo, para a tela "ver perfil" da gestão de
-- amigos — só devolve algo se já existir amizade aceita entre os dois lados,
-- e só os campos públicos (sem e-mail); avatar fica de fora porque é local
-- só do aparelho de cada um, nunca sincronizado com a nuvem
create or replace function public.friend_profile(p_friend_id uuid)
returns table (
  username text, insignia text, xp integer, matches integer,
  wins integer, best_streak integer, ranked_rating integer
)
language sql security definer set search_path = public stable as $$
  select p.username, p.insignia, p.xp, p.matches, p.wins, p.best_streak, p.ranked_rating
  from public.profiles p
  where p.id = p_friend_id
    and exists (
      select 1 from public.friendships f
      where f.status = 'accepted'
        and ((f.requester_id = auth.uid() and f.addressee_id = p_friend_id)
          or (f.addressee_id = auth.uid() and f.requester_id = p_friend_id))
    );
$$;

revoke all on function public.friend_profile(uuid) from public;
grant execute on function public.friend_profile(uuid) to authenticated;

-- retrato e patente públicos de qualquer comandante, sem exigir amizade
-- (diferente de friend_profile) — usado pra mostrar quem foi encontrado na
-- partida rápida e o nome do adversário durante o combate
create or replace function public.opponent_profile(p_user_id uuid)
returns table (username text, insignia text, avatar text, xp integer, ranked_rating integer)
language sql security definer set search_path = public stable as $$
  select p.username, p.insignia, p.avatar, p.xp, p.ranked_rating
  from public.profiles p
  where p.id = p_user_id;
$$;

revoke all on function public.opponent_profile(uuid) from public;
grant execute on function public.opponent_profile(uuid) to authenticated;

-- ranking de troféus da temporada: calculado sob demanda a partir do placar já
-- congelado — nenhum resultado novo grava numa temporada que não seja a
-- corrente (record_ranked_result sempre usa current_season()), então os dados
-- de uma temporada passada nunca mais mudam depois que a próxima começa. Isso
-- dispensa cron ou tabela extra pra "fechar" a temporada: o próprio cálculo
-- na hora da consulta já é o resultado final. Top 10% leva ouro, os próximos
-- 20% prata, os próximos 30% bronze, o resto sem medalha.
create or replace function public.season_trophies(p_season_key text default null)
returns table (
  user_id uuid, username text, insignia text, avatar text,
  points integer, "position" bigint, tier text
)
language sql security definer set search_path = public stable as $$
  with target as (
    select coalesce(
      p_season_key,
      (select s.season_key from public.ranked_season_stats s, public.current_season() c
       where s.season_key <> c.season_key
       order by s.season_key desc limit 1)
    ) as season_key
  ),
  ranked as (
    select
      s.user_id, s.username, p.insignia, p.avatar, s.points,
      row_number() over (order by s.points desc) as "position",
      count(*) over () as total
    from public.ranked_season_stats s
    join target t on s.season_key = t.season_key
    left join public.profiles p on p.id = s.user_id
  )
  select
    user_id, username, insignia, avatar, points, "position",
    case
      when "position" <= greatest(1, ceil(total * 0.10)) then 'ouro'
      when "position" <= greatest(1, ceil(total * 0.30)) then 'prata'
      when "position" <= greatest(1, ceil(total * 0.60)) then 'bronze'
      else null
    end as tier
  from ranked
  order by "position";
$$;

revoke all on function public.season_trophies(text) from public;
grant execute on function public.season_trophies(text) to authenticated;

-- fecha o resultado de uma partida ranqueada — cada lado chama uma vez só (as
-- flags host_result_recorded/guest_result_recorded travam contra reenvio). A
-- pontuação pesa o desempenho: vitória sempre soma (20 a 50), derrota sempre
-- desconta (-15 a -5) — nunca o contrário, verificável termo a termo — com
-- bônus por precisão de tiro nos dois casos e, só na vitória, por quanto da
-- própria frota ainda restava de pé (vencer com o casco intacto vale mais que
-- vencer raspando). Mais simples que ELO de verdade, mas justo o bastante:
-- quem joga bem e perde cai menos do que quem joga mal e perde.
drop function if exists public.record_ranked_result(uuid, boolean);
create function public.record_ranked_result(
  p_match_id uuid, p_won boolean, p_accuracy integer, p_ships_left integer
)
returns void
language plpgsql security definer set search_path = public as $$
declare
  m public.online_matches%rowtype;
  is_host boolean;
  already boolean;
  delta integer;
  accuracy integer;
  ships_left integer;
  my_username text;
  szn text;
begin
  select * into m from public.online_matches where id = p_match_id;
  if m.id is null or not m.ranked then
    return;
  end if;

  is_host := (m.host_id = auth.uid());
  if not is_host and (m.guest_id is null or m.guest_id <> auth.uid()) then
    return;
  end if;

  already := case when is_host then m.host_result_recorded else m.guest_result_recorded end;
  if already then
    return;
  end if;

  if is_host then
    update public.online_matches set host_result_recorded = true where id = p_match_id;
    my_username := m.host_name;
  else
    update public.online_matches set guest_result_recorded = true where id = p_match_id;
    my_username := m.guest_name;
  end if;

  accuracy := greatest(0, least(100, coalesce(p_accuracy, 0)));
  ships_left := greatest(0, least(5, coalesce(p_ships_left, 0)));

  if p_won then
    delta := 20 + round(accuracy * 0.15) + ships_left * 3;
  else
    delta := -(15 - round(accuracy * 0.10));
  end if;

  update public.profiles
    set ranked_rating = greatest(0, ranked_rating + delta)
    where id = auth.uid();

  select season_key into szn from public.current_season();

  insert into public.ranked_season_stats (user_id, season_key, username, points, matches, wins)
  values (auth.uid(), szn, my_username, greatest(0, 1000 + delta), 1, case when p_won then 1 else 0 end)
  on conflict (user_id, season_key) do update
    set points = greatest(0, public.ranked_season_stats.points + delta),
        matches = public.ranked_season_stats.matches + 1,
        wins = public.ranked_season_stats.wins + case when p_won then 1 else 0 end,
        username = excluded.username,
        updated_at = now();
end $$;

revoke all on function public.record_ranked_result(uuid, boolean, integer, integer) from public;
grant execute on function public.record_ranked_result(uuid, boolean, integer, integer) to authenticated;
