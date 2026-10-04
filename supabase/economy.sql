-- Naval Battle · economia: loja de dobrões simulada (beta testers) e milhas náuticas
-- Rode no SQL Editor depois de schema.sql, online.sql e feedback.sql.
-- Idempotente: rodar de novo não duplica nada nem zera saldos.
--
-- O dia vira à meia-noite de Brasília para as duas regras (limite de compra e
-- recarga das milhas) — o relógio é sempre o do servidor, nunca o do aparelho.

create or replace function public.brt_today()
returns date language sql stable as $$
  select (now() at time zone 'America/Sao_Paulo')::date;
$$;

-- segundos até a próxima meia-noite de Brasília (contador da tela)
create or replace function public.brt_seconds_to_midnight()
returns integer language sql stable as $$
  select greatest(0, extract(epoch from (
    ((public.brt_today() + 1)::timestamp at time zone 'America/Sao_Paulo') - now()
  ))::integer);
$$;

-- números ajustáveis sem lançar versão nova (lidos pelas funções abaixo)
insert into public.app_config (key, value) values
  ('beta_store_daily_cents', '5000'),
  ('miles_daily', '10'),
  ('miles_cap', '50'),
  ('miles_ranked_win', '5'),
  ('miles_casual_win', '1'),
  ('miles_pack_size', '5'),
  ('miles_pack_price', '100')
on conflict (key) do nothing;

create or replace function public.config_int(p_key text, p_default integer)
returns integer language sql stable security definer set search_path = public as $$
  select coalesce((select value::integer from public.app_config where key = p_key), p_default);
$$;

-- ------------------------------------------------------------------ loja simulada

-- Cada "compra" de teste fica registrada: é a base do limite diário e, mais à
-- frente, o mesmo histórico recebe as compras de verdade pela Google Play.
create table if not exists public.beta_purchases (
  id          uuid        primary key default gen_random_uuid(),
  user_id     uuid        not null references auth.users(id) on delete cascade,
  pack        text        not null,
  price_cents integer     not null,
  doubloons   integer     not null,
  day         date        not null,
  created_at  timestamptz not null default now()
);

alter table public.beta_purchases enable row level security;

drop policy if exists "compras beta: ler as proprias" on public.beta_purchases;
create policy "compras beta: ler as proprias"
  on public.beta_purchases for select
  using (auth.uid() = user_id);

-- pacotes: código → (preço em centavos, dobrões)
create or replace function public.beta_pack(p_pack text, out price_cents integer, out doubloons integer)
language sql immutable as $$
  select v.price_cents, v.doubloons
  from (values
    ('pouch', 490, 500),
    ('chest', 990, 1100),
    ('strongbox', 2490, 3000),
    ('treasure', 4990, 6500)
  ) as v(code, price_cents, doubloons)
  where v.code = p_pack;
$$;

create or replace function public.beta_store_status()
returns table (eligible boolean, spent_cents integer, limit_cents integer, resets_in_seconds integer)
language sql stable security definer set search_path = public as $$
  select
    exists (select 1 from public.user_badges b where b.user_id = auth.uid() and b.badge_code = 'beta_tester'),
    coalesce((select sum(p.price_cents)::integer from public.beta_purchases p
              where p.user_id = auth.uid() and p.day = public.brt_today()), 0),
    public.config_int('beta_store_daily_cents', 5000),
    public.brt_seconds_to_midnight();
$$;

revoke all on function public.beta_store_status() from public;
grant execute on function public.beta_store_status() to authenticated;

-- Compra simulada: confere badge e limite do dia e registra. O app só credita os
-- dobrões que voltam daqui (mesmo padrão do claim_feedback).
create or replace function public.buy_beta_pack(p_pack text)
returns table (doubloons integer, spent_cents integer, limit_cents integer)
language plpgsql security definer set search_path = public as $$
declare
  pk record;
  spent integer;
  lim integer;
begin
  if auth.uid() is null then
    raise exception 'store_auth';
  end if;
  if not exists (select 1 from public.user_badges b where b.user_id = auth.uid() and b.badge_code = 'beta_tester') then
    raise exception 'store_not_beta';
  end if;
  select * into pk from public.beta_pack(p_pack);
  if pk.price_cents is null then
    raise exception 'store_pack';
  end if;

  -- trava por usuário: dois toques rápidos não passam juntos pelo limite
  perform pg_advisory_xact_lock(hashtext('beta_store:' || auth.uid()::text));

  lim := public.config_int('beta_store_daily_cents', 5000);
  select coalesce(sum(p.price_cents), 0)::integer into spent
    from public.beta_purchases p where p.user_id = auth.uid() and p.day = public.brt_today();
  if spent + pk.price_cents > lim then
    raise exception 'store_limit';
  end if;

  insert into public.beta_purchases (user_id, pack, price_cents, doubloons, day)
  values (auth.uid(), p_pack, pk.price_cents, pk.doubloons, public.brt_today());

  return query select pk.doubloons, spent + pk.price_cents, lim;
end $$;

revoke all on function public.buy_beta_pack(text) from public;
grant execute on function public.buy_beta_pack(text) to authenticated;

-- ------------------------------------------------------------------ milhas náuticas

-- Saldo de milhas no perfil. `miles_day` é o último dia (Brasília) em que a
-- recarga diária foi aplicada: a recarga completa até `miles_daily` sem passar
-- disso; o que veio de vitória ou compra pode passar, até `miles_cap`.
alter table public.profiles add column if not exists nautical_miles integer not null default 10;
alter table public.profiles add column if not exists miles_day date;

-- uma milha gasta e uma recompensa por partida e por comandante (revanche na mesma
-- sala não cobra nem paga de novo)
create table if not exists public.mile_ledger (
  user_id    uuid        not null references auth.users(id) on delete cascade,
  match_id   uuid        not null,
  kind       text        not null check (kind in ('spend', 'reward')),
  amount     integer     not null,
  created_at timestamptz not null default now(),
  primary key (user_id, match_id, kind)
);

alter table public.mile_ledger enable row level security;

drop policy if exists "milhas: ler as proprias" on public.mile_ledger;
create policy "milhas: ler as proprias"
  on public.mile_ledger for select
  using (auth.uid() = user_id);

-- aplica a recarga do dia, se ainda não aplicada, e devolve o saldo
create or replace function public.miles_refresh(p_user uuid)
returns integer language plpgsql security definer set search_path = public as $$
declare
  cur integer;
  d date;
begin
  select nautical_miles, miles_day into cur, d from public.profiles where id = p_user for update;
  if cur is null then
    return 0;
  end if;
  if d is null or d < public.brt_today() then
    cur := greatest(cur, public.config_int('miles_daily', 10));
    update public.profiles set nautical_miles = cur, miles_day = public.brt_today() where id = p_user;
  end if;
  return cur;
end $$;

revoke all on function public.miles_refresh(uuid) from public, anon, authenticated;

create or replace function public.miles_status()
returns table (miles integer, daily integer, cap integer, resets_in_seconds integer,
               pack_size integer, pack_price integer, ranked_win integer, casual_win integer)
language plpgsql security definer set search_path = public as $$
begin
  if auth.uid() is null then
    raise exception 'miles_auth';
  end if;
  return query select
    public.miles_refresh(auth.uid()),
    public.config_int('miles_daily', 10),
    public.config_int('miles_cap', 50),
    public.brt_seconds_to_midnight(),
    public.config_int('miles_pack_size', 5),
    public.config_int('miles_pack_price', 100),
    public.config_int('miles_ranked_win', 5),
    public.config_int('miles_casual_win', 1);
end $$;

revoke all on function public.miles_status() from public;
grant execute on function public.miles_status() to authenticated;

-- Gasta 1 milha ao começar uma partida online. Só quem está na sala gasta, uma vez
-- por sala. Sem milha: erro `miles_empty` (o app já barra antes, isto é a garantia).
drop function if exists public.spend_mile(uuid);
create or replace function public.spend_mile(p_match_id uuid)
returns table (miles integer) language plpgsql security definer set search_path = public as $$
declare
  m public.online_matches%rowtype;
  cur integer;
  inserted integer;
begin
  if auth.uid() is null then
    raise exception 'miles_auth';
  end if;
  select * into m from public.online_matches where id = p_match_id;
  if m.id is null or (m.host_id <> auth.uid() and coalesce(m.guest_id, '00000000-0000-0000-0000-000000000000'::uuid) <> auth.uid()) then
    raise exception 'miles_match';
  end if;
  cur := public.miles_refresh(auth.uid());

  insert into public.mile_ledger (user_id, match_id, kind, amount)
  values (auth.uid(), p_match_id, 'spend', -1)
  on conflict do nothing;
  get diagnostics inserted = row_count;
  if inserted = 0 then
    return query select cur;
    return;
  end if;

  if cur < 1 then
    delete from public.mile_ledger where user_id = auth.uid() and match_id = p_match_id and kind = 'spend';
    raise exception 'miles_empty';
  end if;
  update public.profiles set nautical_miles = nautical_miles - 1 where id = auth.uid()
    returning nautical_miles into cur;
  return query select cur;
    return;
end $$;

revoke all on function public.spend_mile(uuid) from public;
grant execute on function public.spend_mile(uuid) to authenticated;

-- Recompensa de vitória: ranqueada só vale se o servidor registrou este comandante
-- como vencedor (winner_id, gravado por record_ranked_result); casual confia no
-- relato, mas paga 1 milha só uma vez por sala — e a partida custou 1, então
-- jogar casual contra si mesmo não gera milha.
drop function if exists public.award_win_miles(uuid);
create or replace function public.award_win_miles(p_match_id uuid)
returns table (miles integer) language plpgsql security definer set search_path = public as $$
declare
  m public.online_matches%rowtype;
  gain integer;
  cur integer;
  inserted integer;
begin
  if auth.uid() is null then
    raise exception 'miles_auth';
  end if;
  select * into m from public.online_matches where id = p_match_id;
  if m.id is null or (m.host_id <> auth.uid() and coalesce(m.guest_id, '00000000-0000-0000-0000-000000000000'::uuid) <> auth.uid()) then
    raise exception 'miles_match';
  end if;
  cur := public.miles_refresh(auth.uid());

  if m.ranked then
    if m.winner_id is distinct from auth.uid() then
      return query select cur;
    return;
    end if;
    gain := public.config_int('miles_ranked_win', 5);
  else
    gain := public.config_int('miles_casual_win', 1);
  end if;

  insert into public.mile_ledger (user_id, match_id, kind, amount)
  values (auth.uid(), p_match_id, 'reward', gain)
  on conflict do nothing;
  get diagnostics inserted = row_count;
  if inserted = 0 then
    return query select cur;
    return;
  end if;

  update public.profiles
     set nautical_miles = least(public.config_int('miles_cap', 50), nautical_miles + gain)
   where id = auth.uid()
  returning nautical_miles into cur;
  return query select cur;
    return;
end $$;

revoke all on function public.award_win_miles(uuid) from public;
grant execute on function public.award_win_miles(uuid) to authenticated;

-- Compra de milhas com dobrões: o app desconta os dobrões (o saldo de dobrões ainda
-- vive no aparelho) e pede ao servidor os pacotes; aqui só se respeita o teto.
drop function if exists public.buy_miles(integer);
create or replace function public.buy_miles(p_packs integer default 1)
returns table (miles integer) language plpgsql security definer set search_path = public as $$
declare
  cur integer;
  cap integer;
  extra integer;
begin
  if auth.uid() is null then
    raise exception 'miles_auth';
  end if;
  if p_packs is null or p_packs < 1 or p_packs > 10 then
    raise exception 'miles_packs';
  end if;
  cur := public.miles_refresh(auth.uid());
  cap := public.config_int('miles_cap', 50);
  extra := p_packs * public.config_int('miles_pack_size', 5);
  if cur + extra > cap then
    raise exception 'miles_cap';
  end if;
  update public.profiles set nautical_miles = cur + extra where id = auth.uid()
    returning nautical_miles into cur;
  return query select cur;
    return;
end $$;

revoke all on function public.buy_miles(integer) from public;
grant execute on function public.buy_miles(integer) to authenticated;
