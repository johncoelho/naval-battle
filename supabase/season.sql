-- Naval Battle · passe de temporada e recompensas de fim de temporada
-- Rode no SQL Editor depois de online.sql e economy.sql. Idempotente.
--
-- Quatro temporadas por ano, uma por estação (hemisfério sul, ver current_season
-- em online.sql). Para jogar ranqueada o comandante precisa ADERIR à temporada:
--   · Passe Gratuito — só participa da ranqueada, sem recompensa extra;
--   · Passe do Almirante — pago em dobrões: camuflagem exclusiva da estação,
--     dobrões extras e milhas náuticas bônus. Quem aderiu de graça pode fazer
--     upgrade depois, por um preço maior que o da adesão.
-- Os dobrões ainda vivem no aparelho: o app desconta o preço e entrega os dobrões
-- do passe; aqui ficam o registro do passe, as milhas e a regra de quem pode jogar.

insert into public.app_config (key, value) values
  ('season_pass_price', '1200'),
  ('season_pass_upgrade_price', '1600'),
  ('season_pass_doubloons', '500'),
  ('season_pass_miles', '20'),
  ('season_reward_1_doubloons', '3000'),
  ('season_reward_1_miles', '30'),
  ('season_reward_2_doubloons', '2000'),
  ('season_reward_2_miles', '20'),
  ('season_reward_3_doubloons', '1000'),
  ('season_reward_3_miles', '10'),
  ('season_reward_play_doubloons', '150'),
  ('season_reward_play_miles', '5')
on conflict (key) do nothing;

-- ------------------------------------------------------------------ passes

create table if not exists public.season_passes (
  user_id     uuid        not null references auth.users(id) on delete cascade,
  season_key  text        not null,
  tier        text        not null check (tier in ('free', 'premium')),
  price_paid  integer     not null default 0,
  created_at  timestamptz not null default now(),
  upgraded_at timestamptz,
  primary key (user_id, season_key)
);

alter table public.season_passes enable row level security;

drop policy if exists "passes: ler os proprios" on public.season_passes;
create policy "passes: ler os proprios"
  on public.season_passes for select
  using (auth.uid() = user_id);

-- quem já jogou ranqueada nesta temporada antes do passe existir não perde o
-- direito: entra com o passe gratuito
insert into public.season_passes (user_id, season_key, tier)
select s.user_id, s.season_key, 'free'
  from public.ranked_season_stats s, public.current_season() c
 where s.season_key = c.season_key
on conflict do nothing;

-- Situação do passe na temporada corrente: tier nulo = ainda não aderiu.
create or replace function public.season_pass_status()
returns table (season_key text, season_name text, tier text, entry_price integer,
               upgrade_price integer, pass_doubloons integer, pass_miles integer)
language plpgsql security definer set search_path = public as $$
declare
  szn text;
  nm text;
begin
  if auth.uid() is null then
    raise exception 'season_auth';
  end if;
  select c.season_key, c.name into szn, nm from public.current_season() c;
  return query select
    szn,
    nm,
    (select p.tier from public.season_passes p where p.user_id = auth.uid() and p.season_key = szn),
    public.config_int('season_pass_price', 1200),
    public.config_int('season_pass_upgrade_price', 1600),
    public.config_int('season_pass_doubloons', 500),
    public.config_int('season_pass_miles', 20);
end $$;

revoke all on function public.season_pass_status() from public;
grant execute on function public.season_pass_status() to authenticated;

-- Adesão (p_tier 'free' ou 'premium') ou upgrade de free para premium. Devolve o
-- tier final e as milhas bônus que entraram (0 quando nada mudou). O preço que
-- vale é o de adesão quando entra direto no premium, e o de upgrade quando sobe.
create or replace function public.join_season(p_tier text)
returns table (tier text, miles_bonus integer, price integer, miles integer)
language plpgsql security definer set search_path = public as $$
declare
  szn text;
  cur text;
  bonus integer := 0;
  cost integer := 0;
  bal integer;
begin
  if auth.uid() is null then
    raise exception 'season_auth';
  end if;
  if p_tier not in ('free', 'premium') then
    raise exception 'season_tier';
  end if;
  select c.season_key into szn from public.current_season() c;
  perform pg_advisory_xact_lock(hashtext('season_pass:' || auth.uid()::text));
  select p.tier into cur from public.season_passes p where p.user_id = auth.uid() and p.season_key = szn;

  if cur is null then
    if p_tier = 'premium' then
      cost := public.config_int('season_pass_price', 1200);
      bonus := public.config_int('season_pass_miles', 20);
    end if;
    insert into public.season_passes (user_id, season_key, tier, price_paid)
    values (auth.uid(), szn, p_tier, cost);
  elsif cur = 'free' and p_tier = 'premium' then
    cost := public.config_int('season_pass_upgrade_price', 1600);
    bonus := public.config_int('season_pass_miles', 20);
    update public.season_passes
       set tier = 'premium', price_paid = price_paid + cost, upgraded_at = now()
     where user_id = auth.uid() and season_key = szn;
  end if;

  bal := public.miles_refresh(auth.uid());
  if bonus > 0 then
    -- milhas do passe passam do teto normal: são prêmio, não recarga
    update public.profiles set nautical_miles = nautical_miles + bonus where id = auth.uid()
      returning nautical_miles into bal;
  end if;

  return query select
    (select p.tier from public.season_passes p where p.user_id = auth.uid() and p.season_key = szn),
    bonus, cost, bal;
end $$;

revoke all on function public.join_season(text) from public;
grant execute on function public.join_season(text) to authenticated;

-- ------------------------------------------------------------------ fim de temporada

-- Uma linha por comandante e temporada encerrada: trava o resgate em uma vez só.
create table if not exists public.season_rewards (
  user_id    uuid        not null references auth.users(id) on delete cascade,
  season_key text        not null,
  position   integer     not null,
  doubloons  integer     not null,
  miles      integer     not null,
  claimed_at timestamptz not null default now(),
  primary key (user_id, season_key)
);

alter table public.season_rewards enable row level security;

drop policy if exists "recompensas: ler as proprias" on public.season_rewards;
create policy "recompensas: ler as proprias"
  on public.season_rewards for select
  using (auth.uid() = user_id);

-- "2026-primavera" -> "Primavera"
create or replace function public.season_label(p_key text)
returns text language sql immutable as $$
  select case split_part(p_key, '-', 2)
    when 'verao' then 'Verão'
    when 'outono' then 'Outono'
    when 'inverno' then 'Inverno'
    else 'Primavera'
  end;
$$;

-- Resgata o resultado da última temporada encerrada em que o comandante jogou
-- ranqueada e ainda não viu o aviso. Os 3 primeiros têm prêmio especial; os
-- demais que jogaram levam um prêmio de participação. Milhas entram aqui; os
-- dobrões voltam para o app creditar. Nada para resgatar = nenhuma linha.
create or replace function public.claim_season_end()
returns table (season_key text, season_name text, position integer, total_players integer,
               points integer, matches integer, wins integer, doubloons integer, miles integer)
language plpgsql security definer set search_path = public as $$
declare
  szn text;
  target text;
  pos integer;
  total integer;
  st record;
  d integer;
  mi integer;
begin
  if auth.uid() is null then
    raise exception 'season_auth';
  end if;
  select c.season_key into szn from public.current_season() c;

  select s.season_key into target
    from public.ranked_season_stats s
   where s.user_id = auth.uid() and s.season_key <> szn and s.matches > 0
     and not exists (select 1 from public.season_rewards r where r.user_id = auth.uid() and r.season_key = s.season_key)
   order by s.season_key desc
   limit 1;
  if target is null then
    return;
  end if;

  select x.pos, x.total into pos, total from (
    select s.user_id,
           row_number() over (order by s.points desc, s.wins desc,
                              (s.wins::numeric / greatest(s.matches, 1)) desc, s.updated_at asc) as pos,
           count(*) over () as total
      from public.ranked_season_stats s
     where s.season_key = target and s.matches > 0
  ) x where x.user_id = auth.uid();

  select s.points, s.matches, s.wins into st
    from public.ranked_season_stats s where s.user_id = auth.uid() and s.season_key = target;

  d := case pos
         when 1 then public.config_int('season_reward_1_doubloons', 3000)
         when 2 then public.config_int('season_reward_2_doubloons', 2000)
         when 3 then public.config_int('season_reward_3_doubloons', 1000)
         else public.config_int('season_reward_play_doubloons', 150)
       end;
  mi := case pos
          when 1 then public.config_int('season_reward_1_miles', 30)
          when 2 then public.config_int('season_reward_2_miles', 20)
          when 3 then public.config_int('season_reward_3_miles', 10)
          else public.config_int('season_reward_play_miles', 5)
        end;

  insert into public.season_rewards (user_id, season_key, position, doubloons, miles)
  values (auth.uid(), target, pos, d, mi)
  on conflict do nothing;
  if not found then
    return;
  end if;

  perform public.miles_refresh(auth.uid());
  update public.profiles set nautical_miles = nautical_miles + mi where id = auth.uid();

  return query select target, public.season_label(target), pos, total::integer,
                      st.points, st.matches, st.wins, d, mi;
end $$;

revoke all on function public.claim_season_end() from public;
grant execute on function public.claim_season_end() to authenticated;
