-- Naval Battle · compra de dobrões no iPhone pela App Store, StoreKit 2 (#65, fatia 1: servidor).
-- Rode no SQL Editor depois de dobroes.sql e dobroes_saldo.sql (usa dobroes_purchases, beta_eligible,
-- beta_store_on, dobroes_apply e o gatilho dobroes_purchases_credit). Aditivo e idempotente: não
-- toca apply_dobroes_purchase (Android) nem nenhuma compra já gravada.
--
-- Fluxo (Edge `dobroes-verify-ios`, verify_jwt ligado):
--   o app manda o JWS da transação; a Edge lê só o transactionId, reconsulta a App Store Server API
--   (a verdade) e chama apply_dobroes_purchase_ios com service role. Ninguém mais chama a função.
--   PK purchase_token = 'ios:<transactionId>': mesma idempotência do Android (nunca credita 2x).
--   O insert com credited > 0 dispara o gatilho dobroes_purchases_credit, que grava o razão
--   ('purchase:real' ou 'purchase:test', ref = token) na mesma transação.
--
-- Status (mesmo vocabulário do Android) e se o app finaliza a transação (finish):
--   credited, already, sandbox_refused, not_purchased -> finish = true (terminais)
--   billing_off, account_mismatch, unknown_product    -> finish = false (a Apple devolve depois)
--
-- Production: kind 'real'; credita só com real_billing_ios_enabled = 'true' (ou conta
--   beta_eligible com beta_store_enabled). Desligado: billing_off, não grava, não finaliza.
-- Sandbox NUNCA credita conta real: só beta_eligible + beta_store_on() credita (kind 'test').
--   Qualquer outra conta recebe sandbox_refused (grava a linha sem crédito e finaliza).
--   ATENÇÃO (guideline 2.1): o revisor da Apple compra em Sandbox; a conta de demonstração da
--   revisão precisa ser beta_eligible e a vitrine beta ligada, senão a compra "não funciona".
--
-- Reembolso/revogação (Edge `apple-iap-notifications`, verify_jwt desligado): apply_dobroes_refund_ios
--   marca refunded_at (idempotente) e debita no razão (reason 'refund:ios', ref = token) até zerar o
--   saldo, nunca negativo. A diferença vai para dobroes_refund_shortfall (a dívida que o John vê).
--   Sem bloquear a conta nesta fatia.
--
-- Pré-requisito para vender: carteira aberta (dobroes_open_all) em todas as contas, o mesmo passo
-- já exigido antes de real_billing_enabled (#93).
--
-- ROLLBACK (só antes de qualquer venda: compras gravadas ficam):
--   update public.app_config set value = 'false' where key = 'real_billing_ios_enabled';  -- corte imediato
--   drop function if exists public.apply_dobroes_purchase_ios(uuid, text, text, text, boolean, boolean);
--   drop function if exists public.apply_dobroes_refund_ios(text, text);
--   drop table if exists public.dobroes_refund_shortfall;
--   alter table public.dobroes_purchases drop column if exists store, drop column if exists environment,
--     drop column if exists refunded_at;
--   delete from public.app_config where key = 'real_billing_ios_enabled';
--   e apagar as Edges dobroes-verify-ios e apple-iap-notifications.

-- ------------------------------------------------------------------ interruptor

insert into public.app_config (key, value) values
  ('real_billing_ios_enabled', 'false')   -- venda real no iPhone: só com #93 conferida, produtos aprovados e OK do John
on conflict (key) do nothing;

-- ------------------------------------------------------------------ colunas novas

-- store: 'play' (tudo o que já existe) ou 'ios'; environment: 'Production'/'Sandbox' (só iOS);
-- refunded_at: reembolso ou revogação confirmados pela Apple
alter table public.dobroes_purchases
  add column if not exists store text not null default 'play',
  add column if not exists environment text,
  add column if not exists refunded_at timestamptz;

alter table public.dobroes_purchases drop constraint if exists dobroes_purchases_store_check;
alter table public.dobroes_purchases add constraint dobroes_purchases_store_check
  check (store in ('play', 'ios'));

-- dívida de estorno: o que a Apple devolveu ao jogador e o saldo dele já não cobria
create table if not exists public.dobroes_refund_shortfall (
  purchase_token text        primary key,
  user_id        uuid        not null references auth.users(id) on delete cascade,
  shortfall      integer     not null check (shortfall > 0),
  created_at     timestamptz not null default now()
);
alter table public.dobroes_refund_shortfall enable row level security;   -- sem política: só o dono do banco e o service_role
revoke all on public.dobroes_refund_shortfall from public, anon, authenticated;

-- ------------------------------------------------------------------ compra

-- Decide uma compra vinda da Edge `dobroes-verify-ios` (que já conferiu na App Store Server API).
-- Só service_role chama.
--   p_environment: 'Production' ou 'Sandbox' (vem do host que respondeu; qualquer outro valor é
--                  tratado como Sandbox, o caminho que nunca credita conta real)
--   p_account_ok:  appAccountToken da transação == usuário do JWT
--   p_revoked:     a transação tem revocationDate
-- Retorna: credited | already | billing_off | not_purchased | unknown_product | account_mismatch |
--          sandbox_refused
create or replace function public.apply_dobroes_purchase_ios(
  p_user uuid, p_product text, p_transaction_id text, p_environment text,
  p_account_ok boolean, p_revoked boolean
)
returns table (status text, doubloons integer)
language plpgsql security definer set search_path = public as $$
declare
  dob integer;
  v_on boolean;
  v_beta boolean;
  v_token text := 'ios:' || p_transaction_id;
  v_env text := case when p_environment = 'Production' then 'Production' else 'Sandbox' end;
begin
  perform pg_advisory_xact_lock(hashtext('dobroes:' || p_user::text));

  if coalesce(p_revoked, false) then
    return query select 'not_purchased'::text, 0; return;
  end if;
  select d.doubloons into dob from public.dobroes_products d where d.product_id = p_product;
  if dob is null then return query select 'unknown_product'::text, 0; return; end if;
  if not coalesce(p_account_ok, false) then return query select 'account_mismatch'::text, 0; return; end if;

  -- idempotência: o mesmo transactionId nunca credita duas vezes
  if exists (select 1 from public.dobroes_purchases x where x.purchase_token = v_token) then
    return query select 'already'::text, 0; return;
  end if;

  v_beta := public.beta_eligible(p_user);

  if v_env = 'Production' then
    select value = 'true' into v_on from public.app_config where key = 'real_billing_ios_enabled';
    if not (coalesce(v_on, false) or (v_beta and public.beta_store_on())) then
      return query select 'billing_off'::text, 0; return;
    end if;
    insert into public.dobroes_purchases
      (purchase_token, user_id, product_id, order_id, kind, credited, store, environment)
    values (v_token, p_user, p_product, p_transaction_id, 'real', dob, 'ios', 'Production');
    return query select 'credited'::text, dob; return;
  end if;

  -- Sandbox: nunca credita conta real
  if not (v_beta and public.beta_store_on()) then
    insert into public.dobroes_purchases
      (purchase_token, user_id, product_id, order_id, kind, credited, store, environment)
    values (v_token, p_user, p_product, p_transaction_id, 'test', 0, 'ios', 'Sandbox');
    return query select 'sandbox_refused'::text, 0; return;
  end if;
  insert into public.dobroes_purchases
    (purchase_token, user_id, product_id, order_id, kind, credited, store, environment)
  values (v_token, p_user, p_product, p_transaction_id, 'test', dob, 'ios', 'Sandbox');
  return query select 'credited'::text, dob; return;
end $$;
revoke all on function public.apply_dobroes_purchase_ios(uuid, text, text, text, boolean, boolean) from public, anon, authenticated;
grant execute on function public.apply_dobroes_purchase_ios(uuid, text, text, text, boolean, boolean) to service_role;

-- ------------------------------------------------------------------ reembolso e revogação

-- Chamada pela Edge `apple-iap-notifications` só depois de a API da Apple confirmar revocationDate.
-- Só service_role chama. Retorna: refunded | already_refunded | unknown_purchase
--   debited: o que saiu do saldo (até zerar, nunca negativo); shortfall: o que faltou (dívida)
-- A compra sem crédito (sandbox_refused) só ganha refunded_at, sem débito.
create or replace function public.apply_dobroes_refund_ios(p_transaction_id text, p_environment text default null)
returns table (status text, debited integer, shortfall integer)
language plpgsql security definer set search_path = public as $$
declare
  v_token text := 'ios:' || p_transaction_id;
  uid uuid;
  cred integer;
  refunded timestamptz;
  bal integer;
  deb integer := 0;
  falta integer := 0;
begin
  select d.user_id into uid from public.dobroes_purchases d where d.purchase_token = v_token and d.store = 'ios';
  if uid is null then return query select 'unknown_purchase'::text, 0, 0; return; end if;

  perform pg_advisory_xact_lock(hashtext('dobroes:' || uid::text));

  select d.credited, d.refunded_at into cred, refunded
    from public.dobroes_purchases d where d.purchase_token = v_token for update;
  if refunded is not null then return query select 'already_refunded'::text, 0, 0; return; end if;

  update public.dobroes_purchases set refunded_at = now() where purchase_token = v_token;

  if cred > 0 then
    if public.dobroes_has_wallet(uid) then
      select p.credits into bal from public.profiles p where p.id = uid for update;
      deb := least(greatest(coalesce(bal, 0), 0), cred);
      if deb > 0 then
        perform public.dobroes_apply(uid, -deb, 'refund:ios', v_token);
      end if;
    end if;
    falta := cred - deb;
    if falta > 0 then
      insert into public.dobroes_refund_shortfall (purchase_token, user_id, shortfall)
      values (v_token, uid, falta)
      on conflict (purchase_token) do nothing;
    end if;
  end if;
  return query select 'refunded'::text, deb, falta;
end $$;
revoke all on function public.apply_dobroes_refund_ios(text, text) from public, anon, authenticated;
grant execute on function public.apply_dobroes_refund_ios(text, text) to service_role;
