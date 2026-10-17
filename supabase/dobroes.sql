-- Naval Battle · compras reais de dobrões pela Google Play (#15) e Pacote Prêmio Beta.
-- Rode no SQL Editor depois de economy.sql e test_accounts.sql (usa brt_today,
-- brt_seconds_to_midnight e allowed_players). Idempotente: rodar de novo não duplica
-- nada nem zera compras.
--
-- O servidor decide (quanto, se, quantas vezes); o aparelho aplica no saldo.
--  * Compra real: credita só com app_config.real_billing_enabled = 'true'.
--  * Prêmio Beta: compra de teste (licença de testador da Play), só para conta da lista
--    de testadores, só o Tesouro, uma vez por conta e dia (Brasília), só com
--    app_config.premio_beta_enabled = 'true'.
--  * Entrega: o aparelho lê dobroes_pending(), soma o que ainda não somou e confirma com
--    dobroes_ack(token). Nunca credita o mesmo token duas vezes (também no aparelho).
--  * Quem confere a compra na API da Play é a Edge Function dobroes-verify; ela chama
--    apply_dobroes_purchase com service role. Ninguém mais chama essa função.

-- ------------------------------------------------------------------ interruptores

insert into public.app_config (key, value) values
  ('real_billing_enabled', 'false'),   -- venda real para jogador comum (só depois da produção na Play)
  ('premio_beta_enabled', 'false')     -- Prêmio: ligar só com a lista de testadores de licença pronta
on conflict (key) do nothing;

-- ------------------------------------------------------------------ produtos e compras

-- produtos e quantidade (a Play só diz qual produto foi comprado; o servidor diz quanto vale)
create table if not exists public.dobroes_products (
  product_id text primary key,
  doubloons  integer not null check (doubloons > 0)
);
alter table public.dobroes_products enable row level security;

insert into public.dobroes_products (product_id, doubloons) values
  ('dobroes_bolsa', 500), ('dobroes_bau', 1100), ('dobroes_cofre', 3000), ('dobroes_tesouro', 6500)
on conflict (product_id) do nothing;

-- uma linha por compra (o token é a chave: nunca credita duas vezes)
create table if not exists public.dobroes_purchases (
  purchase_token text        primary key,
  user_id        uuid        not null references auth.users(id) on delete cascade,
  product_id     text        not null,
  order_id       text,
  kind           text        not null check (kind in ('real', 'test')),
  premio         boolean     not null default false,
  credited       integer     not null default 0,
  day            date        not null default public.brt_today(),
  delivered_at   timestamptz,
  created_at     timestamptz not null default now()
);

-- Prêmio: no máximo um por conta e dia (Brasília)
create unique index if not exists dobroes_premio_one_per_day
  on public.dobroes_purchases (user_id, day) where premio;

alter table public.dobroes_purchases enable row level security;
drop policy if exists "compras de dobrões: ler as proprias" on public.dobroes_purchases;
create policy "compras de dobrões: ler as proprias" on public.dobroes_purchases
  for select using (auth.uid() = user_id);

-- ------------------------------------------------------------------ regras

-- conta de testador: lista da Play, exceção manual ou conta QA (não depende do interruptor allowlist)
create or replace function public.beta_eligible(p_user uuid)
returns boolean language sql stable security definer set search_path = public as $$
  select exists (
           select 1 from public.allowed_players a
             join auth.users u on u.id = p_user
            where a.source in ('play', 'manual', 'qa') and a.email = lower(trim(u.email)))
      or exists (
           select 1 from public.allowed_players a
            where a.source in ('play', 'manual', 'qa') and a.user_id = p_user);
$$;
revoke all on function public.beta_eligible(uuid) from public, anon, authenticated;

-- o que a aba mostra (e se o Prêmio de hoje já foi pego)
create or replace function public.dobroes_store_status()
returns table (beta boolean, real_enabled boolean, premio_enabled boolean,
               premio_claimed boolean, premio_resets_in_seconds integer)
language sql stable security definer set search_path = public as $$
  select
    public.beta_eligible(auth.uid()),
    coalesce((select value = 'true' from public.app_config where key = 'real_billing_enabled'), false),
    coalesce((select value = 'true' from public.app_config where key = 'premio_beta_enabled'), false),
    exists (select 1 from public.dobroes_purchases d
             where d.user_id = auth.uid() and d.premio and d.day = public.brt_today()),
    public.brt_seconds_to_midnight();
$$;
revoke all on function public.dobroes_store_status() from public, anon;
grant execute on function public.dobroes_store_status() to authenticated;

-- Decide uma compra vinda da Edge `dobroes-verify` (que já conferiu na API da Play).
-- Só service_role chama. A Edge consome a compra na Play só para os status de consumo:
--   credited, premio, already, premio_done, not_beta -> consome (a compra é dela)
--   billing_off, premio_off, premio_only, not_purchased, unknown_product, promo_refused,
--   account_mismatch -> NÃO consome: a Play devolve sozinha em 3 dias
create or replace function public.apply_dobroes_purchase(
  p_user uuid, p_product text, p_token text, p_order text,
  p_purchase_type integer,  -- null = compra real · 0 = teste de licença · 1 = promoção
  p_state integer,          -- Play: 0 comprada · 1 cancelada · 2 pendente
  p_account_ok boolean      -- obfuscatedAccountId confere com o usuário
)
returns table (status text, doubloons integer)
language plpgsql security definer set search_path = public as $$
declare
  dob integer;
  v_on boolean;
  n integer;
begin
  perform pg_advisory_xact_lock(hashtext('dobroes:' || p_user::text));

  if p_state is distinct from 0 then
    return query select 'not_purchased'::text, 0; return;
  end if;
  select d.doubloons into dob from public.dobroes_products d where d.product_id = p_product;
  if dob is null then return query select 'unknown_product'::text, 0; return; end if;
  if p_purchase_type = 1 then return query select 'promo_refused'::text, 0; return; end if;
  if not coalesce(p_account_ok, false) then return query select 'account_mismatch'::text, 0; return; end if;

  -- idempotência: o mesmo token nunca credita duas vezes
  if exists (select 1 from public.dobroes_purchases x where x.purchase_token = p_token) then
    return query select 'already'::text, 0; return;
  end if;

  -- compra real: só com o interruptor ligado; desligado, não registra nem consome
  if p_purchase_type is null then
    select value = 'true' into v_on from public.app_config where key = 'real_billing_enabled';
    if not coalesce(v_on, false) then
      return query select 'billing_off'::text, 0; return;
    end if;
    insert into public.dobroes_purchases (purchase_token, user_id, product_id, order_id, kind, credited)
    values (p_token, p_user, p_product, p_order, 'real', dob);
    return query select 'credited'::text, dob; return;
  end if;

  -- compra de teste (licença de testador): nunca cobra; só o Prêmio credita
  if not public.beta_eligible(p_user) then
    insert into public.dobroes_purchases (purchase_token, user_id, product_id, order_id, kind)
    values (p_token, p_user, p_product, p_order, 'test');
    return query select 'not_beta'::text, 0; return;
  end if;
  select value = 'true' into v_on from public.app_config where key = 'premio_beta_enabled';
  if not coalesce(v_on, false) then
    return query select 'premio_off'::text, 0; return;
  end if;
  if p_product <> 'dobroes_tesouro' then
    return query select 'premio_only'::text, 0; return;
  end if;

  insert into public.dobroes_purchases (purchase_token, user_id, product_id, order_id, kind, premio, credited)
  values (p_token, p_user, p_product, p_order, 'test', true, dob)
  on conflict (user_id, day) where premio do nothing;
  get diagnostics n = row_count;
  if n = 0 then
    -- já pegou o Prêmio hoje: registra a compra de teste sem crédito e consome
    insert into public.dobroes_purchases (purchase_token, user_id, product_id, order_id, kind)
    values (p_token, p_user, p_product, p_order, 'test');
    return query select 'premio_done'::text, 0; return;
  end if;
  return query select 'premio'::text, dob;
end $$;
revoke all on function public.apply_dobroes_purchase(uuid, text, text, text, integer, integer, boolean) from public, anon, authenticated;
grant execute on function public.apply_dobroes_purchase(uuid, text, text, text, integer, integer, boolean) to service_role;

-- ------------------------------------------------------------------ entrega ao aparelho

-- o que ainda não foi somado no saldo do aparelho (dobroes_ack marca como entregue)
create or replace function public.dobroes_pending()
returns table (purchase_token text, doubloons integer, premio boolean)
language sql stable security definer set search_path = public as $$
  select d.purchase_token, d.credited, d.premio
    from public.dobroes_purchases d
   where d.user_id = auth.uid() and d.delivered_at is null and d.credited > 0
   order by d.created_at;
$$;

create or replace function public.dobroes_ack(p_token text)
returns void language sql security definer set search_path = public as $$
  update public.dobroes_purchases set delivered_at = now()
   where purchase_token = p_token and user_id = auth.uid() and delivered_at is null;
$$;

revoke all on function public.dobroes_pending() from public, anon;
revoke all on function public.dobroes_ack(text) from public, anon;
grant execute on function public.dobroes_pending() to authenticated;
grant execute on function public.dobroes_ack(text) to authenticated;

-- ------------------------------------------------------------------ loja simulada aposentada

-- a loja simulada sai: o app antigo (0.32.x) ainda chama esta função e recebe o erro,
-- então nada é creditado. Mesmo bloco está em economy.sql.
create or replace function public.buy_beta_pack(p_pack text)
returns table (doubloons integer, spent_cents integer, limit_cents integer)
language plpgsql security definer set search_path = public as $$
begin
  raise exception 'store_retired';
end $$;
revoke all on function public.buy_beta_pack(text) from public;
grant execute on function public.buy_beta_pack(text) to authenticated;
