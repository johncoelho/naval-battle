-- Naval Battle · segurança entre jogadores (#58, 0.42.0): denunciar, bloquear e filtro de nome de guerra.
-- Migração `player_safety`. Rode depois de online.sql, quick_offer.sql, presence.sql, push.sql, economy.sql
-- e test_accounts.sql (este arquivo SUBSTITUI definições deles; as antigas têm a marca "substituída em
-- safety.sql (#58)" e a ordem de instalação do zero continua valendo).
--
-- Regras de ouro deste arquivo:
-- * Tudo no servidor e compatível com o app antigo: o nome ofensivo vindo de app antigo NUNCA dá erro
--   (o app antigo grava a carreira inteira num upsert só; um erro ali perderia XP e estatísticas) —
--   o servidor mantém o nome anterior. O bloqueio vale para o app antigo porque está na política de leitura
--   das salas e nas funções, não no cliente.
-- * O ranking NÃO esconde bloqueado (o placar é público e a colocação é a mesma para todos); só o
--   suspenso (profiles.suspended_until) sai do ranking.
-- * O e-mail de quem recebe o aviso de denúncia fica só no banco (app_config.moderation_notify_emails).
-- * Constantes e listas: a lista de termos abaixo tem a MESMA lista em app/game/NameFilter.kt; o teste
--   NameTermsParityTest compara as duas (os marcadores TERMS/ALLOW abaixo são lidos por ele).

-- ------------------------------------------------------------------ view antiga

-- public.leaderboard (schema.sql) nunca foi usada pelo app (nem por versão publicada) e, por ser view,
-- ignora o RLS de profiles (expunha username/xp de todos, inclusive de conta de teste).
drop view if exists public.leaderboard;

-- ------------------------------------------------------------------ tabelas

alter table public.profiles add column if not exists suspended_until timestamptz;

create table if not exists public.player_blocks (
  blocker_id uuid        not null references auth.users(id) on delete cascade,
  blocked_id uuid        not null references auth.users(id) on delete cascade,
  created_at timestamptz not null default now(),
  primary key (blocker_id, blocked_id),
  check (blocker_id <> blocked_id)
);
create index if not exists player_blocks_blocked_idx on public.player_blocks (blocked_id);
alter table public.player_blocks enable row level security;
drop policy if exists "bloqueio: ler os proprios" on public.player_blocks;
create policy "bloqueio: ler os proprios"
  on public.player_blocks for select
  using (blocker_id = auth.uid());
-- escrita só por RPC (block_player / unblock_player)
revoke all on public.player_blocks from anon, authenticated;
grant select on public.player_blocks to authenticated;

create table if not exists public.player_reports (
  id            uuid        primary key default gen_random_uuid(),
  reporter_id   uuid        references auth.users(id) on delete set null,
  reported_id   uuid        not null references auth.users(id) on delete cascade,
  reason        text        not null check (reason in ('name', 'abuse', 'cheat', 'other')),
  context       text        not null check (context in ('ranking', 'search', 'friends', 'match', 'result')),
  match_id      uuid,
  reported_name text,        -- cópia do nome na hora da denúncia (ele pode mudar)
  status        text        not null default 'open' check (status in ('open', 'dismissed', 'name_reset', 'suspended')),
  created_at    timestamptz not null default now(),
  reviewed_at   timestamptz
);
create index if not exists player_reports_reported_idx on public.player_reports (reported_id, created_at desc);
create index if not exists player_reports_reporter_idx on public.player_reports (reporter_id, created_at desc);
alter table public.player_reports enable row level security;
-- sem política: ninguém lê pelo app; o John revisa pelo SQL (reports_pending / review_report)
revoke all on public.player_reports from anon, authenticated;

create table if not exists public.name_terms (
  term text primary key,                       -- já normalizado (normalize_name)
  lang text not null check (lang in ('pt', 'en', 'es', 'all')),
  kind text not null check (kind in ('sub', 'word'))
);
alter table public.name_terms enable row level security;
revoke all on public.name_terms from anon, authenticated;

create table if not exists public.name_allow (
  term text primary key                        -- já normalizado
);
alter table public.name_allow enable row level security;
revoke all on public.name_allow from anon, authenticated;

create table if not exists public.name_resets (
  id         bigint      generated always as identity primary key,
  user_id    uuid        not null references auth.users(id) on delete cascade,
  old_name   text,
  reason     text        not null,              -- migration | reports | review
  created_at timestamptz not null default now(),
  seen_at    timestamptz
);
create index if not exists name_resets_user_idx on public.name_resets (user_id, created_at desc);
alter table public.name_resets enable row level security;
drop policy if exists "troca de nome: ler as proprias" on public.name_resets;
create policy "troca de nome: ler as proprias"
  on public.name_resets for select
  using (user_id = auth.uid());
revoke all on public.name_resets from anon, authenticated;
grant select on public.name_resets to authenticated;

create table if not exists public.moderation_push_log (
  sent_at timestamptz not null default now()
);
alter table public.moderation_push_log enable row level security;
revoke all on public.moderation_push_log from anon, authenticated;

-- números ajustáveis e e-mails do aviso (o valor real fica só no banco: o repositório é público)
insert into public.app_config (key, value) values
  ('reports_daily_cap', '10'),               -- denúncias por conta e dia
  ('reports_auto_reset_name', '3'),          -- denunciantes distintos (7 dias) que trocam o nome
  ('moderation_push_minutes', '60'),         -- intervalo mínimo entre avisos ao John
  ('moderation_notify_emails', '')           -- e-mails (minúsculos, separados por vírgula)
on conflict (key) do nothing;
-- copia a lista do John de ads_test_emails (só entre linhas do banco; nada de e-mail neste arquivo)
update public.app_config
   set value = (select a.value from public.app_config a where a.key = 'ads_test_emails')
 where key = 'moderation_notify_emails' and coalesce(value, '') = '';

-- ------------------------------------------------------------------ normalização e filtro de nome

-- minúsculas, acento -> letra base e leet -> letra (0 o, 1 i, 3 e, 4 a, 5 s, 7 t, 8 b, @ a, $ s, ! i, | i).
-- app/game/NameFilter.kt faz exatamente os mesmos passos.
create or replace function public.name_fold(p_text text)
returns text language sql immutable as $$
  select translate(
    translate(lower(coalesce(p_text, '')),
      'áàâãäåéèêëíìîïóòôõöúùûüçñýÿ',
      'aaaaaaeeeeiiiiooooouuuucnyy'),
    '0134578@$!|', 'oieastbasii');
$$;

-- forma compacta: só a-z e sem letra repetida em sequência
create or replace function public.normalize_name(p_text text)
returns text language sql immutable as $$
  select regexp_replace(regexp_replace(public.name_fold(p_text), '[^a-z]', '', 'g'), '(.)\1+', '\1', 'g');
$$;

-- palavras separadas (cada uma sem letra repetida)
create or replace function public.name_tokens(p_text text)
returns text[] language sql immutable as $$
  select coalesce(array(
    select regexp_replace(w, '(.)\1+', '\1', 'g')
    from unnest(regexp_split_to_array(
           trim(regexp_replace(public.name_fold(p_text), '[^a-z]+', ' ', 'g')), ' ')) w
    where w <> ''), '{}'::text[]);
$$;

-- Lista de termos (pt, en, es). Termo normalizado com 5 letras ou mais vale como pedaço do nome (sub);
-- menor que isso só vale como palavra inteira (word), para não cair no problema de Scunthorpe.
-- A mesma lista está em NameFilter.TERMS.
-- TERMS-BEGIN
insert into public.name_terms (term, lang, kind)
select public.normalize_name(t.term), t.lang,
       case when length(public.normalize_name(t.term)) >= 5 then 'sub' else 'word' end
from (values
  ('caralho','pt'), ('buceta','pt'), ('bucetao','pt'), ('putaria','pt'), ('putinha','pt'), ('puta','all'),
  ('puto','pt'), ('merda','pt'), ('fodase','pt'), ('fodido','pt'), ('foder','pt'), ('foda','pt'),
  ('vadia','pt'), ('viado','pt'), ('viadinho','pt'), ('arrombado','pt'), ('cuzao','pt'), ('escroto','pt'),
  ('otario','pt'), ('vagabundo','pt'), ('vagabunda','pt'), ('punheta','pt'), ('xoxota','pt'),
  ('xereca','pt'), ('cacete','pt'), ('porra','pt'), ('bosta','pt'), ('carai','pt'), ('filhodaputa','pt'),
  ('fdp','pt'), ('pqp','pt'), ('estupro','pt'), ('estuprador','pt'), ('pedofilo','pt'), ('pedofilia','pt'),
  ('nazista','pt'), ('boiola','pt'), ('traveco','pt'), ('sapatao','pt'), ('retardado','pt'),
  ('prostituta','pt'), ('mongoloide','pt'), ('crioulo','pt'),
  ('fuck','en'), ('fucker','en'), ('fucking','en'), ('fucked','en'), ('motherfucker','en'), ('fuckyou','en'),
  ('shit','en'), ('shitty','en'), ('bullshit','en'), ('shithead','en'), ('bitch','en'), ('asshole','en'),
  ('bastard','en'), ('dickhead','en'), ('dick','en'), ('cunt','en'), ('whore','en'), ('slut','en'),
  ('slutty','en'), ('nigger','en'), ('nigga','en'), ('faggot','en'), ('fag','en'), ('retard','en'),
  ('rapist','en'), ('rape','en'), ('pedophile','en'), ('paedophile','en'), ('molester','en'),
  ('cocksucker','en'), ('blowjob','en'), ('dildo','en'), ('penis','en'), ('vagina','en'), ('pussy','en'),
  ('cock','en'), ('twat','en'), ('jizz','en'), ('tits','en'), ('porn','en'), ('porno','all'),
  ('mierda','es'), ('cabron','es'), ('pendejo','es'), ('joder','es'), ('gilipollas','es'), ('marica','es'),
  ('maricon','es'), ('culero','es'), ('verga','es'), ('chingar','es'), ('chingada','es'), ('chinga','es'),
  ('hijueputa','es'), ('hijodeputa','es'), ('malparido','es'), ('pelotudo','es'),
  ('pajero','es'), ('sudaca','es'), ('culiao','es'),
  ('hitler','all'), ('nazi','all')
) as t(term, lang)
on conflict (term) do nothing;
-- TERMS-END

-- Exceções (palavras legítimas que contêm um termo). A mesma lista está em NameFilter.ALLOW.
-- ALLOW-BEGIN
insert into public.name_allow (term)
select public.normalize_name(t.term)
from (values
  ('enviado'), ('desviado'), ('invadia'), ('evadia'), ('nigeria'), ('nigerian'),
  ('vergara'), ('converga'), ('diverga'), ('fagote'), ('retardant'), ('therapist'), ('therapists'),
  ('evergarden'), ('notario'), ('notarios')
) as t(term)
on conflict (term) do nothing;
-- ALLOW-END

create or replace function public.name_is_offensive(p_text text)
returns boolean
language plpgsql stable security definer set search_path = public as $$
declare
  c text := public.normalize_name(p_text);
  toks text[] := public.name_tokens(p_text);
  a text;
begin
  if c = '' then return false; end if;
  -- retira as exceções (a mais longa primeiro) antes de procurar os termos
  for a in select n.term from public.name_allow n order by length(n.term) desc, n.term loop
    c := replace(c, a, '');
  end loop;
  toks := array(select x from unnest(toks) x where not exists (select 1 from public.name_allow n where n.term = x));
  return exists (
    select 1 from public.name_terms t
    where (t.kind = 'sub' and position(t.term in c) > 0)
       or (t.kind = 'word' and t.term = any (toks))
  );
end $$;
revoke all on function public.name_fold(text) from public, anon, authenticated;
revoke all on function public.normalize_name(text) from public, anon, authenticated;
revoke all on function public.name_tokens(text) from public, anon, authenticated;
revoke all on function public.name_is_offensive(text) from public, anon, authenticated;

-- nome padrão: Comandante- + 6 primeiros caracteres do id (mais longo se colidir no unique)
create or replace function public.default_name(p_uid uuid)
returns text
language plpgsql stable security definer set search_path = public as $$
declare
  base text := replace(p_uid::text, '-', '');
  n integer := 6;
  v text;
begin
  loop
    v := 'Comandante-' || substr(base, 1, n);
    exit when n >= 32 or not exists (
      select 1 from public.profiles p where lower(p.username) = lower(v) and p.id <> p_uid);
    n := n + 1;
  end loop;
  return v;
end $$;
revoke all on function public.default_name(uuid) from public, anon, authenticated;

-- existe bloqueio em qualquer direção entre os dois, ou um deles está suspenso. Só responde sobre quem
-- chama (um dos dois precisa ser auth.uid()), para não vazar o bloqueio de terceiros. Anon também
-- executa porque as políticas de leitura das salas a chamam (auth.uid() nulo = falso).
create or replace function public.blocked_between(a uuid, b uuid)
returns boolean
language sql stable security definer set search_path = public as $$
  select auth.uid() is not null
     and (a = auth.uid() or b = auth.uid())
     and (
       exists (select 1 from public.player_blocks pb
                where (pb.blocker_id = a and pb.blocked_id = b) or (pb.blocker_id = b and pb.blocked_id = a))
       or exists (select 1 from public.profiles p
                   where p.id in (a, b) and p.suspended_until is not null and p.suspended_until > now())
     );
$$;
revoke all on function public.blocked_between(uuid, uuid) from public;
grant execute on function public.blocked_between(uuid, uuid) to anon, authenticated;

-- ------------------------------------------------------------------ gatilhos de nome

-- profiles: nome ofensivo NUNCA dá erro. No update mantém o nome anterior; no insert usa o padrão (ou, se
-- o upsert do app antigo cair no ON CONFLICT, o nome que a conta já tem).
create or replace function public.filter_profile_name()
returns trigger
language plpgsql security definer set search_path = public as $$
declare v_old text;
begin
  -- o nome trocado à força por denúncia/revisão não volta por 30 dias (o app antigo, ou o mesmo aparelho,
  -- reenviaria o nome velho no próximo upsert da carreira); sem erro, o nome atual fica
  if tg_op = 'UPDATE' and new.username is distinct from old.username and exists (
       select 1 from public.name_resets r
        where r.user_id = new.id and r.reason <> 'migration' and r.old_name = new.username
          and r.created_at > now() - interval '30 days') then
    new.username := old.username;
    return new;
  end if;
  if new.username is null or not public.name_is_offensive(new.username) then
    return new;
  end if;
  if tg_op = 'UPDATE' then
    new.username := old.username;
  else
    select p.username into v_old from public.profiles p where p.id = new.id;
    new.username := coalesce(v_old, public.default_name(new.id));
  end if;
  return new;
end $$;
revoke all on function public.filter_profile_name() from public, anon, authenticated;

drop trigger if exists profiles_name_filter on public.profiles;
create trigger profiles_name_filter
  before insert or update of username on public.profiles
  for each row execute function public.filter_profile_name();

-- o cliente não grava a própria suspensão (invoker: current_user é o papel de quem gravou)
create or replace function public.guard_profile_suspended()
returns trigger language plpgsql as $$
begin
  if current_user in ('anon', 'authenticated') then
    if tg_op = 'INSERT' then
      new.suspended_until := null;
    else
      new.suspended_until := old.suspended_until;
    end if;
  end if;
  return new;
end $$;

drop trigger if exists profiles_guard_suspended on public.profiles;
create trigger profiles_guard_suspended
  before insert or update on public.profiles
  for each row execute function public.guard_profile_suspended();

-- cópias do nome escritas pelo cliente: nome ofensivo vira o nome do perfil do dono do id
create or replace function public.filter_match_names()
returns trigger
language plpgsql security definer set search_path = public as $$
begin
  if new.host_name is not null and public.name_is_offensive(new.host_name) then
    new.host_name := coalesce((select p.username from public.profiles p where p.id = new.host_id), 'Comandante');
  end if;
  if new.guest_name is not null and public.name_is_offensive(new.guest_name) then
    new.guest_name := coalesce((select p.username from public.profiles p where p.id = new.guest_id), 'Comandante');
  end if;
  return new;
end $$;
revoke all on function public.filter_match_names() from public, anon, authenticated;
drop trigger if exists online_matches_name_filter on public.online_matches;
create trigger online_matches_name_filter
  before insert or update of host_name, guest_name on public.online_matches
  for each row execute function public.filter_match_names();

create or replace function public.filter_friendship_names()
returns trigger
language plpgsql security definer set search_path = public as $$
begin
  if new.requester_username is not null and public.name_is_offensive(new.requester_username) then
    new.requester_username := coalesce((select p.username from public.profiles p where p.id = new.requester_id), '?');
  end if;
  if new.addressee_username is not null and public.name_is_offensive(new.addressee_username) then
    new.addressee_username := coalesce((select p.username from public.profiles p where p.id = new.addressee_id), '?');
  end if;
  return new;
end $$;
revoke all on function public.filter_friendship_names() from public, anon, authenticated;
drop trigger if exists friendships_name_filter on public.friendships;
create trigger friendships_name_filter
  before insert or update of requester_username, addressee_username on public.friendships
  for each row execute function public.filter_friendship_names();

create or replace function public.filter_season_name()
returns trigger
language plpgsql security definer set search_path = public as $$
begin
  if new.username is not null and public.name_is_offensive(new.username) then
    new.username := coalesce((select p.username from public.profiles p where p.id = new.user_id), 'Comandante');
  end if;
  return new;
end $$;
revoke all on function public.filter_season_name() from public, anon, authenticated;
drop trigger if exists ranked_season_stats_name_filter on public.ranked_season_stats;
create trigger ranked_season_stats_name_filter
  before insert or update of username on public.ranked_season_stats
  for each row execute function public.filter_season_name();

-- conta nova (substitui schema.sql): nome do Google ofensivo vira o nome padrão
create or replace function public.handle_new_user()
returns trigger language plpgsql security definer set search_path = public as $$
declare
  wanted text := coalesce(
    new.raw_user_meta_data->>'username',
    new.raw_user_meta_data->>'full_name',
    split_part(coalesce(new.email, 'comandante'), '@', 1)
  );
begin
  if public.name_is_offensive(wanted) then
    wanted := public.default_name(new.id);
  end if;
  begin
    insert into public.profiles (id, email, username)
    values (new.id, new.email, wanted)
    on conflict (id) do nothing;
  exception when unique_violation then
    -- nome de usuário já tomado: entra com sufixo em vez de barrar o cadastro
    insert into public.profiles (id, email, username)
    values (new.id, new.email, wanted || '-' || substr(new.id::text, 1, 4))
    on conflict (id) do nothing;
  end;
  return new;
end $$;

-- ------------------------------------------------------------------ bloqueio nas salas e amizades

-- substitui "sala online: ler" (online.sql): a sala aberta some para quem está bloqueado com o anfitrião
-- (nos dois sentidos) ou para quem está suspenso; vale também para o app antigo, que lê a tabela direto
drop policy if exists "sala online: ler" on public.online_matches;
create policy "sala online: ler"
  on public.online_matches for select
  using (
    auth.uid() = host_id
    or auth.uid() = guest_id
    or auth.uid() = invited_id
    or (status = 'waiting'
        and (not is_quick_match or public.is_test_user(host_id) = public.my_is_test())
        and not public.blocked_between(host_id, auth.uid()))
  );

-- substitui "sala online: criar": não sai convite (nem o push) para bloqueado
drop policy if exists "sala online: criar" on public.online_matches;
create policy "sala online: criar"
  on public.online_matches for insert
  with check (
    auth.uid() = host_id
    and (invited_id is null or not public.blocked_between(host_id, invited_id))
  );

-- substitui "amizade: pedir": não sai pedido (nem o push) para bloqueado
drop policy if exists "amizade: pedir" on public.friendships;
create policy "amizade: pedir"
  on public.friendships for insert
  with check (
    auth.uid() = requester_id
    and not public.blocked_between(requester_id, addressee_id)
  );

-- substitui join_online_match (online.sql)
create or replace function public.join_online_match(p_match_id uuid, p_guest_name text)
returns setof public.online_matches
language plpgsql security invoker as $$
begin
  return query
    update public.online_matches
    set guest_id = auth.uid(), guest_name = p_guest_name, status = 'active'
    where id = p_match_id and guest_id is null and status = 'waiting'
      and (invited_id is null or invited_id = auth.uid())
      -- partida rápida: conta de teste só com conta de teste (#23)
      and (not is_quick_match or public.is_test_user(host_id) = public.my_is_test())
      -- bloqueio nos dois sentidos e suspensão (#58)
      and not public.blocked_between(host_id, auth.uid())
    returning *;
end $$;
revoke all on function public.join_online_match(uuid, text) from public, anon;
grant execute on function public.join_online_match(uuid, text) to authenticated;

-- substitui find_quick_offer (quick_offer.sql)
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
    -- conta de teste só recebe sala de conta de teste, e vice-versa (#23)
    and public.is_test_user(m.host_id) = public.is_test_user(me)
    -- bloqueio nos dois sentidos e suspensão (#58)
    and not public.blocked_between(m.host_id, me)
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
revoke all on function public.find_quick_offer(boolean, boolean) from public, anon;
grant execute on function public.find_quick_offer(boolean, boolean) to authenticated;

-- substitui search_commander (online.sql): sem bloqueados nos dois sentidos e sem suspensos
create or replace function public.search_commander(query text)
returns table (id uuid, username text, avatar text)
language sql security definer set search_path = public stable as $$
  select p.id, p.username, p.avatar
  from public.profiles p
  where p.username ilike query || '%'
    and p.id <> auth.uid()
    and not public.blocked_between(p.id, auth.uid())
  order by p.username
  limit 10;
$$;
revoke all on function public.search_commander(text) from public, anon;
grant execute on function public.search_commander(text) to authenticated;

-- ------------------------------------------------------------------ ranking: só o suspenso sai

-- substituem leaderboard_overall, leaderboard_season, my_rank e season_trophies (online.sql): mesma assinatura
-- e mesmo retorno, com o filtro de suspensão. O bloqueado continua no placar.
create or replace function public.leaderboard_overall(p_limit integer default 50)
returns table (user_id uuid, username text, insignia text, avatar text, rating integer, matches integer, wins integer)
language sql security definer set search_path = public stable as $$
  select p.id, p.username, p.insignia, p.avatar, p.ranked_rating, p.ranked_matches, p.ranked_wins
  from public.profiles p
  where p.ranked_matches > 0
    and not coalesce(p.is_test, false)  -- conta de teste fora do placar (#23)
    and not coalesce(p.suspended_until > now(), false)  -- suspenso fora do placar (#58)
  order by p.ranked_rating desc, p.ranked_wins desc,
           (p.ranked_wins::numeric / greatest(p.ranked_matches, 1)) desc, p.updated_at asc
  limit p_limit;
$$;

create or replace function public.leaderboard_season(p_limit integer default 50)
returns table (user_id uuid, username text, insignia text, avatar text, points integer, matches integer, wins integer)
language sql security definer set search_path = public stable as $$
  select s.user_id, s.username, p.insignia, p.avatar, s.points, s.matches, s.wins
  from public.ranked_season_stats s
  join public.current_season() c on s.season_key = c.season_key
  left join public.profiles p on p.id = s.user_id
  where not coalesce(p.is_test, false)
    and not coalesce(p.suspended_until > now(), false)
  order by s.points desc, s.wins desc,
           (s.wins::numeric / greatest(s.matches, 1)) desc, s.updated_at asc
  limit p_limit;
$$;

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
      and not coalesce(p.is_test, false)
      and not coalesce(p.suspended_until > now(), false)
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
    from public.ranked_season_stats s
    join public.current_season() c on s.season_key = c.season_key
    left join public.profiles p on p.id = s.user_id
    where p_season and not coalesce(p.is_test, false)
      and not coalesce(p.suspended_until > now(), false)
  ) ranked
  where ranked.user_id = auth.uid();
$$;

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
    where not coalesce(p.is_test, false)  -- conta de teste fora das medalhas (#23)
      and not coalesce(p.suspended_until > now(), false)  -- suspenso fora (#58)
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

-- ------------------------------------------------------------------ RPCs do app

-- copia o nome novo para onde o cliente guarda cópia: temporada (a corrente, ou todas) , amizades e salas abertas
create or replace function public.sync_username_copies(p_uid uuid, p_name text, p_all_seasons boolean)
returns void
language plpgsql security definer set search_path = public as $$
begin
  update public.ranked_season_stats s set username = p_name
   where s.user_id = p_uid and s.username is distinct from p_name
     and (p_all_seasons or s.season_key = (select c.season_key from public.current_season() c));
  update public.friendships set requester_username = p_name
   where requester_id = p_uid and requester_username is distinct from p_name;
  update public.friendships set addressee_username = p_name
   where addressee_id = p_uid and addressee_username is distinct from p_name;
  update public.online_matches set host_name = p_name
   where host_id = p_uid and status = 'waiting' and host_name is distinct from p_name;
end $$;
revoke all on function public.sync_username_copies(uuid, text, boolean) from public, anon, authenticated;

-- troca o nome do jogador à força, guarda o anterior para auditoria e para o aviso no app
create or replace function public.force_reset_name(p_uid uuid, p_reason text)
returns text
language plpgsql security definer set search_path = public as $$
declare
  v_old text;
  v_new text := public.default_name(p_uid);
begin
  select username into v_old from public.profiles where id = p_uid;
  if not found then return null; end if;
  if v_old is not distinct from v_new then return v_new; end if;
  update public.profiles set username = v_new where id = p_uid;
  insert into public.name_resets (user_id, old_name, reason) values (p_uid, v_old, p_reason);
  perform public.sync_username_copies(p_uid, v_new, true);
  return v_new;
end $$;
revoke all on function public.force_reset_name(uuid, text) from public, anon, authenticated;

-- ok | invalid | offensive | taken
create or replace function public.set_username(p_name text)
returns text
language plpgsql security definer set search_path = public as $$
declare
  me uuid := auth.uid();
  v text := trim(coalesce(p_name, ''));
begin
  if me is null or not exists (select 1 from public.profiles where id = me) then return 'invalid'; end if;
  if char_length(v) < 3 or char_length(v) > 18 then return 'invalid'; end if;
  if public.name_is_offensive(v) then return 'offensive'; end if;
  if exists (select 1 from public.profiles p where lower(p.username) = lower(v) and p.id <> me) then
    return 'taken';
  end if;
  -- nome que acabou de ser trocado à força: indisponível por 30 dias (ver filter_profile_name)
  if exists (select 1 from public.name_resets r
              where r.user_id = me and r.reason <> 'migration' and lower(r.old_name) = lower(v)
                and r.created_at > now() - interval '30 days') then
    return 'taken';
  end if;
  begin
    update public.profiles set username = v where id = me;
  exception when unique_violation then
    return 'taken';
  end;
  perform public.sync_username_copies(me, v, false);
  return 'ok';
end $$;
revoke all on function public.set_username(text) from public, anon;
grant execute on function public.set_username(text) to authenticated;

-- ok | self | invalid
create or replace function public.block_player(p_user uuid)
returns text
language plpgsql security definer set search_path = public as $$
declare
  me uuid := auth.uid();
begin
  if me is null or p_user is null then return 'invalid'; end if;
  if me = p_user then return 'self'; end if;
  if not exists (select 1 from public.profiles where id = p_user) then return 'invalid'; end if;
  perform pg_advisory_xact_lock(hashtextextended(
    'safety:' || least(me, p_user)::text || greatest(me, p_user)::text, 0));
  insert into public.player_blocks (blocker_id, blocked_id) values (me, p_user)
  on conflict do nothing;
  delete from public.friendships
   where (requester_id = me and addressee_id = p_user) or (requester_id = p_user and addressee_id = me);
  update public.online_matches set status = 'abandoned'
   where status = 'waiting'
     and ((host_id = me and invited_id = p_user) or (host_id = p_user and invited_id = me));
  update public.online_matches set offered_to = null, offered_at = null
   where status = 'waiting'
     and ((host_id = me and offered_to = p_user) or (host_id = p_user and offered_to = me));
  return 'ok';
end $$;
revoke all on function public.block_player(uuid) from public, anon;
grant execute on function public.block_player(uuid) to authenticated;

create or replace function public.unblock_player(p_user uuid)
returns text
language plpgsql security definer set search_path = public as $$
begin
  if auth.uid() is null or p_user is null then return 'invalid'; end if;
  delete from public.player_blocks where blocker_id = auth.uid() and blocked_id = p_user;
  return 'ok';
end $$;
revoke all on function public.unblock_player(uuid) from public, anon;
grant execute on function public.unblock_player(uuid) to authenticated;

create or replace function public.my_blocks()
returns table (user_id uuid, username text, avatar text, created_at timestamptz)
language sql security definer set search_path = public stable as $$
  select b.blocked_id, p.username, p.avatar, b.created_at
  from public.player_blocks b
  left join public.profiles p on p.id = b.blocked_id
  where b.blocker_id = auth.uid()
  order by b.created_at desc;
$$;
revoke all on function public.my_blocks() from public, anon;
grant execute on function public.my_blocks() to authenticated;

-- ok | already | limit | self | invalid
create or replace function public.report_player(
  p_user uuid, p_reason text, p_context text, p_match uuid default null
)
returns text
language plpgsql security definer set search_path = public as $$
declare
  me uuid := auth.uid();
  v_name text;
  v_match uuid;
  v_distinct integer;
  v_min integer;
  r record;
begin
  if me is null or p_user is null
     or p_reason is null or p_reason not in ('name', 'abuse', 'cheat', 'other')
     or p_context is null or p_context not in ('ranking', 'search', 'friends', 'match', 'result') then
    return 'invalid';
  end if;
  if me = p_user then return 'self'; end if;
  select username into v_name from public.profiles where id = p_user;
  if not found then return 'invalid'; end if;

  perform pg_advisory_xact_lock(hashtextextended('report:' || me::text, 0));
  if exists (select 1 from public.player_reports x
              where x.reporter_id = me and x.reported_id = p_user and x.reason = p_reason
                and (x.created_at at time zone 'America/Sao_Paulo')::date = public.brt_today()) then
    return 'already';
  end if;
  if (select count(*) from public.player_reports x
       where x.reporter_id = me
         and (x.created_at at time zone 'America/Sao_Paulo')::date = public.brt_today())
     >= public.config_int('reports_daily_cap', 10) then
    return 'limit';
  end if;

  -- a sala só vale se os dois estiveram nela
  select m.id into v_match from public.online_matches m
   where m.id = p_match
     and ((m.host_id = me and m.guest_id = p_user) or (m.guest_id = me and m.host_id = p_user));

  insert into public.player_reports (reporter_id, reported_id, reason, context, match_id, reported_name)
  values (me, p_user, p_reason, p_context, v_match, v_name);

  -- ação automática: 3 denunciantes distintos (fora conta de teste) em 7 dias contra o nome atual
  if p_reason = 'name' then
    select count(distinct x.reporter_id) into v_distinct
      from public.player_reports x
      join public.profiles rp on rp.id = x.reporter_id
     where x.reported_id = p_user and x.reason = 'name' and x.status = 'open'
       and x.reported_name is not distinct from v_name
       and x.created_at > now() - interval '7 days'
       and not coalesce(rp.is_test, false);
    if v_distinct >= public.config_int('reports_auto_reset_name', 3) then
      perform public.force_reset_name(p_user, 'reports');
      update public.player_reports set status = 'name_reset', reviewed_at = now()
       where reported_id = p_user and reason = 'name' and status = 'open';
    end if;
  end if;

  -- aviso ao John: no máximo 1 a cada moderation_push_minutes; texto fixo (push aparece na tela bloqueada)
  begin
    perform pg_advisory_xact_lock(hashtextextended('moderation_push', 0));
    v_min := public.config_int('moderation_push_minutes', 60);
    if not exists (select 1 from public.moderation_push_log l
                    where l.sent_at > now() - make_interval(mins => v_min)) then
      insert into public.moderation_push_log default values;
      for r in
        select p.id from public.profiles p
         where lower(trim(p.email)) = any (string_to_array(replace(lower(coalesce(
                 (select value from public.app_config where key = 'moderation_notify_emails'), '')), ' ', ''), ','))
      loop
        perform public.queue_push(r.id, 'moderation', 'Nova denúncia no Naval Battle',
          'Há denúncias de jogadores para revisar.');
      end loop;
    end if;
  exception when others then
    null;  -- o aviso nunca derruba a denúncia
  end;
  return 'ok';
end $$;
revoke all on function public.report_player(uuid, text, text, uuid) from public, anon;
grant execute on function public.report_player(uuid, text, text, uuid) to authenticated;

-- aviso único "seu nome foi trocado"
create or replace function public.my_name_reset()
returns table (reason text, created_at timestamptz)
language sql security definer set search_path = public stable as $$
  select r.reason, r.created_at from public.name_resets r
   where r.user_id = auth.uid() and r.seen_at is null
   order by r.created_at desc limit 1;
$$;
revoke all on function public.my_name_reset() from public, anon;
grant execute on function public.my_name_reset() to authenticated;

create or replace function public.name_reset_ack()
returns text
language plpgsql security definer set search_path = public as $$
begin
  update public.name_resets set seen_at = now() where user_id = auth.uid() and seen_at is null;
  return 'ok';
end $$;
revoke all on function public.name_reset_ack() from public, anon;
grant execute on function public.name_reset_ack() to authenticated;

-- ------------------------------------------------------------------ moderação (só administrador, pelo SQL do Supabase)

create or replace function public.reports_pending()
returns table (
  reported_id uuid, current_name text, reported_name text, total bigint,
  n_name bigint, n_abuse bigint, n_cheat bigint, n_other bigint,
  last_at timestamptz, suspended_until timestamptz
)
language sql security definer set search_path = public stable as $$
  select r.reported_id, p.username, (array_agg(r.reported_name order by r.created_at desc))[1],
         count(*),
         count(*) filter (where r.reason = 'name'), count(*) filter (where r.reason = 'abuse'),
         count(*) filter (where r.reason = 'cheat'), count(*) filter (where r.reason = 'other'),
         max(r.created_at), p.suspended_until
  from public.player_reports r
  left join public.profiles p on p.id = r.reported_id
  where r.status = 'open'
  group by r.reported_id, p.username, p.suspended_until
  order by max(r.created_at) desc;
$$;
revoke all on function public.reports_pending() from public, anon, authenticated;

-- dismiss | reset_name | suspend (p_days dias). Fecha todas as denúncias abertas daquele jogador.
create or replace function public.review_report(p_reported uuid, p_action text, p_days integer default 7)
returns text
language plpgsql security definer set search_path = public as $$
declare v_status text;
begin
  if p_action = 'dismiss' then
    v_status := 'dismissed';
  elsif p_action = 'reset_name' then
    perform public.force_reset_name(p_reported, 'review');
    v_status := 'name_reset';
  elsif p_action = 'suspend' then
    update public.profiles set suspended_until = now() + make_interval(days => greatest(coalesce(p_days, 7), 1))
     where id = p_reported;
    v_status := 'suspended';
  else
    return 'invalid';
  end if;
  update public.player_reports set status = v_status, reviewed_at = now()
   where reported_id = p_reported and status = 'open';
  return 'ok';
end $$;
revoke all on function public.review_report(uuid, text, integer) from public, anon, authenticated;

-- Retenção de 12 meses (política de privacidade, #57): apaga denúncias e trocas de nome antigas. Não há pg_cron:
-- a rodada diária da esteira chama `select * from public.purge_moderation();` (como o push_check_daily).
create or replace function public.purge_moderation()
returns table (reports_deleted bigint, resets_deleted bigint)
language plpgsql security definer set search_path = public as $$
declare r bigint; n bigint;
begin
  delete from public.player_reports where created_at < now() - interval '12 months';
  get diagnostics r = row_count;
  delete from public.name_resets where created_at < now() - interval '12 months';
  get diagnostics n = row_count;
  return query select r, n;
end $$;
revoke all on function public.purge_moderation() from public, anon, authenticated;

-- ------------------------------------------------------------------ nomes atuais que violam

-- troca por Comandante-xxxxxx (o nome antigo fica em name_resets, reason 'migration') e atualiza as cópias
do $$
declare r record;
begin
  for r in select p.id, p.username from public.profiles p
            where p.username is not null and public.name_is_offensive(p.username)
  loop
    perform public.force_reset_name(r.id, 'migration');
  end loop;
end $$;
