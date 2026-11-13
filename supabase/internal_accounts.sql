-- Naval Battle · contas internas fora da medição e visão das compras com origem (issue #96, 0.44.1)
-- Rode no SQL Editor depois de schema.sql, test_accounts.sql, dobroes.sql e dobroes_saldo.sql.
-- Idempotente: rodar de novo não duplica nada. Migração no banco: `internal_accounts` (mesmo conteúdo).
-- Teste que desfaz tudo: supabase/tests/internal_accounts_ensaio.sql (cole este arquivo e o ensaio na
-- MESMA execução; o raise no fim cancela a transação inteira, DDL inclusive).
--
-- O problema: a compra real de 18/10 era do próprio dono e entrou na receita da linha de base (#94).
-- A medição precisa separar contas do time (dono e QA) dos jogadores de fora, sem mudar o jogo de
-- ninguém.
--
-- Por que uma coluna nova e não `is_test`: `is_test` tira a conta do placar e da temporada, separa a
-- partida rápida e corta brindes; marcar o dono com ela mudaria o jogo dele, e `refresh_is_test` a
-- desmarcaria na passada seguinte. `profiles.is_internal` vale SÓ para medição e relatório.
-- REGRA: nenhuma regra de jogo, RLS, brinde, ranking ou preço lê `is_internal` nem
-- `is_internal_account` (README → Especificação técnica → Back-end).
--
-- Predicado único "interna": `is_internal_account(id)` = `profiles.is_internal` OU conta QA
-- (`allowed_players` source 'qa'). QA01/QA02 contam como internas mesmo quando o QA põe
-- `is_test = false` durante o teste. As medições excluem `is_test OR is_internal_account(id)`.
--
-- Como marcar a conta do dono sem e-mail no repositório (o repositório é público):
-- `app_config.internal_accounts` nasce VAZIA aqui; o valor (user_id ou e-mail de login, separados por
-- vírgula) é preenchido só no banco, como `beta_gift_emails`. `app_config` tem RLS sem política (o
-- cliente não lê). A marca só soma: nada desmarca sozinho. Para tirar alguém, update manual.
-- Aplicação na produção (com o OK do John, fora do repo): update da chave com o user_id do dono,
-- `select * from refresh_all_internal(false)` (conferir to_internal = 1) e depois `(true)`.
--
-- Cliente: nenhuma mudança. `loadProfile` usa select=* com optString/optInt (coluna extra ignorada) e
-- o upsert não manda a coluna. O app antigo instalado segue igual.
--
-- Desfazer na produção:
--   drop view if exists public.dobroes_purchases_com_origem;
--   drop trigger if exists profiles_classify_internal on public.profiles;
--   drop trigger if exists profiles_guard_is_internal on public.profiles;
--   drop function if exists public.classify_internal_profile();
--   drop function if exists public.guard_profile_is_internal();
--   drop function if exists public.refresh_all_internal(boolean);
--   drop function if exists public.refresh_internal(uuid);
--   drop function if exists public.internal_list_match(uuid);
--   drop function if exists public.is_internal_account(uuid);
--   alter table public.profiles drop column if exists is_internal;
--   alter table public.dobroes_products drop column if exists price_cents;
--   delete from public.app_config where key = 'internal_accounts';

-- ------------------------------------------------------------------ coluna, chave e preço

alter table public.profiles add column if not exists is_internal boolean not null default false;

insert into public.app_config (key, value) values ('internal_accounts', '')
on conflict (key) do nothing;

-- preço de tabela em centavos (BRL): fonte única para a view e para docs/medicao-semanal.sql
alter table public.dobroes_products add column if not exists price_cents integer
  check (price_cents is null or price_cents > 0);
update public.dobroes_products p set price_cents = v.cents
  from (values ('dobroes_bolsa', 490), ('dobroes_bau', 990), ('dobroes_cofre', 2490), ('dobroes_tesouro', 4990))
       as v(product_id, cents)
 where p.product_id = v.product_id and p.price_cents is distinct from v.cents;

-- ------------------------------------------------------------------ trava (igual à de is_test)

-- anon/authenticated não marcam nem desmarcam; no INSERT do app vira false
create or replace function public.guard_profile_is_internal()
returns trigger language plpgsql as $$
begin
  if current_user in ('anon', 'authenticated') then
    if tg_op = 'INSERT' then
      new.is_internal := false;
    else
      new.is_internal := coalesce(old.is_internal, false);
    end if;
  end if;
  return new;
end $$;
revoke all on function public.guard_profile_is_internal() from public, anon, authenticated;

drop trigger if exists profiles_guard_is_internal on public.profiles;
create trigger profiles_guard_is_internal
  before insert or update on public.profiles
  for each row execute function public.guard_profile_is_internal();

-- ------------------------------------------------------------------ predicado e marcação

-- interna = marcada pela lista OU conta QA (allowed_players 'qa'). Só medição e relatório.
create or replace function public.is_internal_account(p_user uuid)
returns boolean
language sql security definer set search_path = public stable as $$
  select coalesce((select p.is_internal from public.profiles p where p.id = p_user), false)
      or public.is_qa_account(p_user);
$$;
revoke all on function public.is_internal_account(uuid) from public, anon, authenticated;
grant execute on function public.is_internal_account(uuid) to service_role;

-- a conta está na lista app_config.internal_accounts (user_id ou e-mail de login de auth.users,
-- nunca profiles.email, que o cliente grava)
create or replace function public.internal_list_match(p_user uuid)
returns boolean
language sql security definer set search_path = public stable as $$
  select exists (
    select 1
      from public.app_config c
      cross join lateral unnest(string_to_array(c.value, ',')) as item(raw)
      left join auth.users u on u.id = p_user
     where c.key = 'internal_accounts'
       and nullif(lower(trim(item.raw)), '') is not null
       and (lower(trim(item.raw)) = p_user::text or lower(trim(item.raw)) = lower(trim(u.email))));
$$;
revoke all on function public.internal_list_match(uuid) from public, anon, authenticated;

-- marca uma conta se estiver na lista; só soma, nunca desmarca
create or replace function public.refresh_internal(p_user uuid)
returns void
language plpgsql security definer set search_path = public as $$
begin
  update public.profiles p
     set is_internal = true
   where p.id = p_user
     and not p.is_internal
     and public.internal_list_match(p.id);
end $$;
revoke all on function public.refresh_internal(uuid) from public, anon, authenticated;

-- passada em todas as contas: p_apply = false só conta (ensaio), true aplica.
-- to_internal = quantas passam a interna; internal_after = marcadas pela lista depois da passada
-- (as QA contam pelo allowed_players, não por esta coluna).
create or replace function public.refresh_all_internal(p_apply boolean default false)
returns table (to_internal integer, internal_after integer)
language plpgsql security definer set search_path = public as $$
declare n integer;
begin
  select count(*) into n from public.profiles p
   where not p.is_internal and public.internal_list_match(p.id);
  if p_apply and n > 0 then
    update public.profiles p set is_internal = true
     where not p.is_internal and public.internal_list_match(p.id);
  end if;
  return query
    select n,
           ((select count(*) from public.profiles p where p.is_internal)
             + case when p_apply then 0 else n end)::integer;
end $$;
revoke all on function public.refresh_all_internal(boolean) from public, anon, authenticated;

-- conta nova já nasce marcada (handle_new_user e insert direto do app); security definer para
-- passar pela trava guard_profile_is_internal
create or replace function public.classify_internal_profile()
returns trigger
language plpgsql security definer set search_path = public as $$
begin
  perform public.refresh_internal(new.id);
  return null;
end $$;
revoke all on function public.classify_internal_profile() from public, anon, authenticated;

drop trigger if exists profiles_classify_internal on public.profiles;
create trigger profiles_classify_internal
  after insert on public.profiles
  for each row execute function public.classify_internal_profile();

-- ------------------------------------------------------------------ visão das compras

-- Compras de dobrões com a origem. Sem e-mail, sem purchase_token, sem order_id.
-- valor_brl: preço de tabela só para kind 'real' (nos outros não houve cobrança, vale 0).
-- Privada: só service_role (e o dono do banco) lê. O Supabase dá direitos padrão a anon/authenticated
-- em objeto novo do public, por isso o revoke explícito.
create or replace view public.dobroes_purchases_com_origem
with (security_invoker = true) as
select d.user_id,
       p.username                                   as apelido,
       d.kind,
       d.product_id                                 as produto,
       d.credited                                   as dobroes,
       (case when d.kind = 'real' then pr.price_cents / 100.0 else 0 end)::numeric(10, 2) as valor_brl,
       d.created_at                                 as data,
       d.day                                        as dia,
       public.is_internal_account(d.user_id)        as interna,
       coalesce(p.is_test, false)                   as teste
  from public.dobroes_purchases d
  left join public.profiles p on p.id = d.user_id
  left join public.dobroes_products pr on pr.product_id = d.product_id;

revoke all on public.dobroes_purchases_com_origem from public, anon, authenticated;
grant select on public.dobroes_purchases_com_origem to service_role;
