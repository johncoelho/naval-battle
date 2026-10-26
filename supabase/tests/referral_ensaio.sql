-- Ensaio que desfaz tudo (#44): convide um amigo (supabase/referral.sql).
-- Rodar no SQL Editor do projeto cwtslesnthbenxswdcbv. Antes de aplicar a migração, cole o texto de
-- referral.sql e este arquivo na mesma execução: o raise no fim cancela a transação inteira, DDL
-- inclusive. Cria contas descartáveis dentro do bloco (nenhuma conta real nem QA é tocada).
--
-- Resultado esperado (cada item vira "nome=valor"):
--   codigo=ok/igual            código de 6 caracteres válido e estável
--   resgate=ok/amigos=1        código em minúscula, com espaço e hífen; amizade criada accepted
--   resgate_2=already          segundo resgate do mesmo convidado
--   proprio=self               o próprio código
--   antiga=too_old             conta de 10 dias
--   is_test=not_eligible       robô da Google
--   qa=not_eligible            conta QA sem referral_allow_qa
--   qa_liberado=ok             com referral_allow_qa = true (e a idade não vale para QA)
--   invalido=invalid           código que não existe; depois de 10 erros, too_many
--   pendente=ok/amizades=1/accepted   pedido pendente vira accepted sem duplicar a linha
--   sem_combate=pending        sala sem tiro dos dois lados não paga
--   pago=paid/100/inviter=paid/100    convidado e quem convidou recebem uma vez
--   pago_2=already/ledger=1/1  idempotente
--   teto_online=paid           500 de 'online' no dia não atrapalha o prêmio
--   limite_diario=paid/over_limit     2º convite no dia: convidado paga, quem convidou não
--   dia_seguinte=paid          no outro dia paga de novo
--   limite_total=10 pagos/over_limit  11º convite do mesmo jogador não paga quem convidou
--   inviter_sem_carteira=pending/paid depois de abrir a carteira, referral_status paga e marca não visto
--   visto=false                referral_ack zera o aviso
--   convidado_sem_carteira=no_wallet/gravado=f  nada gravado
--   inviter_virou_teste=paid/qa/0  convidado paga, quem convidou (virou is_test) não
--   sem_convite=none           settle de quem nunca resgatou
--   soma_razao=igual           soma do razão = saldo nas contas pagas
--   privilegios=...            anon sem execute; authenticated com; internas e tabelas fechadas
create or replace function pg_temp.as_user(u uuid) returns void language sql as $$
  select set_config('request.jwt.claims', json_build_object('sub', u, 'role', 'authenticated')::text, true);
$$;

-- sala com tiros: ambos=true cria ACT dos dois lados; false só do primeiro
create or replace function pg_temp.mkmatch(h uuid, g uuid, ambos boolean) returns uuid language plpgsql as $$
declare mid uuid := gen_random_uuid();
begin
  insert into public.online_matches (id, host_id, guest_id, host_name, guest_name, status)
  values (mid, h, g, 'h', 'g', 'finished');
  insert into public.online_messages (match_id, sender_id, body) values (mid, h, 'ACT|1,1');
  if ambos then
    insert into public.online_messages (match_id, sender_id, body) values (mid, g, 'ABIL|X');
  end if;
  return mid;
end $$;

do $$
declare
  inv uuid := gen_random_uuid(); v1 uuid := gen_random_uuid(); v2 uuid := gen_random_uuid();
  v3 uuid := gen_random_uuid(); u_old uuid := gen_random_uuid(); u_test uuid := gen_random_uuid();
  u_qa uuid := gen_random_uuid(); v_pend uuid := gen_random_uuid(); v_nowal uuid := gen_random_uuid();
  inv2 uuid := gen_random_uuid(); w1 uuid := gen_random_uuid(); inv3 uuid := gen_random_uuid(); w2 uuid := gen_random_uuid();
  d1 uuid := gen_random_uuid(); inv4 uuid := gen_random_uuid(); w3 uuid := gen_random_uuid(); opp uuid := gen_random_uuid(); stranger uuid := gen_random_uuid();
  vs uuid[] := '{}'; x uuid; i int;
  code text; code2 text; r record; out text := ''; n int; mid uuid; st text; paid_n int; ldg int;
begin
  insert into auth.users (id, created_at) values
    (inv, now()), (v1, now()), (v2, now()), (v3, now()), (u_old, now() - interval '10 days'), (u_test, now()),
    (u_qa, now() - interval '30 days'), (v_pend, now()), (v_nowal, now()), (inv2, now()), (w1, now()),
    (inv3, now()), (w2, now()), (d1, now()), (inv4, now()), (w3, now()), (opp, now()), (stranger, now());
  update public.profiles set username = 'ens-' || substr(id::text, 1, 8)
   where id in (d1, inv, v1, v2, v3, u_old, u_test, u_qa, v_pend, v_nowal, inv2, w1, inv3, w2, inv4, w3, opp, stranger);
  update public.profiles set is_test = true where id = u_test;
  insert into public.allowed_players (user_id, source) values (u_qa, 'qa');
  perform public.dobroes_open_core(u, 0, '{}'::text[], 'app')
    from unnest(array[d1, inv, v1, v2, v3, u_old, u_test, u_qa, v_pend, inv3, w1, w2, inv4, w3, opp, stranger]) u;
  -- inv2 e v_nowal ficam sem carteira de propósito
  update public.app_config set value = 'true' where key = 'referral_enabled';
  update public.app_config set value = 'false' where key = 'referral_allow_qa';

  -- código
  perform pg_temp.as_user(inv);
  select rc.code into code from public.referral_my_code() rc;
  select rc.code into code2 from public.referral_my_code() rc;
  out := out || format('codigo=%s/%s | ', case when code ~ '^[A-HJ-NP-Z2-9]{6}$' then 'ok' else 'ruim:' || code end,
    case when code = code2 then 'igual' else 'diferente' end);

  -- resgate válido (minúscula, espaço e hífen), amizade na hora
  perform pg_temp.as_user(v1);
  select * into r from public.referral_redeem(lower(substr(code, 1, 3)) || ' - ' || lower(substr(code, 4)));
  out := out || format('resgate=%s/amigos=%s | ', r.status,
    (select count(*) from public.friendships where status = 'accepted'
       and ((requester_id = v1 and addressee_id = inv) or (requester_id = inv and addressee_id = v1))));
  select * into r from public.referral_redeem(code);
  out := out || format('resgate_2=%s | ', r.status);

  -- próprio código, conta antiga, is_test, QA
  perform pg_temp.as_user(inv);
  select * into r from public.referral_redeem(code);
  out := out || format('proprio=%s | ', r.status);
  perform pg_temp.as_user(u_old);
  select * into r from public.referral_redeem(code);
  out := out || format('antiga=%s | ', r.status);
  perform pg_temp.as_user(u_test);
  select * into r from public.referral_redeem(code);
  out := out || format('is_test=%s | ', r.status);
  perform pg_temp.as_user(u_qa);
  select * into r from public.referral_redeem(code);
  out := out || format('qa=%s | ', r.status);
  update public.app_config set value = 'true' where key = 'referral_allow_qa';
  select * into r from public.referral_redeem(code);
  out := out || format('qa_liberado=%s | ', r.status);
  update public.app_config set value = 'false' where key = 'referral_allow_qa';

  -- código inválido e limite de erros
  perform pg_temp.as_user(v2);
  select * into r from public.referral_redeem('ZZZZZZ');
  out := out || format('invalido=%s', r.status);
  for i in 1..10 loop
    select * into r from public.referral_redeem('AAAAA' || i::text);
  end loop;
  select * into r from public.referral_redeem(code);
  out := out || format('/%s | ', r.status);
  delete from public.referral_tries where user_id = v2;

  -- pedido de amizade pendente (v_pend -> inv) vira accepted sem duplicar
  insert into public.friendships (requester_id, addressee_id, requester_username, addressee_username, status)
  values (inv, v_pend, 'a', 'b', 'pending');
  perform pg_temp.as_user(v_pend);
  select * into r from public.referral_redeem(code);
  out := out || format('pendente=%s/amizades=%s/%s | ', r.status,
    (select count(*) from public.friendships where (requester_id = v_pend and addressee_id = inv) or (requester_id = inv and addressee_id = v_pend)),
    (select status from public.friendships where requester_id = inv and addressee_id = v_pend));

  -- settle sem combate dos dois lados não paga; sem convite, none
  mid := pg_temp.mkmatch(v1, opp, false);
  perform pg_temp.as_user(v1);
  select * into r from public.referral_settle(mid);
  out := out || format('sem_combate=%s/ledger=%s | ', r.status,
    (select count(*) from public.dobroes_ledger where user_id in (v1, inv) and reason = 'referral'));
  perform pg_temp.as_user(stranger);
  select * into r from public.referral_settle(mid);
  out := out || format('sem_convite=%s | ', r.status);

  -- com combate: os dois recebem 100, uma vez
  mid := pg_temp.mkmatch(v1, opp, true);
  perform pg_temp.as_user(v1);
  select * into r from public.referral_settle(mid);
  out := out || format('pago=%s/%s/inviter=%s/%s | ', r.status, r.doubloons,
    (select inviter_status from public.referrals where invitee_id = v1),
    (select inviter_paid from public.referrals where invitee_id = v1));
  select * into r from public.referral_settle(mid);
  out := out || format('pago_2=%s/ledger=%s/%s | ', r.status,
    (select count(*) from public.dobroes_ledger where user_id = v1 and reason = 'referral'),
    (select count(*) from public.dobroes_ledger where user_id = inv and reason = 'referral'));

  -- teto online cheio: 500 de 'online' hoje e o prêmio paga mesmo assim
  perform public.dobroes_apply(v3, 500, 'online', 'ens-teto');
  perform pg_temp.as_user(v3);
  select * into r from public.referral_redeem(code);
  mid := pg_temp.mkmatch(v3, opp, true);
  select * into r from public.referral_settle(mid);
  out := out || format('teto_online=%s/%s | ', r.status, r.doubloons);
  -- v3 era o 2º convite do dia de inv: o convidado pagou, quem convidou não
  out := out || format('limite_diario=%s/%s | ', r.status, (select inviter_status from public.referrals where invitee_id = v3));

  -- no dia seguinte (recua o pagamento de v1 em 1 dia): outro convidado paga quem convidou de novo
  update public.referrals set inviter_paid_at = inviter_paid_at - interval '1 day' where invitee_id = v1;
  perform pg_temp.as_user(d1);
  perform public.referral_redeem(code);
  select * into r from public.referral_settle(pg_temp.mkmatch(d1, opp, true));
  out := out || format('dia_seguinte=%s/%s | ', r.status, (select inviter_status from public.referrals where invitee_id = d1));

  -- limite total (10): relaxa o diário e convida mais gente com a mesma pessoa
  update public.app_config set value = '100' where key = 'referral_daily_limit';
  for i in 1..9 loop
    x := gen_random_uuid(); vs := vs || x;
    insert into auth.users (id, created_at) values (x, now());
    update public.profiles set username = 'ens-' || substr(x::text, 1, 8) where id = x;
    perform public.dobroes_open_core(x, 0, '{}'::text[], 'app');
    perform pg_temp.as_user(x);
    perform public.referral_redeem(code);
    perform public.referral_settle(pg_temp.mkmatch(x, opp, true));
  end loop;
  select count(*) into paid_n from public.referrals where inviter_id = inv and inviter_status = 'paid';
  out := out || format('limite_total=%s pagos/%s | ', paid_n,
    (select inviter_status from public.referrals where invitee_id = vs[9]));
  update public.app_config set value = '1' where key = 'referral_daily_limit';

  -- quem convidou sem carteira: fica pending e recebe quando abrir a carteira
  perform pg_temp.as_user(inv2);
  select rc.code into code2 from public.referral_my_code() rc;
  perform pg_temp.as_user(w1);
  perform public.referral_redeem(code2);
  select * into r from public.referral_settle(pg_temp.mkmatch(w1, opp, true));
  st := (select inviter_status from public.referrals where invitee_id = w1);
  perform public.dobroes_open_core(inv2, 0, '{}'::text[], 'app');
  perform pg_temp.as_user(inv2);
  select * into r from public.referral_status();
  out := out || format('inviter_sem_carteira=%s/%s/unseen=%s | ', st, r.state, r.unseen);
  perform public.referral_ack();
  select * into r from public.referral_status();
  out := out || format('visto=%s | ', r.unseen);

  -- convidado sem carteira: no_wallet e nada gravado
  perform pg_temp.as_user(inv3);
  select rc.code into code2 from public.referral_my_code() rc;
  perform pg_temp.as_user(v_nowal);
  perform public.referral_redeem(code2);
  select * into r from public.referral_settle(pg_temp.mkmatch(v_nowal, opp, true));
  out := out || format('convidado_sem_carteira=%s/gravado=%s | ', r.status,
    (select settled_match is not null from public.referrals where invitee_id = v_nowal));

  -- quem convidou virou is_test entre o resgate e o pagamento
  perform pg_temp.as_user(inv4);
  select rc.code into code2 from public.referral_my_code() rc;
  perform pg_temp.as_user(w3);
  perform public.referral_redeem(code2);
  update public.profiles set is_test = true where id = inv4;
  select * into r from public.referral_settle(pg_temp.mkmatch(w3, opp, true));
  out := out || format('inviter_virou_teste=%s/%s/%s | ', r.status,
    (select inviter_status from public.referrals where invitee_id = w3),
    (select count(*) from public.dobroes_ledger where user_id = inv4 and reason = 'referral'));

  -- o razão fecha com o saldo nas contas que receberam
  select count(*) into n from public.profiles p
   where p.id in (inv, v1, v3, d1, inv2, w1)
     and p.credits <> (select coalesce(sum(l.delta), 0) from public.dobroes_ledger l where l.user_id = p.id);
  out := out || format('soma_razao=%s | ', case when n = 0 then 'igual' else 'DIVERGE' end);

  out := out || format('privilegios=anon:%s,%s,%s,%s auth:%s,%s,%s,%s,%s interna:%s,%s tabelas:%s,%s',
    has_function_privilege('anon', 'public.referral_my_code()', 'execute'),
    has_function_privilege('anon', 'public.referral_redeem(text)', 'execute'),
    has_function_privilege('anon', 'public.referral_settle(uuid)', 'execute'),
    has_function_privilege('anon', 'public.referral_status()', 'execute'),
    has_function_privilege('authenticated', 'public.referral_my_code()', 'execute'),
    has_function_privilege('authenticated', 'public.referral_redeem(text)', 'execute'),
    has_function_privilege('authenticated', 'public.referral_settle(uuid)', 'execute'),
    has_function_privilege('authenticated', 'public.referral_status()', 'execute'),
    has_function_privilege('authenticated', 'public.referral_ack()', 'execute'),
    has_function_privilege('authenticated', 'public.referral_pay_inviter(uuid)', 'execute'),
    has_function_privilege('authenticated', 'public.referral_blocked(uuid)', 'execute'),
    has_table_privilege('authenticated', 'public.referrals', 'insert'),
    has_table_privilege('authenticated', 'public.referral_tries', 'select'));

  raise exception 'RESULTADO (rollback): %', out;
end $$;
