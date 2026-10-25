-- Naval Battle · anúncios premiados opcionais (#29, AdMob com verificação no servidor, SSV).
-- Rode no SQL Editor depois de economy.sql, daily.sql, online.sql, test_accounts.sql e
-- dobroes.sql (usa brt_today, config_int, miles_refresh, daily_claims, online_matches,
-- online_messages, allowed_players e dobroes_purchases com kind 'ad'). Idempotente.
--
-- Só anúncio premiado, sempre a partir de um toque num botão. Quatro lugares:
--   win       dobrar os dobrões de uma vitória (até ads_win_double_cap)    2/dia
--   checkin   dobrar o check-in de hoje (o bônus da semana não dobra)      1/dia
--   challenge dobrar o desafio de hoje                                     1/dia
--   mile      ganhar 1 milha com saldo zero (sem passar de miles_cap)       2/dia
-- Teto geral: ads_daily_max anúncios e ads_daily_doubloons_max dobrões por conta e dia (Brasília).
--
-- Fluxo (o servidor decide; o aparelho só pede e aplica):
--  1. ad_reward_start(lugar, ref, valor): confere interruptor, limites e se o lugar vale; grava um
--     vale (15 min) com o valor decidido aqui. O app passa o vale como custom_data do SSV.
--  2. O Google chama a Edge ads-reward (verify_jwt desligado; a segurança é a assinatura ECDSA).
--  3. A Edge chama apply_ad_reward(vale, transaction_id, user_id) com service role: sob o lock
--     dobroes:<uid>, credita uma vez por transaction_id e uma vez por vale, confere os limites de
--     novo e entrega os dobrões por dobroes_purchases (kind 'ad', token ad:<transaction_id>) →
--     dobroes_pending → dobroes_ack, o mesmo caminho do Prêmio Beta. A milha soma direto no perfil.
--  4. O app consulta ad_reward_status(vale) e roda o recoverPending.
-- O limite conta na aplicação (vale aplicado), não no início: fechar o anúncio não gasta.
-- iPhone: ads_status('ios') volta tudo desligado (o SDK no iPhone fica para depois).

-- ------------------------------------------------------------------ números (ajustáveis)

insert into public.app_config (key, value) values
  ('ads_enabled', 'false'),              -- interruptor geral; nasce desligado
  ('ads_daily_max', '6'),                -- anúncios premiados por conta e dia, somando os lugares
  ('ads_daily_doubloons_max', '375'),    -- dobrões por dia vindos de anúncio
  ('ads_win_double_daily', '2'),
  ('ads_win_double_cap', '150'),
  ('ads_checkin_double_daily', '1'),
  ('ads_challenge_double_daily', '1'),
  ('ads_mile_daily', '2'),
  ('ads_mile_amount', '1'),
  -- e-mails (minúsculos, separados por vírgula) que usam a unidade de teste do Google além das
  -- contas QA. O valor real fica só no banco: o repositório é público.
  ('ads_test_emails', ''),
  -- versão mínima do app Android para receber anúncio (#49, ECA Digital): o app que não lê a
  -- faixa etária pede anúncio personalizado para todos. Vazio = sem exigência. Só o John liga,
  -- depois que a versão com a leitura estiver no ar (ex.: '0.34.7').
  ('ads_min_app_version', '')
on conflict (key) do nothing;

-- ------------------------------------------------------------------ vales

create table if not exists public.ad_vales (
  id             uuid        primary key default gen_random_uuid(),
  user_id        uuid        not null references auth.users(id) on delete cascade,
  place          text        not null check (place in ('win', 'checkin', 'challenge', 'mile')),
  ref            text        not null,
  amount         integer     not null check (amount > 0),
  status         text        not null default 'pending' check (status in ('pending', 'applied')),
  transaction_id text        unique,
  day            date        not null default public.brt_today(),
  applied_day    date,
  created_at     timestamptz not null default now(),
  expires_at     timestamptz not null default now() + interval '15 minutes',
  applied_at     timestamptz
);

-- cada vitória, check-in e desafio dobra uma vez só (a milha conta só pelo limite do dia)
create unique index if not exists ad_vales_one_per_ref
  on public.ad_vales (user_id, place, ref) where status = 'applied' and place <> 'mile';
create index if not exists ad_vales_user_day on public.ad_vales (user_id, applied_day);

-- o cliente não lê nem grava direto: tudo pelas RPCs abaixo
alter table public.ad_vales enable row level security;
revoke all on public.ad_vales from anon, authenticated;

-- ------------------------------------------------------------------ regras

create or replace function public.ads_on()
returns boolean language sql stable security definer set search_path = public as $$
  select coalesce((select value = 'true' from public.app_config where key = 'ads_enabled'), false);
$$;
revoke all on function public.ads_on() from public, anon, authenticated;

-- conta que usa a unidade de teste do Google: QA (allowed_players 'qa') ou e-mail da lista
create or replace function public.ads_test_account(p_user uuid)
returns boolean language sql stable security definer set search_path = public as $$
  select exists (
           select 1 from public.allowed_players a
             left join auth.users u on u.id = p_user
            where a.source = 'qa' and (a.user_id = p_user or a.email = lower(trim(u.email))))
      or exists (
           select 1 from auth.users u
            where u.id = p_user
              and lower(trim(u.email)) = any (
                    string_to_array(replace(lower(coalesce(
                      (select value from public.app_config where key = 'ads_test_emails'), '')), ' ', ''), ',')));
$$;
revoke all on function public.ads_test_account(uuid) from public, anon, authenticated;

-- Gate de versão (#49, ECA Digital): com ads_min_app_version preenchida, só recebe anúncio a conta
-- cujo último device_checks Android tem essa versão ou mais nova (o app antigo não marca a faixa
-- etária). Vazia ou inválida = sem gate. Conta sem registro de aparelho fica de fora.
create or replace function public.ads_version_ok(p_user uuid)
returns boolean language sql stable security definer set search_path = public as $$
  with cfg as (
    select trim(coalesce((select value from public.app_config where key = 'ads_min_app_version'), '')) as minv
  )
  select case
           when (select minv from cfg) !~ '^[0-9]+(\.[0-9]+)*$' then true
           else coalesce((select public.app_version_at_least(d.app_version, (select minv from cfg))
                            from public.device_checks d
                           where d.user_id = p_user and d.platform = 'android'
                           order by d.created_at desc limit 1), false)
         end;
$$;
revoke all on function public.ads_version_ok(uuid) from public, anon, authenticated;

-- Anúncio ligado para esta conta. ads_enabled = 'true' liga para todos; 'test' liga só para as
-- contas de anúncio de teste (QA e ads_test_emails), para o QA testar sem nenhum jogador real ver
-- botão (decisão do John, 19/10); qualquer outro valor desliga.
create or replace function public.ads_on_for(p_user uuid)
returns boolean language sql stable security definer set search_path = public as $$
  select public.ads_version_ok(p_user)
         and case coalesce((select value from public.app_config where key = 'ads_enabled'), 'false')
           when 'true' then true
           when 'test' then p_user is not null and public.ads_test_account(p_user)
           else false
         end;
$$;
revoke all on function public.ads_on_for(uuid) from public, anon, authenticated;

-- o que já foi aplicado hoje: total de anúncios, dobrões e por lugar
create or replace function public.ads_used_today(p_user uuid, p_place text default null)
returns integer language sql stable security definer set search_path = public as $$
  select count(*)::integer from public.ad_vales v
   where v.user_id = p_user and v.status = 'applied' and v.applied_day = public.brt_today()
     and (p_place is null or v.place = p_place);
$$;
revoke all on function public.ads_used_today(uuid, text) from public, anon, authenticated;

create or replace function public.ads_doubloons_left(p_user uuid)
returns integer language sql stable security definer set search_path = public as $$
  select greatest(0, public.config_int('ads_daily_doubloons_max', 375) - coalesce((
    select sum(v.amount)::integer from public.ad_vales v
     where v.user_id = p_user and v.status = 'applied' and v.applied_day = public.brt_today()
       and v.place <> 'mile'), 0));
$$;
revoke all on function public.ads_doubloons_left(uuid) from public, anon, authenticated;

-- limite diário do lugar
create or replace function public.ads_place_daily(p_place text)
returns integer language sql stable security definer set search_path = public as $$
  select case p_place
    when 'win' then public.config_int('ads_win_double_daily', 2)
    when 'checkin' then public.config_int('ads_checkin_double_daily', 1)
    when 'challenge' then public.config_int('ads_challenge_double_daily', 1)
    when 'mile' then public.config_int('ads_mile_daily', 2)
    else 0 end;
$$;
revoke all on function public.ads_place_daily(text) from public, anon, authenticated;

-- cabe mais um anúncio deste lugar hoje (teto geral e do lugar)
create or replace function public.ads_room(p_user uuid, p_place text)
returns boolean language sql stable security definer set search_path = public as $$
  select public.ads_used_today(p_user) < public.config_int('ads_daily_max', 6)
     and public.ads_used_today(p_user, p_place) < public.ads_place_daily(p_place)
     and (p_place = 'mile' or public.ads_doubloons_left(p_user) > 0);
$$;
revoke all on function public.ads_room(uuid, text) from public, anon, authenticated;

-- dobrões do check-in ou do desafio pagos hoje (0 = ainda não feito). O bônus da semana não entra.
create or replace function public.ads_daily_paid(p_user uuid, p_kind text)
returns integer language sql stable security definer set search_path = public as $$
  select coalesce((select c.doubloons from public.daily_claims c
                    where c.user_id = p_user and c.day = public.brt_today() and c.kind = p_kind), 0);
$$;
revoke all on function public.ads_daily_paid(uuid, text) from public, anon, authenticated;

create or replace function public.ads_ref_used(p_user uuid, p_place text, p_ref text)
returns boolean language sql stable security definer set search_path = public as $$
  select exists (select 1 from public.ad_vales v
                  where v.user_id = p_user and v.place = p_place and v.ref = p_ref and v.status = 'applied');
$$;
revoke all on function public.ads_ref_used(uuid, text, text) from public, anon, authenticated;

-- ------------------------------------------------------------------ RPCs do app

-- O que cada lugar ainda pode hoje. Desligado, sem conta ou fora do Android: tudo falso.
-- mile_limit = saldo zero e o limite de anúncio de milha (ou o geral) acabou: o app mostra o aviso.
drop function if exists public.ads_status(text);
create or replace function public.ads_status(p_platform text default 'android')
returns table (enabled boolean, test_ads boolean, win_can boolean, win_cap integer,
               checkin_can boolean, checkin_amount integer, challenge_can boolean,
               challenge_amount integer, mile_can boolean, mile_amount integer, mile_limit boolean)
language plpgsql security definer set search_path = public as $$
declare
  uid uuid := auth.uid();
  on_ boolean;
  dleft integer;
  ck integer;
  ch integer;
  cur integer;
  mile_amt integer := public.config_int('ads_mile_amount', 1);
begin
  on_ := uid is not null and public.ads_on_for(uid) and coalesce(p_platform, 'android') = 'android';
  if not on_ then
    return query select false, false, false, 0, false, 0, false, 0, false, 0, false;
    return;
  end if;
  dleft := public.ads_doubloons_left(uid);
  ck := least(public.ads_daily_paid(uid, 'checkin'), dleft);
  ch := least(public.ads_daily_paid(uid, 'challenge'), dleft);
  cur := public.miles_refresh(uid);
  return query select
    true,
    public.ads_test_account(uid),
    public.ads_room(uid, 'win'),
    least(public.config_int('ads_win_double_cap', 150), dleft),
    ck > 0 and public.ads_room(uid, 'checkin')
      and not public.ads_ref_used(uid, 'checkin', public.brt_today()::text),
    ck,
    ch > 0 and public.ads_room(uid, 'challenge')
      and not public.ads_ref_used(uid, 'challenge', public.brt_today()::text),
    ch,
    cur = 0 and public.ads_room(uid, 'mile'),
    mile_amt,
    cur = 0 and not public.ads_room(uid, 'mile');
end $$;
revoke all on function public.ads_status(text) from public, anon;
grant execute on function public.ads_status(text) to authenticated;

-- Pede um vale. p_ref: 'ai:<id gerado no aparelho>' ou 'online:<id da sala>' no lugar win
-- (ignorado nos outros: vale o dia). p_amount: só no lugar win (dobrões da vitória; o servidor
-- corta no teto). Retorna status ok | off | limit | not_eligible | already, o vale e o valor.
drop function if exists public.ad_reward_start(text, text, integer);
create or replace function public.ad_reward_start(p_place text, p_ref text default null, p_amount integer default null)
returns table (status text, vale uuid, amount integer)
language plpgsql security definer set search_path = public as $$
declare
  uid uuid := auth.uid();
  ref_ text;
  amt integer;
  cur integer;
  room_id uuid;
  v uuid;
begin
  if uid is null or not public.ads_on_for(uid) then
    return query select 'off'::text, null::uuid, 0; return;
  end if;
  if p_place not in ('win', 'checkin', 'challenge', 'mile') then
    return query select 'not_eligible'::text, null::uuid, 0; return;
  end if;
  perform pg_advisory_xact_lock(hashtext('dobroes:' || uid::text));

  -- freio contra vale em massa (cada um vence em 15 min de qualquer jeito)
  if (select count(*) from public.ad_vales x where x.user_id = uid and x.day = public.brt_today()) >= 40 then
    return query select 'limit'::text, null::uuid, 0; return;
  end if;
  if not public.ads_room(uid, p_place) then
    return query select 'limit'::text, null::uuid, 0; return;
  end if;

  if p_place = 'win' then
    ref_ := left(trim(coalesce(p_ref, '')), 80);
    if coalesce(p_amount, 0) <= 0 or ref_ !~ '^(ai|online):[A-Za-z0-9-]{8,64}$' then
      return query select 'not_eligible'::text, null::uuid, 0; return;
    end if;
    if ref_ like 'online:%' then
      -- a sala existe, ele estava nela e deu tiro ou usou habilidade (sem combate não dobra)
      begin
        room_id := substr(ref_, 8)::uuid;
      exception when invalid_text_representation then
        return query select 'not_eligible'::text, null::uuid, 0; return;
      end;
      if not exists (select 1 from public.online_matches m
                      where m.id = room_id and (m.host_id = uid or m.guest_id = uid))
         or not exists (select 1 from public.online_messages om
                         where om.match_id = room_id and om.sender_id = uid
                           and (om.body like 'ACT|%' or om.body like 'ABIL|%')) then
        return query select 'not_eligible'::text, null::uuid, 0; return;
      end if;
    end if;
    amt := least(p_amount, public.config_int('ads_win_double_cap', 150), public.ads_doubloons_left(uid));
  elsif p_place in ('checkin', 'challenge') then
    ref_ := public.brt_today()::text;
    amt := least(public.ads_daily_paid(uid, p_place), public.ads_doubloons_left(uid));
  else
    ref_ := public.brt_today()::text;
    cur := public.miles_refresh(uid);
    if cur <> 0 then
      return query select 'not_eligible'::text, null::uuid, 0; return;
    end if;
    amt := least(public.config_int('ads_mile_amount', 1), public.config_int('miles_cap', 50) - cur);
  end if;

  if coalesce(amt, 0) <= 0 then
    return query select 'not_eligible'::text, null::uuid, 0; return;
  end if;
  if p_place <> 'mile' and public.ads_ref_used(uid, p_place, ref_) then
    return query select 'already'::text, null::uuid, 0; return;
  end if;

  insert into public.ad_vales (user_id, place, ref, amount) values (uid, p_place, ref_, amt)
  returning id into v;
  return query select 'ok'::text, v, amt;
end $$;
revoke all on function public.ad_reward_start(text, text, integer) from public, anon;
grant execute on function public.ad_reward_start(text, text, integer) to authenticated;

-- Situação de um vale da própria conta: pending | applied | expired | unknown
drop function if exists public.ad_reward_status(uuid);
create or replace function public.ad_reward_status(p_vale uuid)
returns table (status text, place text, amount integer)
language sql stable security definer set search_path = public as $$
  select coalesce(
           (select case when v.status = 'applied' then 'applied'
                        when v.expires_at < now() then 'expired'
                        else 'pending' end
              from public.ad_vales v where v.id = p_vale and v.user_id = auth.uid()),
           'unknown'),
         (select v.place from public.ad_vales v where v.id = p_vale and v.user_id = auth.uid()),
         coalesce((select v.amount from public.ad_vales v where v.id = p_vale and v.user_id = auth.uid()), 0);
$$;
revoke all on function public.ad_reward_status(uuid) from public, anon;
grant execute on function public.ad_reward_status(uuid) to authenticated;

-- ------------------------------------------------------------------ aplicação (só a Edge)

-- Chamada pela Edge ads-reward depois de conferir a assinatura do Google. Só service_role.
-- Retorna applied | already (mesmo transaction_id) | unknown_vale | user_mismatch | used |
-- expired | off | limit | cap. O valor sai do vale, nunca do callback.
drop function if exists public.apply_ad_reward(uuid, text, text);
create or replace function public.apply_ad_reward(p_vale uuid, p_transaction text, p_user text)
returns table (status text, amount integer)
language plpgsql security definer set search_path = public as $$
declare
  owner uuid;
  v public.ad_vales%rowtype;
  amt integer;
  cur integer;
begin
  if p_transaction is null or length(p_transaction) = 0 or length(p_transaction) > 200 then
    return query select 'unknown_vale'::text, 0; return;
  end if;
  if exists (select 1 from public.ad_vales x where x.transaction_id = p_transaction) then
    return query select 'already'::text, 0; return;
  end if;
  select x.user_id into owner from public.ad_vales x where x.id = p_vale;
  if owner is null then
    return query select 'unknown_vale'::text, 0; return;
  end if;
  perform pg_advisory_xact_lock(hashtext('dobroes:' || owner::text));

  -- de novo sob o lock: dois callbacks ao mesmo tempo
  if exists (select 1 from public.ad_vales x where x.transaction_id = p_transaction) then
    return query select 'already'::text, 0; return;
  end if;
  select * into v from public.ad_vales x where x.id = p_vale for update;
  if p_user is distinct from v.user_id::text then
    return query select 'user_mismatch'::text, 0; return;
  end if;
  if v.status = 'applied' then return query select 'used'::text, 0; return; end if;
  if v.expires_at < now() then return query select 'expired'::text, 0; return; end if;
  if not public.ads_on_for(v.user_id) then return query select 'off'::text, 0; return; end if;
  if not public.ads_room(v.user_id, v.place)
     or (v.place <> 'mile' and public.ads_ref_used(v.user_id, v.place, v.ref)) then
    return query select 'limit'::text, 0; return;
  end if;

  if v.place = 'mile' then
    cur := public.miles_refresh(v.user_id);
    amt := least(v.amount, public.config_int('miles_cap', 50) - cur);
    if amt <= 0 then return query select 'cap'::text, 0; return; end if;
    update public.profiles set nautical_miles = nautical_miles + amt where id = v.user_id;
  else
    amt := least(v.amount, public.ads_doubloons_left(v.user_id));
    if amt <= 0 then return query select 'limit'::text, 0; return; end if;
    insert into public.dobroes_purchases (purchase_token, user_id, product_id, order_id, kind, premio, credited)
    values ('ad:' || p_transaction, v.user_id, 'ad_' || v.place, null, 'ad', false, amt);
  end if;

  update public.ad_vales x
     set status = 'applied', transaction_id = p_transaction, amount = amt,
         applied_day = public.brt_today(), applied_at = now()
   where x.id = v.id;
  return query select 'applied'::text, amt;
end $$;
revoke all on function public.apply_ad_reward(uuid, text, text) from public, anon, authenticated;
grant execute on function public.apply_ad_reward(uuid, text, text) to service_role;
