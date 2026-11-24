-- Naval Battle · bandeira de nacionalidade no perfil (issue #124, 0.48.6)
-- Rode no SQL Editor depois de schema.sql, online.sql e safety.sql. Idempotente. Migração no banco:
-- `profiles_country_code` (mesmo conteúdo). Aplicar ANTES de publicar o app 0.48.6: o app novo grava
-- `country_code` no upsert da carreira, e contra um banco sem a coluna o PostgREST recusa o upsert inteiro.
-- Teste que desfaz tudo: supabase/tests/country_ensaio.sql (cole este arquivo e o ensaio na MESMA execução; o
-- raise no fim cancela a transação inteira, DDL inclusive).
--
-- O que é: o jogador escolhe a bandeira do país numa lista FECHADA (sem texto livre), opcional; nulo = sem bandeira,
-- que é o padrão ("Prefiro não mostrar"). Aparece para adversários ("Adversário encontrado") e no placar.
--
-- Regras:
--  * Coluna nova `profiles.country_code`, nula por padrão, com check de duas letras maiúsculas.
--  * Lista fechada em `public.country_codes()`, IGUAL à `Countries.all` do app (commonMain/game/Countries.kt;
--    o `CountriesTest` confere as duas). Só membros da ONU e os observadores (Palestina, Santa Sé).
--  * Gatilho `profiles_country_filter`: código fora da lista NUNCA dá erro (o upsert da carreira inteiro cairia).
--    No update mantém o anterior; no insert usa o que a conta já tem (o upsert do app cai no ON CONFLICT e o valor
--    proposto vira o EXCLUDED). Texto vazio = "Prefiro não mostrar" = nulo (o iPhone não manda nulo no JSON).
--  * Quem grava: só o dono, pelas políticas de `profiles` que já existem (schema.sql). Nada novo de RLS.
--  * App antigo: não manda o campo no upsert, então o ON CONFLICT não o toca e a bandeira gravada fica.
--  * RPCs públicas ganham a coluna no fim do retorno (drop + create, porque o retorno mudou): `opponent_profile`
--    (online.sql), `leaderboard_overall` e `leaderboard_season` (safety.sql). O app antigo lê por nome e ignora o
--    campo novo. As três voltam com os mesmos grants.
--
-- Desfazer na produção (a ordem importa: as RPCs antigas não leem a coluna, então voltam antes do drop dela):
--   rode de novo as definições de opponent_profile (online.sql) e leaderboard_overall/leaderboard_season
--   (safety.sql) com `drop function` antes de cada uma, e depois:
--   drop trigger if exists profiles_country_filter on public.profiles;
--   drop function if exists public.filter_profile_country();
--   drop function if exists public.country_codes();
--   alter table public.profiles drop column if exists country_code;

-- ------------------------------------------------------------------ coluna

alter table public.profiles add column if not exists country_code text;

do $$
begin
  if not exists (select 1 from pg_constraint where conname = 'profiles_country_code_format') then
    alter table public.profiles add constraint profiles_country_code_format
      check (country_code is null or country_code ~ '^[A-Z]{2}$');
  end if;
end $$;

-- ------------------------------------------------------------------ lista fechada

create or replace function public.country_codes()
returns text[]
language sql immutable set search_path = public as $$
  select array[
    'AE','AO','AR','AT','AU','BE','BO','BR','CA','CH','CL','CN','CO','CR','CU','CV','CZ','DE','DK','DO',
    'EC','EG','ES','FI','FR','GB','GH','GR','GT','GW','HN','HR','HU','ID','IE','IL','IN','IT','JM','JP',
    'KE','KR','MA','MX','MY','MZ','NG','NI','NL','NO','NZ','PA','PE','PH','PK','PL','PS','PT','PY','RO',
    'RS','RU','SA','SE','SG','SN','ST','SV','TH','TR','UA','US','UY','VA','VE','VN','ZA'
  ]::text[];
$$;
revoke all on function public.country_codes() from public, anon, authenticated;

-- ------------------------------------------------------------------ gatilho

create or replace function public.filter_profile_country()
returns trigger
language plpgsql security definer set search_path = public as $$
declare v_old text;
begin
  if new.country_code = '' then
    new.country_code := null;  -- "Prefiro não mostrar"
    return new;
  end if;
  if new.country_code is null or new.country_code = any(public.country_codes()) then
    return new;
  end if;
  -- fora da lista (minúsculo, três letras, inventado): ignora sem erro
  if tg_op = 'UPDATE' then
    new.country_code := old.country_code;
  else
    select p.country_code into v_old from public.profiles p where p.id = new.id;
    new.country_code := v_old;
  end if;
  return new;
end $$;
revoke all on function public.filter_profile_country() from public, anon, authenticated;

drop trigger if exists profiles_country_filter on public.profiles;
create trigger profiles_country_filter
  before insert or update of country_code on public.profiles
  for each row execute function public.filter_profile_country();

-- ------------------------------------------------------------------ RPCs públicas com a bandeira

-- substitui opponent_profile (online.sql): mesmo filtro, coluna nova no fim
drop function if exists public.opponent_profile(uuid);
create function public.opponent_profile(p_user_id uuid)
returns table (username text, insignia text, avatar text, xp integer, ranked_rating integer, country_code text)
language sql security definer set search_path = public stable as $$
  select p.username, p.insignia, p.avatar, p.xp, p.ranked_rating, p.country_code
  from public.profiles p
  where p.id = p_user_id;
$$;
revoke all on function public.opponent_profile(uuid) from public, anon;
grant execute on function public.opponent_profile(uuid) to authenticated;

-- substitui leaderboard_overall (safety.sql): mesmo filtro e ordem, coluna nova no fim
drop function if exists public.leaderboard_overall(integer);
create function public.leaderboard_overall(p_limit integer default 50)
returns table (user_id uuid, username text, insignia text, avatar text, rating integer, matches integer, wins integer,
               country_code text)
language sql security definer set search_path = public stable as $$
  select p.id, p.username, p.insignia, p.avatar, p.ranked_rating, p.ranked_matches, p.ranked_wins, p.country_code
  from public.profiles p
  where p.ranked_matches > 0
    and not coalesce(p.is_test, false)  -- conta de teste fora do placar (#23)
    and not coalesce(p.suspended_until > now(), false)  -- suspenso fora do placar (#58)
  order by p.ranked_rating desc, p.ranked_wins desc,
           (p.ranked_wins::numeric / greatest(p.ranked_matches, 1)) desc, p.updated_at asc
  limit p_limit;
$$;
revoke all on function public.leaderboard_overall(integer) from public, anon;
grant execute on function public.leaderboard_overall(integer) to authenticated;

-- substitui leaderboard_season (safety.sql): mesmo filtro e ordem, coluna nova no fim
drop function if exists public.leaderboard_season(integer);
create function public.leaderboard_season(p_limit integer default 50)
returns table (user_id uuid, username text, insignia text, avatar text, points integer, matches integer, wins integer,
               country_code text)
language sql security definer set search_path = public stable as $$
  select s.user_id, s.username, p.insignia, p.avatar, s.points, s.matches, s.wins, p.country_code
  from public.ranked_season_stats s
  join public.current_season() c on s.season_key = c.season_key
  left join public.profiles p on p.id = s.user_id
  where not coalesce(p.is_test, false)
    and not coalesce(p.suspended_until > now(), false)
  order by s.points desc, s.wins desc,
           (s.wins::numeric / greatest(s.matches, 1)) desc, s.updated_at asc
  limit p_limit;
$$;
revoke all on function public.leaderboard_season(integer) from public, anon;
grant execute on function public.leaderboard_season(integer) to authenticated;
