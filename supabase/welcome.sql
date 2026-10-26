-- Presente de boas-vindas (#43, parte da primeira partida guiada). Rode depois de economy.sql,
-- dobroes_saldo.sql e test_accounts.sql. Idempotente. Migração: `welcome_gift` (mesmo conteúdo).
--
-- Conta NOVA ganha uma vez: welcome_gift_doubloons dobrões + welcome_gift_miles milhas, ao terminar OU
-- pular a partida guiada contra a IA. O servidor não vê a partida contra a IA, então o pagamento não
-- depende de resultado (a IA continua pagando zero dobrões). Valor fixo e conhecido antes (ECA Digital).
--  * Só conta criada DEPOIS de welcome_gift_since (data da migração): quem já joga não recebe.
--    Conta QA (is_qa_account) ignora a data, para o QA poder testar com as contas antigas.
--  * Conta is_test (robô da Google) não recebe. Interruptor de emergência: welcome_gift_enabled.
--  * Pago por dobroes_apply(uid, valor, 'welcome', 'welcome'): o razão é a trava de uma vez só. Não
--    entra no teto de dobroes_online_daily_cap (que protege só reason = 'online').
--  * Ordem de locks: welcome:<uid>, depois dobroes:<uid> (dobroes_apply), depois a linha de profiles
--    (miles_refresh).

insert into public.app_config (key, value) values
  ('welcome_gift_enabled', 'true'),
  ('welcome_gift_doubloons', '150'),
  ('welcome_gift_miles', '5'),
  ('welcome_gift_since', now()::text)
on conflict (key) do nothing;

create table if not exists public.welcome_gifts (
  user_id    uuid        primary key references auth.users(id) on delete cascade,
  doubloons  integer     not null,
  miles      integer     not null,
  created_at timestamptz not null default now()
);

alter table public.welcome_gifts enable row level security;

drop policy if exists "boas-vindas: ler o proprio" on public.welcome_gifts;
create policy "boas-vindas: ler o proprio"
  on public.welcome_gifts for select
  using (auth.uid() = user_id);

revoke all on public.welcome_gifts from anon, authenticated;
grant select on public.welcome_gifts to authenticated;

-- status: ok | already | not_eligible | no_wallet. `balance` é o saldo depois (0 quando não pagou).
create or replace function public.claim_welcome_gift()
returns table (status text, doubloons integer, miles integer, balance integer)
language plpgsql security definer set search_path = public as $$
#variable_conflict use_column
declare
  uid uuid := auth.uid();
  created timestamptz;
  since timestamptz;
  pay integer;
  mi integer;
  bal integer;
  cur integer;
begin
  if uid is null then
    raise exception 'welcome_auth';
  end if;
  perform pg_advisory_xact_lock(hashtext('welcome:' || uid::text));

  if exists (select 1 from public.welcome_gifts g where g.user_id = uid) then
    return query select 'already'::text, 0, 0,
      coalesce((select p.credits from public.profiles p where p.id = uid), 0);
    return;
  end if;

  select u.created_at into created from auth.users u where u.id = uid;
  begin
    since := (select a.value from public.app_config a where a.key = 'welcome_gift_since')::timestamptz;
  exception when others then
    since := null;
  end;

  if coalesce((select a.value from public.app_config a where a.key = 'welcome_gift_enabled'), 'false') <> 'true'
     or public.is_test_user(uid)
     or since is null
     or (created < since and not public.is_qa_account(uid)) then
    return query select 'not_eligible'::text, 0, 0, 0;
    return;
  end if;

  if not public.dobroes_has_wallet(uid) then
    return query select 'no_wallet'::text, 0, 0, 0;
    return;
  end if;

  pay := public.config_int('welcome_gift_doubloons', 150);
  mi := public.config_int('welcome_gift_miles', 5);

  bal := public.dobroes_apply(uid, pay, 'welcome', 'welcome');
  cur := public.miles_refresh(uid);
  update public.profiles
     set nautical_miles = greatest(cur, least(public.config_int('miles_cap', 50), cur + mi))
   where id = uid
  returning nautical_miles into cur;
  insert into public.welcome_gifts (user_id, doubloons, miles) values (uid, pay, mi);

  return query select 'ok'::text, pay, mi, bal;
end $$;

revoke all on function public.claim_welcome_gift() from public, anon;
grant execute on function public.claim_welcome_gift() to authenticated;

-- só administrador: repete o QA numa conta QA. Apaga a marca e libera a linha do razão do presente (vira
-- 'welcome_reset'); o saldo já creditado fica como está. Recusa conta que não seja QA.
create or replace function public.welcome_gift_reset(p_user uuid)
returns void
language plpgsql security definer set search_path = public as $$
begin
  if not public.is_qa_account(p_user) then
    raise exception 'welcome_reset_only_qa';
  end if;
  perform pg_advisory_xact_lock(hashtext('welcome:' || p_user::text));
  perform pg_advisory_xact_lock(hashtext('dobroes:' || p_user::text));
  delete from public.welcome_gifts where user_id = p_user;
  -- a linha do razão sai da trava (reason 'welcome') sem sumir: a soma do razão continua igual ao saldo
  update public.dobroes_ledger
     set reason = 'welcome_reset', ref = gen_random_uuid()::text
   where user_id = p_user and reason = 'welcome';
end $$;

revoke all on function public.welcome_gift_reset(uuid) from public, anon, authenticated;
