-- Convide um amigo (#44). Rode depois de online.sql, push.sql, economy.sql, dobroes_saldo.sql e
-- test_accounts.sql. Idempotente. Migração: `referral` (mesmo conteúdo).
--
-- Quem convida mostra um código de 6 caracteres (sem 0/O/1/I); o convidado o digita em Amigos. Sem link
-- mágico: ler o código da instalação exigiria a Install Referrer API (dependência nova).
--  * Resgate: os dois viram amigos na hora (friendships 'accepted'). Uma vez por convidado na vida, conta
--    de até referral_new_account_days dias, nunca o próprio código, nunca conta is_test, e conta QA só
--    com referral_allow_qa = 'true' (o QA liga durante o teste e desliga no fim).
--  * Pagamento: quando o convidado termina a 1ª partida online com combate dos DOIS lados (mesma regra do
--    award_online_match), cada um ganha 100 dobrões por dobroes_apply com reason 'referral'. Não entra no
--    teto de dobroes_online_daily_cap (que protege só reason = 'online').
--  * Quem convida: no máximo referral_daily_limit prêmio por dia (Brasília) e referral_total_limit no total.
--    Passou do limite, a amizade fica e o convidado recebe; quem convidou fica 'over_limit'.
--  * Ordem de locks: referral:<uid> (do menor para o maior uuid quando são dois) e depois dobroes:<uid>
--    (dobroes_apply), como welcome:/daily:.

insert into public.app_config (key, value) values
  ('referral_enabled', 'true'),
  ('referral_doubloons_inviter', '100'),
  ('referral_doubloons_invitee', '100'),
  ('referral_daily_limit', '1'),
  ('referral_total_limit', '10'),
  ('referral_new_account_days', '7'),
  ('referral_bad_tries_per_day', '10'),
  ('referral_allow_qa', 'false')
on conflict (key) do nothing;

-- ------------------------------------------------------------------ tabelas

create table if not exists public.referral_codes (
  user_id    uuid        primary key references auth.users(id) on delete cascade,
  code       text        not null unique,
  created_at timestamptz not null default now()
);

alter table public.referral_codes enable row level security;
drop policy if exists "convite: ler o proprio codigo" on public.referral_codes;
create policy "convite: ler o proprio codigo"
  on public.referral_codes for select
  using (auth.uid() = user_id);
revoke all on public.referral_codes from anon, authenticated;
grant select on public.referral_codes to authenticated;

create table if not exists public.referrals (
  invitee_id       uuid        primary key references auth.users(id) on delete cascade,
  inviter_id       uuid        not null references auth.users(id) on delete cascade,
  code             text        not null,
  redeemed_at      timestamptz not null default now(),
  settled_match    uuid,
  settled_at       timestamptz,
  invitee_paid     integer     not null default 0,
  inviter_paid     integer     not null default 0,
  inviter_paid_at  timestamptz,
  inviter_status   text        not null default 'pending' check (inviter_status in ('pending', 'paid', 'over_limit', 'qa')),
  inviter_seen     boolean     not null default false,
  check (invitee_id <> inviter_id)
);
create index if not exists referrals_inviter_idx on public.referrals (inviter_id, redeemed_at);

alter table public.referrals enable row level security;
drop policy if exists "convite: ler os meus" on public.referrals;
create policy "convite: ler os meus"
  on public.referrals for select
  using (auth.uid() = invitee_id or auth.uid() = inviter_id);
revoke all on public.referrals from anon, authenticated;
grant select on public.referrals to authenticated;

-- tentativas com código inválido por conta por dia (só as funções escrevem)
create table if not exists public.referral_tries (
  user_id uuid    not null references auth.users(id) on delete cascade,
  day     date    not null,
  n       integer not null default 0,
  primary key (user_id, day)
);
alter table public.referral_tries enable row level security;
revoke all on public.referral_tries from anon, authenticated;

-- ------------------------------------------------------------------ internas

-- conta que não vale para convite: is_test sempre; QA só com referral_allow_qa = 'true'
create or replace function public.referral_blocked(p_user uuid)
returns boolean language sql stable security definer set search_path = public as $$
  select public.is_test_user(p_user)
      or (public.is_qa_account(p_user)
          and coalesce((select a.value from public.app_config a where a.key = 'referral_allow_qa'), 'false') <> 'true');
$$;
revoke all on function public.referral_blocked(uuid) from public, anon, authenticated;

-- Paga quem convidou, se estiver no limite e tiver carteira. Quem chama já segura referral:<inviter>.
-- Devolve o status: paid | over_limit | qa | pending (sem carteira, fica para depois).
create or replace function public.referral_pay_inviter(p_invitee uuid)
returns text language plpgsql security definer set search_path = public as $$
declare
  r public.referrals%rowtype;
  amt integer := public.config_int('referral_doubloons_inviter', 100);
  used_day integer;
  used_total integer;
  invitee_name text;
begin
  select * into r from public.referrals x where x.invitee_id = p_invitee;
  if r.invitee_id is null then return 'pending'; end if;
  if public.referral_blocked(r.inviter_id) then
    update public.referrals set inviter_status = 'qa' where invitee_id = p_invitee;
    return 'qa';
  end if;
  if not public.dobroes_has_wallet(r.inviter_id) then
    return 'pending';
  end if;

  select count(*)::integer into used_total from public.referrals x
   where x.inviter_id = r.inviter_id and x.inviter_status = 'paid';
  select count(*)::integer into used_day from public.referrals x
   where x.inviter_id = r.inviter_id and x.inviter_status = 'paid'
     and (x.inviter_paid_at at time zone 'America/Sao_Paulo')::date = public.brt_today();
  if used_day >= public.config_int('referral_daily_limit', 1)
     or used_total >= public.config_int('referral_total_limit', 10) then
    update public.referrals set inviter_status = 'over_limit' where invitee_id = p_invitee;
    return 'over_limit';
  end if;

  perform public.dobroes_apply(r.inviter_id, amt, 'referral', p_invitee::text);
  update public.referrals
     set inviter_status = 'paid', inviter_paid = amt, inviter_paid_at = now()
   where invitee_id = p_invitee;
  select coalesce(p.username, '?') into invitee_name from public.profiles p where p.id = p_invitee;
  perform public.queue_push(r.inviter_id, 'referral_reward', 'Seu convite rendeu',
    coalesce(invitee_name, 'Seu amigo') || ' jogou a primeira partida online. Você ganhou ◆' || amt || '.');
  return 'paid';
end $$;
revoke all on function public.referral_pay_inviter(uuid) from public, anon, authenticated;

-- ------------------------------------------------------------------ RPCs

create or replace function public.referral_my_code()
returns table (code text)
language plpgsql security definer set search_path = public as $$
#variable_conflict use_column
declare
  uid uuid := auth.uid();
  c text;
  alphabet constant text := 'ABCDEFGHJKLMNPQRSTUVWXYZ23456789';  -- 32 símbolos, sem 0/O/1/I
  bytes bytea;
  i integer;
  tries integer := 0;
begin
  if uid is null then
    raise exception 'referral_auth';
  end if;
  perform pg_advisory_xact_lock(hashtext('referral:' || uid::text));
  select rc.code into c from public.referral_codes rc where rc.user_id = uid;
  if c is not null then
    return query select c;
    return;
  end if;
  loop
    tries := tries + 1;
    bytes := decode(replace(gen_random_uuid()::text, '-', ''), 'hex');
    c := '';
    for i in 0..5 loop
      c := c || substr(alphabet, (get_byte(bytes, i) % 32) + 1, 1);
    end loop;
    begin
      insert into public.referral_codes (user_id, code) values (uid, c);
      exit;
    exception when unique_violation then
      if tries >= 20 then raise exception 'referral_code_collision'; end if;
    end;
  end loop;
  return query select c;
end $$;
revoke all on function public.referral_my_code() from public, anon;
grant execute on function public.referral_my_code() to authenticated;

-- status: ok | invalid | self | already | too_old | not_eligible | disabled | too_many
create or replace function public.referral_redeem(p_code text)
returns table (status text, inviter_name text)
language plpgsql security definer set search_path = public as $$
#variable_conflict use_column
declare
  uid uuid := auth.uid();
  c text := upper(regexp_replace(coalesce(p_code, ''), '[\s-]', '', 'g'));
  inviter uuid;
  created timestamptz;
  my_name text;
  inv_name text;
  tries integer;
  fid uuid;
  fstatus text;
  qa_ok boolean;
begin
  if uid is null then
    raise exception 'referral_auth';
  end if;
  perform pg_advisory_xact_lock(hashtext('referral:' || uid::text));

  if coalesce((select a.value from public.app_config a where a.key = 'referral_enabled'), 'false') <> 'true' then
    return query select 'disabled'::text, ''::text; return;
  end if;
  if exists (select 1 from public.referrals r where r.invitee_id = uid) then
    return query select 'already'::text, ''::text; return;
  end if;

  select t.n into tries from public.referral_tries t where t.user_id = uid and t.day = public.brt_today();
  if coalesce(tries, 0) >= public.config_int('referral_bad_tries_per_day', 10) then
    return query select 'too_many'::text, ''::text; return;
  end if;

  select rc.user_id into inviter from public.referral_codes rc where rc.code = c;
  if inviter is null then
    insert into public.referral_tries (user_id, day, n) values (uid, public.brt_today(), 1)
      on conflict (user_id, day) do update set n = public.referral_tries.n + 1;
    return query select 'invalid'::text, ''::text; return;
  end if;
  if inviter = uid then
    return query select 'self'::text, ''::text; return;
  end if;

  if public.referral_blocked(uid) or public.referral_blocked(inviter) then
    return query select 'not_eligible'::text, ''::text; return;
  end if;
  -- QA liberado pelo interruptor ignora a idade da conta (as contas QA são antigas); conta real não
  qa_ok := public.is_qa_account(uid);
  select u.created_at into created from auth.users u where u.id = uid;
  if not qa_ok and (created is null
       or created < now() - make_interval(days => public.config_int('referral_new_account_days', 7))) then
    return query select 'too_old'::text, ''::text; return;
  end if;

  select coalesce(p.username, '?') into my_name from public.profiles p where p.id = uid;
  select coalesce(p.username, '?') into inv_name from public.profiles p where p.id = inviter;

  -- amizade na hora: reaproveita a linha em qualquer sentido (unique requester/addressee) e a aceita
  select f.id, f.status into fid, fstatus from public.friendships f
   where (f.requester_id = uid and f.addressee_id = inviter) or (f.requester_id = inviter and f.addressee_id = uid)
   limit 1;
  if fid is null then
    insert into public.friendships (requester_id, addressee_id, requester_username, addressee_username, status)
    values (uid, inviter, my_name, inv_name, 'accepted')
    on conflict (requester_id, addressee_id) do update set status = 'accepted';
  elsif fstatus <> 'accepted' then
    update public.friendships set status = 'accepted' where id = fid;
  end if;

  insert into public.referrals (invitee_id, inviter_id, code) values (uid, inviter, c);
  return query select 'ok'::text, inv_name;
end $$;
revoke all on function public.referral_redeem(text) from public, anon;
grant execute on function public.referral_redeem(text) to authenticated;

-- status: paid | pending | none | already | not_eligible | no_wallet. `pending` = esta sala ainda não vale
-- (sem combate dos dois lados): o app tenta de novo na próxima partida online.
create or replace function public.referral_settle(p_match_id uuid)
returns table (status text, doubloons integer, balance integer)
language plpgsql security definer set search_path = public as $$
#variable_conflict use_column
declare
  uid uuid := auth.uid();
  r public.referrals%rowtype;
  m public.online_matches%rowtype;
  opp uuid;
  first_lock uuid;
  second_lock uuid;
  pay integer := public.config_int('referral_doubloons_invitee', 100);
  bal integer;
  ist text;
begin
  if uid is null then
    raise exception 'referral_auth';
  end if;

  -- leitura sem lock só para saber quem convidou; o lock vem em ordem fixa mais abaixo
  select * into r from public.referrals x where x.invitee_id = uid;
  if r.invitee_id is null then
    return query select 'none'::text, 0, 0; return;
  end if;
  -- lê o saldo uma vez (o razão é a verdade); sem perfil, 0
  select p.credits into bal from public.profiles p where p.id = uid;
  if r.settled_match is not null then
    return query select 'already'::text, 0, coalesce(bal, 0); return;
  end if;
  if coalesce((select a.value from public.app_config a where a.key = 'referral_enabled'), 'false') <> 'true'
     or public.referral_blocked(uid) then
    return query select 'not_eligible'::text, 0, coalesce(bal, 0); return;
  end if;

  select * into m from public.online_matches om where om.id = p_match_id;
  if m.id is null or m.guest_id is null or (m.host_id <> uid and m.guest_id <> uid) or m.created_at < r.redeemed_at then
    return query select 'pending'::text, 0, coalesce(bal, 0); return;
  end if;
  opp := case when m.host_id = uid then m.guest_id else m.host_id end;
  if not exists (select 1 from public.online_messages om where om.match_id = p_match_id and om.sender_id = uid
                    and (om.body like 'ACT|%' or om.body like 'ABIL|%'))
     or not exists (select 1 from public.online_messages om where om.match_id = p_match_id and om.sender_id = opp
                    and (om.body like 'ACT|%' or om.body like 'ABIL|%')) then
    return query select 'pending'::text, 0, coalesce(bal, 0); return;
  end if;

  if not public.dobroes_has_wallet(uid) then
    return query select 'no_wallet'::text, 0, 0; return;
  end if;

  -- trava o convidado e quem convidou (limite diário e total sem corrida entre convidados), sempre do menor
  -- para o maior uuid para duas pessoas que se convidaram não travarem uma à outra
  first_lock := least(uid, r.inviter_id);
  second_lock := greatest(uid, r.inviter_id);
  perform pg_advisory_xact_lock(hashtext('referral:' || first_lock::text));
  perform pg_advisory_xact_lock(hashtext('referral:' || second_lock::text));

  -- relê sob o lock: outra chamada pode ter fechado enquanto esperava
  select * into r from public.referrals x where x.invitee_id = uid;
  if r.settled_match is not null then
    return query select 'already'::text, 0, coalesce(bal, 0); return;
  end if;

  -- Sem deadlock: todo caminho que pega dobroes:<inviter> (aqui via referral_pay_inviter, ou referral_status)
  -- já segura referral:<inviter>, e referral: vem sempre antes de dobroes:. Os dois referral: de duas pessoas
  -- que se convidaram são tomados em ordem crescente de uuid.
  bal := public.dobroes_apply(uid, pay, 'referral', 'invitee');
  update public.referrals
     set settled_match = p_match_id, settled_at = now(), invitee_paid = pay
   where invitee_id = uid;
  ist := public.referral_pay_inviter(uid);

  return query select 'paid'::text, pay, bal;
end $$;
revoke all on function public.referral_settle(uuid) from public, anon;
grant execute on function public.referral_settle(uuid) to authenticated;

-- convites que eu fiz. state: waiting (o amigo ainda não jogou) | paid | pending (sem carteira) |
-- over_limit | qa. Antes de listar, paga os `pending` de quem já tem carteira.
create or replace function public.referral_status()
returns table (invitee_name text, state text, amount integer, unseen boolean, redeemed_at timestamptz)
language plpgsql security definer set search_path = public as $$
#variable_conflict use_column
declare
  uid uuid := auth.uid();
  x record;
begin
  if uid is null then
    raise exception 'referral_auth';
  end if;
  perform pg_advisory_xact_lock(hashtext('referral:' || uid::text));
  if public.dobroes_has_wallet(uid) then
    for x in select r.invitee_id from public.referrals r
              where r.inviter_id = uid and r.inviter_status = 'pending' and r.settled_match is not null
              order by r.settled_at loop
      perform public.referral_pay_inviter(x.invitee_id);
    end loop;
  end if;
  return query
    select coalesce(p.username, '?'),
           case when r.settled_match is null then 'waiting' else r.inviter_status end,
           r.inviter_paid,
           (r.inviter_status = 'paid' and not r.inviter_seen),
           r.redeemed_at
      from public.referrals r
      left join public.profiles p on p.id = r.invitee_id
     where r.inviter_id = uid
     order by r.redeemed_at desc;
end $$;
revoke all on function public.referral_status() from public, anon;
grant execute on function public.referral_status() to authenticated;

-- o app mostrou o popup "Seu convite rendeu": marca como visto
create or replace function public.referral_ack()
returns void
language plpgsql security definer set search_path = public as $$
begin
  if auth.uid() is null then
    raise exception 'referral_auth';
  end if;
  update public.referrals set inviter_seen = true
   where inviter_id = auth.uid() and inviter_status = 'paid' and not inviter_seen;
end $$;
revoke all on function public.referral_ack() from public, anon;
grant execute on function public.referral_ack() to authenticated;

notify pgrst, 'reload schema';
