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

-- balão "aceitar partida rápida" de qualquer tela (0.14.0): quem marcou nos
-- Ajustes que está disponível vê salas de partida rápida esperando adversário,
-- de qualquer modo (Clássico ou Tático), dos tipos que aceita (casual,
-- ranqueada ou ambas). Só lê — entrar continua sendo pelo join_online_match,
-- com a mesma trava de "guest_id ainda nulo". Sala com mais de 10 minutos é
-- descartada pelo relógio do servidor: quem fechou o app no meio da espera
-- deixa a sala 'waiting' para sempre, e aceitar uma dessas seria entrar numa
-- partida sem ninguém do outro lado. Devolve algumas, mais antigas primeiro —
-- o cliente pula as que o comandante já dispensou com "Agora não".
create or replace function public.find_quick_offer(p_casual boolean, p_ranked boolean)
returns setof public.online_matches
language sql security invoker stable as $$
  select *
  from public.online_matches m
  where m.status = 'waiting'
    and m.is_quick_match
    and m.guest_id is null
    and m.invited_id is null
    and m.host_id <> auth.uid()
    and ((m.ranked and p_ranked) or (not m.ranked and p_casual))
    and m.created_at > now() - interval '10 minutes'
  order by m.created_at asc
  limit 10;
$$;

grant execute on function public.find_quick_offer(boolean, boolean) to authenticated;

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
-- contadores próprios da ranqueada, separados de profiles.matches/wins (que somam
-- QUALQUER partida — IA, local, LAN). Sem isso o placar geral mostrava até quem
-- nunca jogou ranqueada, só porque tinha jogado contra a IA alguma vez
alter table public.profiles add column if not exists ranked_matches integer not null default 0;
alter table public.profiles add column if not exists ranked_wins integer not null default 0;

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
  select p.id, p.username, p.insignia, p.avatar, p.ranked_rating, p.ranked_matches, p.ranked_wins
  from public.profiles p
  where p.ranked_matches > 0
  -- desempate: pontos, depois vitórias, depois aproveitamento (vitórias/partidas)
  -- e, por último, quem chegou primeiro àquela pontuação fica na frente
  order by p.ranked_rating desc, p.ranked_wins desc,
           (p.ranked_wins::numeric / greatest(p.ranked_matches, 1)) desc, p.updated_at asc
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
  -- mesmo desempate do placar geral (ver leaderboard_overall)
  order by s.points desc, s.wins desc,
           (s.wins::numeric / greatest(s.matches, 1)) desc, s.updated_at asc
  limit p_limit;
$$;

revoke all on function public.leaderboard_season(integer) from public;
grant execute on function public.leaderboard_season(integer) to authenticated;

-- posição e pontuação do próprio comandante, mesmo fora do top da lista —
-- sem isso quem não está entre os melhores nunca saberia a própria colocação.
-- Mesmo desempate dos placares (pontos, vitórias, aproveitamento, antiguidade).
-- "position" é palavra reservada do SQL (usada em SUBSTRING ... FROM ... FOR
-- ... e afins), por isso vai entre aspas em todo lugar que aparece como nome
create or replace function public.my_rank(p_season boolean)
returns table ("position" bigint, rating integer)
language sql security definer set search_path = public stable as $$
  select "position", rating from (
    select
      row_number() over (
        order by p.ranked_rating desc, p.ranked_wins desc,
                 (p.ranked_wins::numeric / greatest(p.ranked_matches, 1)) desc, p.updated_at asc
      ) as "position",
      p.ranked_rating as rating,
      p.id
    from public.profiles p
    where p.ranked_matches > 0 and not p_season
  ) ranked
  where ranked.id = auth.uid()
  union all
  select "position", rating from (
    select
      row_number() over (
        order by s.points desc, s.wins desc,
                 (s.wins::numeric / greatest(s.matches, 1)) desc, s.updated_at asc
      ) as "position",
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
-- flags host_result_recorded/guest_result_recorded travam contra reenvio).
--
-- Critérios a partir da 0.14.0 (antes o servidor confiava no cliente por inteiro
-- e dava de 20 a 50 pontos fixos por vitória — foi assim que um convidado ficou
-- com uma vitória que na verdade era derrota):
-- 1. Quem decide o vencedor é o servidor: o PRIMEIRO relato da partida fixa o
--    vencedor em `winner_id`. Um relato posterior do outro lado que discorde (os
--    dois dizendo que venceram, ou os dois que perderam) não troca nada — esse
--    lado é pontuado pelo resultado já gravado e a sala fica marcada em
--    `result_conflict`, para conferência manual.
-- 2. Pontos estilo Elo, pela força do adversário:
--      esperado = 1 / (1 + 10^((adversário - eu) / 400))
--      base     = round(32 * (resultado - esperado))   (resultado: 1 vitória, 0 derrota)
--    A conta roda duas vezes: com os pontos de temporada dos dois (placar da
--    temporada) e com o ranked_rating dos dois (placar geral).
-- 3. Bônus de desempenho só para quem venceu, com teto de 30% do ganho:
--      bônus = least(round(|base| * 0.3), round(precisão * 0.08) + navios_restantes)
--    Vitória vale no mínimo +5. Derrota não desconta por desempenho, só tem um
--    alívio pequeno: até 3 pontos a menos de perda (round(precisão * 0.03)),
--    sem nunca virar ganho.
-- 4. Abandono é derrota cheia: quem desiste (ou estoura os 60s em segundo
--    plano) agora também relata, com p_won = false e precisão 0 — sem alívio.
-- 5. Desempate dos placares: pontos, vitórias, aproveitamento, antiguidade.
--
-- Devolve uma linha com o que a tela de resultado mostra no bloco de ranking:
-- pontos desta partida (e como foram compostos: base Elo + bônus/alívio),
-- pontos e posição na temporada depois da partida, partidas e vitórias da
-- temporada e `accepted` — falso quando este lado já tinha relatado antes
-- (nada mudou nesta chamada, só devolve o estado atual).
alter table public.online_matches add column if not exists winner_id uuid references auth.users(id) on delete set null;
alter table public.online_matches add column if not exists result_conflict boolean not null default false;

-- o formato de retorno mudou (void -> tabela): create or replace não troca isso
drop function if exists public.record_ranked_result(uuid, boolean, integer, integer);
create function public.record_ranked_result(
  p_match_id uuid, p_won boolean, p_accuracy integer, p_ships_left integer
)
returns table (
  points_delta integer, season_points integer, season_position integer, season_name text,
  season_matches integer, season_wins integer, accepted boolean,
  base_points integer, bonus_points integer
)
language plpgsql security definer set search_path = public as $$
declare
  m public.online_matches%rowtype;
  me uuid := auth.uid();
  opp uuid;
  is_host boolean;
  won boolean;
  acc integer;
  ships integer;
  my_username text;
  szn text;
  szn_label text;
  my_season integer;
  opp_season integer;
  my_overall integer;
  opp_overall integer;
  base_s integer;
  bonus_s integer;
  delta_s integer;
  base_o integer;
  bonus_o integer;
  delta_o integer;
begin
  -- "for update" enfileira os dois relatos da mesma sala: os dois lados terminam
  -- quase juntos, e sem a trava os dois podiam achar winner_id vazio ao mesmo tempo
  select * into m from public.online_matches om where om.id = p_match_id for update;
  if m.id is null or not m.ranked or m.guest_id is null then
    return;
  end if;

  is_host := (m.host_id = me);
  if not is_host and m.guest_id <> me then
    return;
  end if;
  opp := case when is_host then m.guest_id else m.host_id end;
  my_username := case when is_host then m.host_name else m.guest_name end;

  select c.season_key, c.name into szn, szn_label from public.current_season() c;
  season_name := szn_label;

  -- ranqueada só para quem aderiu à temporada (passe gratuito ou do Almirante,
  -- ver supabase/season.sql) — o app já barra antes; aqui é a garantia
  if to_regclass('public.season_passes') is not null
     and not exists (select 1 from public.season_passes sp where sp.user_id = me and sp.season_key = szn) then
    return;
  end if;

  if (case when is_host then m.host_result_recorded else m.guest_result_recorded end) then
    -- relato repetido (reabriu a tela, retomou o app): só devolve o estado atual
    select s.points, s.matches, s.wins into season_points, season_matches, season_wins
      from public.ranked_season_stats s where s.user_id = me and s.season_key = szn;
    select x.pos::integer into season_position from (
      select s.user_id, row_number() over (
        order by s.points desc, s.wins desc,
                 (s.wins::numeric / greatest(s.matches, 1)) desc, s.updated_at asc
      ) as pos
      from public.ranked_season_stats s where s.season_key = szn
    ) x where x.user_id = me;
    points_delta := 0;
    base_points := 0;
    bonus_points := 0;
    accepted := false;
    return next;
    return;
  end if;

  -- 1. o primeiro relato fixa o vencedor; o segundo só confere
  if m.winner_id is null then
    m.winner_id := case when p_won then me else opp end;
    update public.online_matches om set winner_id = m.winner_id where om.id = p_match_id;
  elsif (m.winner_id = me) <> p_won then
    update public.online_matches om set result_conflict = true where om.id = p_match_id;
  end if;
  won := (m.winner_id = me);

  if is_host then
    update public.online_matches om set host_result_recorded = true where om.id = p_match_id;
  else
    update public.online_matches om set guest_result_recorded = true where om.id = p_match_id;
  end if;

  acc := greatest(0, least(100, coalesce(p_accuracy, 0)));
  ships := greatest(0, least(5, coalesce(p_ships_left, 0)));

  -- 2. força dos dois lados — quem ainda não jogou a temporada começa em 1000
  select s.points into my_season from public.ranked_season_stats s where s.user_id = me and s.season_key = szn;
  select s.points into opp_season from public.ranked_season_stats s where s.user_id = opp and s.season_key = szn;
  select p.ranked_rating into my_overall from public.profiles p where p.id = me;
  select p.ranked_rating into opp_overall from public.profiles p where p.id = opp;
  my_season := coalesce(my_season, 1000);
  opp_season := coalesce(opp_season, 1000);
  my_overall := coalesce(my_overall, 1000);
  opp_overall := coalesce(opp_overall, 1000);

  base_s := round(32 * ((case when won then 1 else 0 end)
    - 1.0 / (1 + power(10.0, (opp_season - my_season) / 400.0))));
  base_o := round(32 * ((case when won then 1 else 0 end)
    - 1.0 / (1 + power(10.0, (opp_overall - my_overall) / 400.0))));

  -- 3. bônus de desempenho (vencedor, teto de 30%) ou alívio pequeno (perdedor)
  if won then
    bonus_s := least(round(abs(base_s) * 0.3), round(acc * 0.08) + ships);
    bonus_o := least(round(abs(base_o) * 0.3), round(acc * 0.08) + ships);
    delta_s := greatest(5, base_s + bonus_s);
    delta_o := greatest(5, base_o + bonus_o);
  else
    bonus_s := least(3, round(acc * 0.03));
    bonus_o := bonus_s;
    delta_s := least(0, base_s + bonus_s);
    delta_o := least(0, base_o + bonus_o);
  end if;
  -- o que aparece como bônus é o que de fato entrou além da base (inclui o piso
  -- de +5 na vitória e o corte do alívio que viraria ganho na derrota)
  bonus_s := delta_s - base_s;

  update public.profiles p
    set ranked_rating = greatest(0, p.ranked_rating + delta_o),
        ranked_matches = p.ranked_matches + 1,
        ranked_wins = p.ranked_wins + case when won then 1 else 0 end
    where p.id = me;

  insert into public.ranked_season_stats as s (user_id, season_key, username, points, matches, wins)
  values (me, szn, my_username, greatest(0, 1000 + delta_s), 1, case when won then 1 else 0 end)
  on conflict (user_id, season_key) do update
    set points = greatest(0, s.points + delta_s),
        matches = s.matches + 1,
        wins = s.wins + case when won then 1 else 0 end,
        username = excluded.username,
        updated_at = now();

  select s.points, s.matches, s.wins into season_points, season_matches, season_wins
    from public.ranked_season_stats s where s.user_id = me and s.season_key = szn;
  select x.pos::integer into season_position from (
    select s.user_id, row_number() over (
      order by s.points desc, s.wins desc,
               (s.wins::numeric / greatest(s.matches, 1)) desc, s.updated_at asc
    ) as pos
    from public.ranked_season_stats s where s.season_key = szn
  ) x where x.user_id = me;

  points_delta := delta_s;
  base_points := base_s;
  bonus_points := bonus_s;
  accepted := true;
  return next;
end $$;

revoke all on function public.record_ranked_result(uuid, boolean, integer, integer) from public;
grant execute on function public.record_ranked_result(uuid, boolean, integer, integer) to authenticated;

-- ------------------------------------------------------------------ reparo manual (NÃO RODAR sem conferir)
--
-- Partida conhecida com resultado errado (antes da 0.14.0): o convidado
-- "porteiro do john" ficou com uma vitória e 1050 pontos na temporada, quando na
-- verdade perdeu (o bug do lado ENEMY corrigido na 0.43.0 do CHANGELOG). Exemplo
-- de correção, comentado de propósito — conferir ids e valores no SQL Editor
-- antes de descomentar. Pela regra antiga a derrota ficaria entre -15 e -5; aqui
-- uso -15 (derrota cheia): temporada 1050 -> 985, geral -65 (desfaz +50, aplica -15).
--
-- begin;
-- -- 1. ache a sala e confira quem foi o anfitrião (o vencedor de verdade)
-- select id, host_id, host_name, guest_id, guest_name, created_at
--   from public.online_matches
--  where ranked and guest_name = 'porteiro do john'
--  order by created_at desc;
--
-- -- 2. temporada: tira a vitória e troca o +50 por -15
-- update public.ranked_season_stats s
--    set points = 985, wins = greatest(0, s.wins - 1), updated_at = now()
--   from public.current_season() c
--  where s.season_key = c.season_key
--    and s.username = 'porteiro do john' and s.points = 1050;
--
-- -- 3. geral: mesmo ajuste em profiles
-- update public.profiles
--    set ranked_rating = greatest(0, ranked_rating - 65), ranked_wins = greatest(0, ranked_wins - 1)
--  where username = 'porteiro do john';
--
-- -- 4. grava o vencedor certo na sala (troque <match_id> pelo id do passo 1)
-- update public.online_matches set winner_id = host_id, result_conflict = true where id = '<match_id>';
-- commit;
