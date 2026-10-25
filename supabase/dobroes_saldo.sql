-- Naval Battle · saldo de dobrões no servidor (#34, parte "e" do #15, 0.34.8).
-- Rode no SQL Editor depois de economy.sql, daily.sql, season.sql, online.sql, dobroes.sql e ads.sql.
-- Idempotente. As funções que já existiam e mudam (daily_checkin, claim_daily_challenge, join_season,
-- claim_season_end, buy_miles, claim_feedback, ad_reward_start) continuam nos arquivos do domínio
-- delas; a migração `dobroes_saldo` leva este arquivo mais o texto idêntico dessas funções.
--
-- IDEIA CENTRAL: a carteira abre POR CONTA, e nunca fecha.
--  * Conta sem carteira (legado): tudo igual a antes. O app 0.34.4 a 0.34.7 grava `credits`, as RPCs
--    devolvem o valor e o aparelho soma. Esta migração, sozinha, NÃO abre nenhuma carteira: é inerte
--    para todos os apps instalados.
--  * Conta com carteira: `profiles.credits` é o saldo de verdade, alterado SÓ por RPC. Cada movimento
--    fica no razão `dobroes_ledger`. O cliente perde o direito de gravar `credits`, `owned` e
--    `fleets` (o gatilho IGNORA a gravação em vez de falhar, para o upsert do app antigo seguir
--    dando 200).
--  * A carteira abre quando o app 0.34.8 entra com a conta (dobroes_open) ou pelo administrador
--    (dobroes_open_all, abre a de quem sobrou). dobroes_open_all e `real_billing_enabled = 'true'`
--    só depois da 0.34.8 na produção, com o OK registrado no HISTORY (decisão 8 do John, #34).
--
-- Regras de ouro: contra a IA são zero dobrões em qualquer resultado; online paga por
-- award_online_match; a abertura leva o MAIOR entre o saldo do servidor e o do aparelho, até
-- dobroes_open_max (nunca perde saldo; admite ganho único de quem editou o aparelho).
-- Ordem de locks: season_pass:<uid> / daily:<uid> primeiro, dobroes:<uid> depois. Nunca o inverso.

-- ------------------------------------------------------------------ números (ajustáveis)

insert into public.app_config (key, value) values
  ('dobroes_open_max', '200000'),            -- teto do saldo do aparelho na abertura
  ('dobroes_online_daily_cap', '500'),       -- dobrões de partida online por conta e dia (Brasília)
  ('dobroes_online_per_opponent', '3'),      -- recompensas por dia contra o mesmo adversário
  ('dobroes_online_base_win', '220'),        -- mesma conta de Profile.registerMatch (XP), um terço vira dobrão
  ('dobroes_online_base_loss', '80'),
  ('dobroes_online_precision_mult', '2'),
  ('dobroes_online_sunk_mult', '15'),
  ('dobroes_online_blitz_bonus', '120'),
  ('dobroes_online_blitz_turns', '40'),
  ('dobroes_online_divisor', '3'),
  ('dobroes_online_max_shots', '200')
on conflict (key) do nothing;

-- ------------------------------------------------------------------ tabelas

-- uma linha por conta com carteira aberta. source: 'app' (dobroes_open) ou 'admin' (dobroes_open_all)
create table if not exists public.dobroes_wallets (
  user_id   uuid        primary key references auth.users(id) on delete cascade,
  opened_at timestamptz not null default now(),
  opening   integer     not null,
  source    text        not null check (source in ('app', 'admin'))
);
alter table public.dobroes_wallets enable row level security;
-- só a própria linha, e só leitura: o gatilho de profiles (roda como o cliente) precisa enxergá-la
drop policy if exists "carteira: ler a propria" on public.dobroes_wallets;
create policy "carteira: ler a propria" on public.dobroes_wallets
  for select using (auth.uid() = user_id);
revoke insert, update, delete on public.dobroes_wallets from anon, authenticated;

-- razão: cada entrada e saída. unique (user, reason, ref) é a trava de "uma vez só"
create table if not exists public.dobroes_ledger (
  id            bigserial   primary key,
  user_id       uuid        not null references auth.users(id) on delete cascade,
  delta         integer     not null,
  reason        text        not null,
  ref           text        not null,
  balance_after integer     not null,
  created_at    timestamptz not null default now(),
  unique (user_id, reason, ref)
);
create index if not exists dobroes_ledger_user_day on public.dobroes_ledger (user_id, reason, created_at);
alter table public.dobroes_ledger enable row level security;
drop policy if exists "razao: ler o proprio" on public.dobroes_ledger;
create policy "razao: ler o proprio" on public.dobroes_ledger
  for select using (auth.uid() = user_id);
revoke insert, update, delete on public.dobroes_ledger from anon, authenticated;

-- preços da loja (pintura, casco, cartucho): o servidor manda. O app tem a mesma lista só para
-- mostrar (StoreCatalogTest trava a igualdade; mudou preço, mudam o teste, o SQL e o app juntos).
create table if not exists public.store_items (
  kind           text    not null check (kind in ('paint', 'fleet', 'charge')),
  item_id        text    not null,
  price          integer not null check (price >= 0),
  sort           integer not null,
  offer_eligible boolean not null default false,
  primary key (kind, item_id)
);
alter table public.store_items enable row level security;
drop policy if exists "loja: leitura aberta" on public.store_items;
create policy "loja: leitura aberta" on public.store_items for select using (true);
revoke insert, update, delete on public.store_items from anon, authenticated;

-- mesma ordem de Paint.all; oferta do dia = preço > 0 e sem estação (Paint.dailyOffer)
insert into public.store_items (kind, item_id, price, sort, offer_eligible) values
  ('paint', 'std', 0, 1, false), ('paint', 'br', 0, 2, false),
  ('paint', 'jp', 450, 3, true), ('paint', 'us', 450, 4, true),
  ('paint', 'uk', 600, 5, true), ('paint', 'pt', 600, 6, true),
  ('paint', 'arc', 800, 7, true), ('paint', 'dzl', 1000, 8, true),
  ('paint', 'spl', 1100, 9, true), ('paint', 'cor', 1200, 10, true),
  ('paint', 'slt', 1400, 11, true),
  ('paint', 's_pri', 0, 12, false), ('paint', 's_ver', 0, 13, false),
  ('paint', 's_out', 0, 14, false), ('paint', 's_inv', 0, 15, false),
  ('fleet', 'std', 0, 1, false), ('fleet', 'imp', 900, 2, false),
  ('fleet', 'atl', 900, 3, false), ('fleet', 'gho', 1800, 4, false),
  -- cartuchos avulsos: código da habilidade (Ability.code)
  ('charge', '2X', 100, 1, false), ('charge', 'REC', 80, 2, false),
  ('charge', 'SNR', 60, 3, false), ('charge', 'FUM', 50, 4, false)
on conflict (kind, item_id) do nothing;

-- ------------------------------------------------------------------ núcleo (internas)

create or replace function public.dobroes_has_wallet(p_user uuid)
returns boolean language sql stable security definer set search_path = public as $$
  select exists (select 1 from public.dobroes_wallets w where w.user_id = p_user);
$$;
revoke all on function public.dobroes_has_wallet(uuid) from public, anon, authenticated;

-- Movimenta o saldo (delta positivo credita, negativo debita) e grava o razão, tudo na mesma
-- transação e sob o lock dobroes:<uid> (reentrante). Idempotente: (uid, reason, ref) repetido devolve
-- o saldo sem mexer. Saldo final negativo desfaz a RPC inteira (dobroes_insufficient).
create or replace function public.dobroes_apply(p_user uuid, p_delta integer, p_reason text, p_ref text)
returns integer language plpgsql security definer set search_path = public as $$
declare
  bal integer;
begin
  perform pg_advisory_xact_lock(hashtext('dobroes:' || p_user::text));
  if exists (select 1 from public.dobroes_ledger l
              where l.user_id = p_user and l.reason = p_reason and l.ref = p_ref) then
    return (select p.credits from public.profiles p where p.id = p_user);
  end if;
  select p.credits into bal from public.profiles p where p.id = p_user for update;
  if bal is null then
    raise exception 'dobroes_no_profile';
  end if;
  bal := bal + p_delta;
  if bal < 0 then
    raise exception 'dobroes_insufficient';
  end if;
  insert into public.dobroes_ledger (user_id, delta, reason, ref, balance_after)
  values (p_user, p_delta, p_reason, p_ref, bal);
  update public.profiles set credits = bal where id = p_user;
  return bal;
end $$;
revoke all on function public.dobroes_apply(uuid, integer, text, text) from public, anon, authenticated;

-- É o que as RPCs que já existiam usam: com carteira credita, sem carteira não faz nada (legado).
create or replace function public.dobroes_credit_if_wallet(p_user uuid, p_delta integer, p_reason text, p_ref text)
returns void language plpgsql security definer set search_path = public as $$
begin
  if p_delta > 0 and public.dobroes_has_wallet(p_user) then
    perform public.dobroes_apply(p_user, p_delta, p_reason, p_ref);
  end if;
end $$;
revoke all on function public.dobroes_credit_if_wallet(uuid, integer, text, text) from public, anon, authenticated;

-- ------------------------------------------------------------------ gatilho de profiles

-- Com carteira aberta, o cliente (anon/authenticated, gravação direta) não muda dobrões nem itens:
-- o valor é IGNORADO (não falha), para o upsert do app antigo seguir dando 200 e gravando o resto da
-- carreira. Dentro das RPCs security definer o current_user é o dono e passa. Sem carteira (legado)
-- não faz nada. Roda com os direitos de quem grava; a política "carteira: ler a propria" deixa o
-- gatilho enxergar a carteira da própria conta.
create or replace function public.guard_profile_credits()
returns trigger language plpgsql as $$
begin
  if tg_op = 'UPDATE' and current_user in ('anon', 'authenticated') then
    if exists (select 1 from public.dobroes_wallets w where w.user_id = old.id) then
      new.credits := old.credits;
      new.owned := old.owned;
      new.fleets := old.fleets;
    end if;
  end if;
  return new;
end $$;

drop trigger if exists profiles_guard_credits on public.profiles;
create trigger profiles_guard_credits
  before update on public.profiles
  for each row execute function public.guard_profile_credits();

-- ------------------------------------------------------------------ abertura

-- Abre a carteira de uma conta (núcleo de dobroes_open e dobroes_open_all). Sob o lock:
--  * saldo base = o maior entre profiles.credits e p_local (limitado a dobroes_open_max);
--  * compras com credited > 0 e delivered_at nulo entram no razão (reason 'backfill'), EXCETO as que
--    o aparelho já somou (p_delivered): essas só ganham delivered_at, sem somar (evita soma dupla).
create or replace function public.dobroes_open_core(p_user uuid, p_local integer, p_delivered text[], p_source text)
returns boolean language plpgsql security definer set search_path = public as $$
declare
  server integer;
  base integer;
  r record;
begin
  perform pg_advisory_xact_lock(hashtext('dobroes:' || p_user::text));
  if public.dobroes_has_wallet(p_user) then
    return false;
  end if;
  select p.credits into server from public.profiles p where p.id = p_user for update;
  if server is null then
    raise exception 'dobroes_no_profile';
  end if;
  base := greatest(server, least(greatest(coalesce(p_local, 0), 0), public.config_int('dobroes_open_max', 200000)));

  insert into public.dobroes_wallets (user_id, opening, source) values (p_user, base, p_source);
  -- o razão soma o saldo desde zero: credits volta a 0 e a abertura repõe o valor
  update public.profiles set credits = 0 where id = p_user;
  perform public.dobroes_apply(p_user, base, 'opening', 'open');

  for r in
    select d.purchase_token, d.credited from public.dobroes_purchases d
     where d.user_id = p_user and d.delivered_at is null and d.credited > 0
     order by d.created_at, d.purchase_token
  loop
    if r.purchase_token = any (coalesce(p_delivered, '{}'::text[])) then
      update public.dobroes_purchases set delivered_at = now() where purchase_token = r.purchase_token;
    else
      perform public.dobroes_apply(p_user, r.credited, 'backfill', r.purchase_token);
    end if;
  end loop;
  return true;
end $$;
revoke all on function public.dobroes_open_core(uuid, integer, text[], text) from public, anon, authenticated;

-- O app 0.34.8 abre a carteira da própria conta (uma vez; chamada repetida só devolve o saldo).
-- balance, owned e fleets são os do servidor depois da abertura.
drop function if exists public.dobroes_open(integer, text[]);
create or replace function public.dobroes_open(p_local integer, p_delivered text[] default '{}')
returns table (balance integer, opened_now boolean, owned text, fleets text)
language plpgsql security definer set search_path = public as $$
declare
  uid uuid := auth.uid();
  opened boolean;
begin
  if uid is null then
    raise exception 'dobroes_auth';
  end if;
  opened := public.dobroes_open_core(uid, p_local, p_delivered, 'app');
  return query select p.credits, opened, p.owned, p.fleets from public.profiles p where p.id = uid;
end $$;
revoke all on function public.dobroes_open(integer, text[]) from public, anon;
grant execute on function public.dobroes_open(integer, text[]) to authenticated;

-- Abertura em massa, só administrador (service_role/dono, SQL Editor): abre a carteira de toda conta
-- sem carteira com credits + compras não entregues. Passo obrigatório antes de real_billing_enabled.
create or replace function public.dobroes_open_all()
returns integer language plpgsql security definer set search_path = public as $$
declare
  u uuid;
  n integer := 0;
begin
  for u in
    select p.id from public.profiles p
     where not exists (select 1 from public.dobroes_wallets w where w.user_id = p.id)
     order by p.created_at
  loop
    if public.dobroes_open_core(u, 0, '{}'::text[], 'admin') then n := n + 1; end if;
  end loop;
  return n;
end $$;
revoke all on function public.dobroes_open_all() from public, anon, authenticated;
grant execute on function public.dobroes_open_all() to service_role;

-- Saldo e itens do servidor. wallet = false: conta ainda no legado (o app abre a carteira).
create or replace function public.dobroes_balance()
returns table (balance integer, wallet boolean, owned text, fleets text)
language sql stable security definer set search_path = public as $$
  select p.credits, public.dobroes_has_wallet(p.id), p.owned, p.fleets
    from public.profiles p where p.id = auth.uid();
$$;
revoke all on function public.dobroes_balance() from public, anon;
grant execute on function public.dobroes_balance() to authenticated;

-- ------------------------------------------------------------------ compras reais, Prêmio e anúncio

-- Toda compra com crédito (real, vitrine, Prêmio Beta, anúncio) já é gravada em dobroes_purchases:
-- um gatilho cobre as quatro de uma vez, sem mexer em apply_dobroes_purchase, claim_premio_beta nem
-- apply_ad_reward. Sem carteira não faz nada (o aparelho soma por dobroes_pending, como antes).
-- delivered_at fica nulo: dobroes_pending segue mostrando o aviso "+N" ao app, que confirma com ack.
create or replace function public.dobroes_purchase_credit()
returns trigger language plpgsql security definer set search_path = public as $$
begin
  if new.credited > 0 then
    perform public.dobroes_credit_if_wallet(new.user_id, new.credited, 'purchase:' || new.kind, new.purchase_token);
  end if;
  return new;
end $$;
revoke all on function public.dobroes_purchase_credit() from public, anon, authenticated;

drop trigger if exists dobroes_purchases_credit on public.dobroes_purchases;
create trigger dobroes_purchases_credit
  after insert on public.dobroes_purchases
  for each row execute function public.dobroes_purchase_credit();

-- ------------------------------------------------------------------ passe de temporada: pintura da estação

-- "2026-primavera" -> "s_pri" (mesma tabela de Paint.ofSeason)
create or replace function public.season_paint_id(p_key text)
returns text language sql immutable as $$
  select case split_part(p_key, '-', 2)
    when 'verao' then 's_ver'
    when 'outono' then 's_out'
    when 'inverno' then 's_inv'
    else 's_pri'
  end;
$$;

-- ------------------------------------------------------------------ partida online

-- Paga a recompensa de dobrões de uma partida online, só para conta com carteira. O servidor
-- confere a sala e limita os números; paga uma vez por sala (reason 'online', ref = id da sala).
-- Contra a IA não há RPC: são zero dobrões. Retorna status:
--   ok | already | no_wallet | not_eligible | no_result | limit (3 por adversário/dia) | capped (teto diário)
-- e o valor creditado (em 'already', o valor que foi pago) e o saldo.
drop function if exists public.award_online_match(uuid, integer, integer, integer, integer, boolean);
create or replace function public.award_online_match(
  p_match_id uuid, p_shots integer, p_hits integer, p_sunk integer, p_turns integer, p_victory boolean)
returns table (status text, amount integer, balance integer)
language plpgsql security definer set search_path = public as $$
declare
  uid uuid := auth.uid();
  m public.online_matches%rowtype;
  opp uuid;
  won boolean;
  sh integer;
  hi integer;
  su integer;
  tu integer;
  prec integer;
  raw integer;
  pay integer;
  used_opp integer;
  used_day integer;
  prior integer;
  bal integer;
  day_ date := public.brt_today();
begin
  if uid is null then
    raise exception 'dobroes_auth';
  end if;
  perform pg_advisory_xact_lock(hashtext('dobroes:' || uid::text));
  select p.credits into bal from public.profiles p where p.id = uid;
  if not public.dobroes_has_wallet(uid) then
    return query select 'no_wallet'::text, 0, bal; return;
  end if;

  select l.delta into prior from public.dobroes_ledger l
   where l.user_id = uid and l.reason = 'online' and l.ref = p_match_id::text;
  if found then
    return query select 'already'::text, prior, bal; return;
  end if;

  select * into m from public.online_matches om where om.id = p_match_id;
  if m.id is null or m.guest_id is null or (m.host_id <> uid and m.guest_id <> uid) then
    return query select 'not_eligible'::text, 0, bal; return;
  end if;
  opp := case when m.host_id = uid then m.guest_id else m.host_id end;

  -- sem combate não paga: os DOIS lados precisam ter dado tiro (ACT) ou usado habilidade (ABIL)
  if not exists (select 1 from public.online_messages om where om.match_id = p_match_id and om.sender_id = uid
                    and (om.body like 'ACT|%' or om.body like 'ABIL|%'))
     or not exists (select 1 from public.online_messages om where om.match_id = p_match_id and om.sender_id = opp
                    and (om.body like 'ACT|%' or om.body like 'ABIL|%')) then
    return query select 'not_eligible'::text, 0, bal; return;
  end if;

  -- ranqueada: o vencedor é o que o servidor registrou (winner_id); sem ele ainda, tenta de novo depois
  if m.ranked then
    if m.winner_id is null then
      return query select 'no_result'::text, 0, bal; return;
    end if;
    won := m.winner_id = uid;
  else
    won := coalesce(p_victory, false);
  end if;

  -- limite por adversário e por dia (mesma regra que Profile.claimOnlineReward tinha no aparelho)
  select count(*)::integer into used_opp
    from public.dobroes_ledger l
    join public.online_matches om on om.id::text = l.ref
   where l.user_id = uid and l.reason = 'online'
     and (l.created_at at time zone 'America/Sao_Paulo')::date = day_
     and (case when om.host_id = uid then om.guest_id else om.host_id end) = opp;
  if used_opp >= public.config_int('dobroes_online_per_opponent', 3) then
    return query select 'limit'::text, 0, bal; return;
  end if;

  -- mesma fórmula de Profile.registerMatch, com os números limitados no servidor
  sh := least(greatest(coalesce(p_shots, 0), 0), public.config_int('dobroes_online_max_shots', 200));
  hi := least(greatest(coalesce(p_hits, 0), 0), sh);
  su := least(greatest(coalesce(p_sunk, 0), 0), 5);
  tu := greatest(coalesce(p_turns, 1), 1);
  prec := case when sh = 0 then 0 else (hi * 100) / sh end;
  raw := case when won then public.config_int('dobroes_online_base_win', 220)
              else public.config_int('dobroes_online_base_loss', 80) end
         + prec * public.config_int('dobroes_online_precision_mult', 2)
         + su * public.config_int('dobroes_online_sunk_mult', 15)
         + case when won and tu <= public.config_int('dobroes_online_blitz_turns', 40)
                then public.config_int('dobroes_online_blitz_bonus', 120) else 0 end;
  pay := raw / greatest(public.config_int('dobroes_online_divisor', 3), 1);

  select coalesce(sum(l.delta), 0)::integer into used_day from public.dobroes_ledger l
   where l.user_id = uid and l.reason = 'online'
     and (l.created_at at time zone 'America/Sao_Paulo')::date = day_;
  pay := least(pay, greatest(public.config_int('dobroes_online_daily_cap', 500) - used_day, 0));
  if pay <= 0 then
    return query select 'capped'::text, 0, bal; return;
  end if;

  bal := public.dobroes_apply(uid, pay, 'online', p_match_id::text);
  return query select 'ok'::text, pay, bal;
end $$;
revoke all on function public.award_online_match(uuid, integer, integer, integer, integer, boolean) from public, anon;
grant execute on function public.award_online_match(uuid, integer, integer, integer, integer, boolean) to authenticated;

-- ------------------------------------------------------------------ loja (saídas)

-- Camuflagem em oferta hoje (Paint.dailyOffer): uma por dia, rodízio pela data de Brasília sobre as
-- oferecíveis em `sort`, pulando as que a conta já tem. Nulo quando não sobrou nenhuma.
create or replace function public.store_offer_today(p_owned text)
returns text language plpgsql stable security definer set search_path = public as $$
declare
  ids text[];
  n integer;
  start integer;
  i integer;
  cand text;
  have text[] := string_to_array(coalesce(p_owned, ''), ',');
begin
  select array_agg(s.item_id order by s.sort) into ids
    from public.store_items s where s.kind = 'paint' and s.offer_eligible and s.price > 0;
  n := coalesce(array_length(ids, 1), 0);
  if n = 0 then return null; end if;
  start := (public.brt_today() - date '1970-01-01') % n;
  for i in 0 .. n - 1 loop
    cand := ids[((start + i) % n) + 1];
    if not (cand = any (have)) then return cand; end if;
  end loop;
  return null;
end $$;
revoke all on function public.store_offer_today(text) from public, anon, authenticated;

-- Compra de pintura (paint), linha de casco (fleet) ou cartucho de habilidade (charge, p_item = código
-- da habilidade). O preço sai de store_items; a oferta do dia (30% off) é recalculada aqui. Item que a
-- conta já tem devolve 'owned' sem cobrar. Status: ok | owned | insufficient | not_found | no_wallet.
-- O cartucho vale no aparelho (o app soma a carga quando recebe 'ok').
drop function if exists public.dobroes_buy(text, text);
create or replace function public.dobroes_buy(p_kind text, p_item text)
returns table (status text, balance integer, owned text, fleets text)
language plpgsql security definer set search_path = public as $$
declare
  uid uuid := auth.uid();
  it public.store_items%rowtype;
  prof public.profiles%rowtype;
  price integer;
  bal integer;
  have text[];
begin
  if uid is null then
    raise exception 'dobroes_auth';
  end if;
  perform pg_advisory_xact_lock(hashtext('dobroes:' || uid::text));
  select * into prof from public.profiles p where p.id = uid;
  if not public.dobroes_has_wallet(uid) then
    return query select 'no_wallet'::text, prof.credits, prof.owned, prof.fleets; return;
  end if;
  select * into it from public.store_items s where s.kind = p_kind and s.item_id = p_item;
  if it.item_id is null or it.price <= 0 then
    return query select 'not_found'::text, prof.credits, prof.owned, prof.fleets; return;
  end if;
  if p_kind = 'paint' and it.item_id like 's\_%' then
    return query select 'not_found'::text, prof.credits, prof.owned, prof.fleets; return;
  end if;

  if p_kind in ('paint', 'fleet') then
    have := string_to_array(case when p_kind = 'paint' then prof.owned else prof.fleets end, ',');
    if p_item = any (have) then
      return query select 'owned'::text, prof.credits, prof.owned, prof.fleets; return;
    end if;
  end if;

  price := it.price;
  if p_kind = 'paint' and public.store_offer_today(prof.owned) = p_item then
    price := it.price * (100 - 30) / 100;
  end if;
  if prof.credits < price then
    return query select 'insufficient'::text, prof.credits, prof.owned, prof.fleets; return;
  end if;

  if p_kind = 'charge' then
    bal := public.dobroes_apply(uid, -price, 'store_charge', gen_random_uuid()::text);
  else
    bal := public.dobroes_apply(uid, -price, 'store_' || p_kind, p_item);
    if p_kind = 'paint' then
      update public.profiles p set owned = p.owned || ',' || p_item where p.id = uid;
    else
      update public.profiles p set fleets = p.fleets || ',' || p_item where p.id = uid;
    end if;
  end if;
  return query select 'ok'::text, bal, p2.owned, p2.fleets from public.profiles p2 where p2.id = uid;
end $$;
revoke all on function public.dobroes_buy(text, text) from public, anon;
grant execute on function public.dobroes_buy(text, text) to authenticated;
