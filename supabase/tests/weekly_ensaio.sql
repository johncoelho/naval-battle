-- Ensaio que desfaz tudo (#47): desafio da semana (supabase/weekly.sql, migração weekly_challenge).
-- Rodar no SQL Editor do projeto cwtslesnthbenxswdcbv. Para ensaiar ANTES de aplicar a migração, cole o
-- texto de weekly.sql e este arquivo na mesma execução: o raise no fim cancela a transação inteira, DDL
-- inclusive, e nada fica gravado. Depois de aplicada, rode só este. Contas descartáveis criadas dentro do
-- bloco (nunca QA nem jogador real). Sem e-mail real.
--
-- Resultado esperado:
--   semana=t/t/ONLINE_PLAY_5/RANKED_WIN_3/t   segunda de Brasília; rodízio fixo (2026-01-05 = online,
--                                             2025-12-29 = ranqueada), repete a cada 3 semanas
--   fuso_online=4/5                           sala 1h antes da segunda 00h BRT e 1h depois do domingo
--                                             23h59 ficam fora (4); a de 1h antes do fim entra (5)
--   online_sem_combate=5                      sala sem tiro do convidado e sala sem convidado não contam
--   checkin=4/5/1                             check-ins fora da semana (1 depois, 1 antes) não contam; 5 = 5
--   ranked=2/2/3                              derrota ranqueada, vitória casual, ranqueada vencida sem combate
--                                             e ranqueada com result_conflict não contam; com combate = 3
--   status=<code>/<alvo>/<alvo-1>/f/250/5     weekly_status antes do resgate
--   nao_feito=not_done/0                      progresso < alvo não paga
--   ok=ok/250/5/250                           resgate paga 250 + 5 e o saldo sobe 250
--   status_depois=t                           claimed = true
--   duplo=already/250/5                       segunda chamada não paga de novo
--   corrida=250                               marca apagada à mão (corrida): o razão trava, saldo +250 só 1x
--   razao_weekly=1                            uma linha 'weekly' no razão (ref = segunda)
--   semana_seguinte=ok/500                    claim da semana anterior não bloqueia a atual: +250
--   teste=not_eligible/0                      is_test não recebe, nada gravado
--   sem_carteira=no_wallet/0/0                nada gravado e saldo igual
--   teto_online=ok/250                        500 já pagos com reason 'online' hoje; o prêmio paga
--   milhas_teto=2/50                          48 + 5 para no miles_cap (pago 2, gravado 2)
--   milhas_no_teto=0                          já no teto: pago 0
--   desligado=0/not_eligible                  weekly_enabled = false: status vazio e resgate bloqueado
--   privilegios=f/t/f/t/f/f                   anon sem status; authenticated com status;
--                                             anon sem claim; authenticated com claim;
--                                             progress_of fechada; authenticated sem insert na tabela

create or replace function pg_temp.fill(u uuid, o uuid, w date, code text, a int, b int) returns void
language plpgsql as $$
declare
  t0 timestamptz := w::timestamp at time zone 'America/Sao_Paulo';
  i int; mid uuid;
begin
  for i in a..b loop
    if code = 'ONLINE_PLAY_5' then
      mid := gen_random_uuid();
      insert into public.online_matches (id, host_id, guest_id, host_name, guest_name, status, created_at)
        values (mid, u, o, 'ensaio', 'ensaio', 'finished', t0 + interval '1 hour' + i * interval '1 minute');
      insert into public.online_messages (match_id, sender_id, body) values
        (mid, u, 'ACT|1|1'), (mid, o, 'ABIL|X');
    elsif code = 'CHECKIN_5' then
      insert into public.daily_claims (user_id, day, kind, doubloons) values (u, w + i - 1, 'checkin', 25);
    else
      mid := gen_random_uuid();
      insert into public.online_matches (id, host_id, guest_id, host_name, guest_name, status, ranked, winner_id, created_at)
        values (mid, u, o, 'ensaio', 'ensaio', 'finished', true, u, t0 + interval '1 hour' + i * interval '1 minute');
      insert into public.online_messages (match_id, sender_id, body) values (mid, u, 'ACT|1|1'), (mid, o, 'ACT|2|2');
    end if;
  end loop;
end $$;

create or replace function pg_temp.como(u uuid) returns void language sql as $$
  select set_config('request.jwt.claims', json_build_object('sub', u, 'role', 'authenticated')::text, true);
$$;

do $$
declare
  a uuid := gen_random_uuid(); o uuid := gen_random_uuid(); t uuid := gen_random_uuid();
  nw uuid := gen_random_uuid(); cp uuid := gen_random_uuid(); cp2 uuid := gen_random_uuid();
  x uuid := gen_random_uuid();
  cw date := public.brt_week_start();
  d date := public.brt_today();
  w_on date; w_ck date; w_rk date; w date; k int;
  t0 timestamptz; t1 timestamptz; mid uuid;
  cur record; r record; s record; out text := ''; n int; bal int; n2 int;
begin
  insert into auth.users (id, email)
    select y, 'ensaio-' || y || '@ensaio.invalid' from unnest(array[a, o, t, nw, cp, cp2, x]) y;
  update public.profiles set credits = 1000, nautical_miles = 10, miles_day = d where id in (a, o, x);
  update public.profiles set credits = 500, nautical_miles = 10, miles_day = d, is_test = true where id = t;
  update public.profiles set credits = 500, nautical_miles = 10, miles_day = d where id = nw;
  update public.profiles set credits = 0, nautical_miles = 48, miles_day = d where id = cp;
  update public.profiles set credits = 0, nautical_miles = 50, miles_day = d where id = cp2;
  insert into public.dobroes_wallets (user_id, opening, source)
    select y, 0, 'admin' from unnest(array[a, o, t, cp, cp2, x]) y;
  insert into public.app_config (key, value) values
    ('weekly_enabled', 'true'), ('weekly_doubloons', '250'), ('weekly_miles', '5'), ('miles_cap', '50')
  on conflict (key) do update set value = excluded.value;

  ---------------------------------------------------------------- semana e rodízio
  select * into cur from public.weekly_mission(date '2026-01-05');
  select * into s from public.weekly_mission(date '2025-12-29');
  out := out || format('semana=%s/%s/%s/%s/%s | ',
    extract(isodow from cw) = 1, d - cw between 0 and 6, cur.code, s.code,
    (select code from public.weekly_mission(date '2026-01-26')) = cur.code);

  -- semanas que têm cada missão (hoje + 0, 7 ou 14 dias, ou adiante)
  for k in 0..2 loop
    w := cw + 7 * k;
    case (select code from public.weekly_mission(w))
      when 'ONLINE_PLAY_5' then w_on := w;
      when 'CHECKIN_5' then w_ck := w;
      else w_rk := w;
    end case;
  end loop;

  ---------------------------------------------------------------- fuso: borda da semana (BRT)
  t0 := w_on::timestamp at time zone 'America/Sao_Paulo';
  t1 := (w_on + 7)::timestamp at time zone 'America/Sao_Paulo';
  perform pg_temp.fill(x, o, w_on, 'ONLINE_PLAY_5', 1, 4);
  -- 1h antes da segunda 00h BRT (domingo anterior) e 1h depois do fim: fora
  insert into public.online_matches (id, host_id, guest_id, host_name, guest_name, status, created_at)
    values (gen_random_uuid(), x, o, 'e', 'e', 'finished', t0 - interval '1 hour'),
           (gen_random_uuid(), x, o, 'e', 'e', 'finished', t1 + interval '1 hour');
  insert into public.online_messages (match_id, sender_id, body)
    select m.id, u, 'ACT|1|1' from public.online_matches m, unnest(array[x, o]) u
     where m.host_id = x and m.created_at in (t0 - interval '1 hour', t1 + interval '1 hour');
  n := public.weekly_progress_of(x, w_on);
  -- 1h antes do fim da semana: dentro
  mid := gen_random_uuid();
  insert into public.online_matches (id, host_id, guest_id, host_name, guest_name, status, created_at)
    values (mid, o, x, 'e', 'e', 'finished', t1 - interval '1 hour');
  insert into public.online_messages (match_id, sender_id, body) values (mid, x, 'ACT|1|1'), (mid, o, 'ACT|2|2');
  out := out || format('fuso_online=%s/%s | ', n, public.weekly_progress_of(x, w_on));

  -- sala em que só um lado deu tiro, e sala sem convidado: não contam
  mid := gen_random_uuid();
  insert into public.online_matches (id, host_id, guest_id, host_name, guest_name, status, created_at)
    values (mid, x, o, 'e', 'e', 'abandoned', t0 + interval '3 days');
  insert into public.online_messages (match_id, sender_id, body) values (mid, x, 'ACT|1|1'), (mid, o, 'READY|1');
  insert into public.online_matches (host_id, host_name, status, created_at)
    values (x, 'e', 'waiting', t0 + interval '4 days');
  out := out || format('online_sem_combate=%s | ', public.weekly_progress_of(x, w_on));

  ---------------------------------------------------------------- check-in
  perform pg_temp.fill(x, o, w_ck, 'CHECKIN_5', 1, 4);
  insert into public.daily_claims (user_id, day, kind, doubloons) values (x, w_ck + 7, 'checkin', 25), (x, w_ck - 1, 'checkin', 25);
  n := public.weekly_progress_of(x, w_ck);
  insert into public.daily_claims (user_id, day, kind, doubloons) values (x, w_ck + 4, 'checkin', 25);
  out := out || format('checkin=%s/%s/%s | ', n, public.weekly_progress_of(x, w_ck),
    (select count(*) from public.daily_claims where user_id = x and kind = 'checkin' and day >= w_ck + 7));

  ---------------------------------------------------------------- ranqueadas
  perform pg_temp.fill(x, o, w_rk, 'RANKED_WIN_3', 1, 2);
  insert into public.online_matches (host_id, guest_id, host_name, guest_name, status, ranked, winner_id, created_at)
    values (x, o, 'e', 'e', 'finished', true, o, (w_rk::timestamp at time zone 'America/Sao_Paulo') + interval '2 days'),
           (x, o, 'e', 'e', 'finished', false, x, (w_rk::timestamp at time zone 'America/Sao_Paulo') + interval '2 days');
  n := public.weekly_progress_of(x, w_rk);
  -- vencida sem combate (sem jogadas) e vencida com conflito de resultado: não contam
  insert into public.online_matches (host_id, guest_id, host_name, guest_name, status, ranked, winner_id, created_at)
    values (x, o, 'e', 'e', 'finished', true, x, (w_rk::timestamp at time zone 'America/Sao_Paulo') + interval '3 days');
  mid := gen_random_uuid();
  insert into public.online_matches (id, host_id, guest_id, host_name, guest_name, status, ranked, winner_id, result_conflict, created_at)
    values (mid, x, o, 'e', 'e', 'finished', true, x, true, (w_rk::timestamp at time zone 'America/Sao_Paulo') + interval '3 days');
  insert into public.online_messages (match_id, sender_id, body) values (mid, x, 'ACT|1|1'), (mid, o, 'ACT|2|2');
  n2 := public.weekly_progress_of(x, w_rk);
  perform pg_temp.fill(x, o, w_rk, 'RANKED_WIN_3', 3, 3);
  out := out || format('ranked=%s/%s/%s | ', n, n2, public.weekly_progress_of(x, w_rk));

  ---------------------------------------------------------------- a (carteira): status, resgate, duplo, corrida
  select * into cur from public.weekly_mission(cw);
  perform pg_temp.como(a);
  perform pg_temp.fill(a, o, cw, cur.code, 1, cur.target - 1);
  select * into s from public.weekly_status();
  out := out || format('status=%s/%s/%s/%s/%s/%s | ', s.mission, s.target, s.progress, s.claimed, s.doubloons, s.miles);
  select credits into bal from public.profiles where id = a;
  select * into r from public.claim_weekly();
  out := out || format('nao_feito=%s/%s | ', r.status, r.doubloons);
  perform pg_temp.fill(a, o, cw, cur.code, cur.target, cur.target);
  select * into r from public.claim_weekly();
  out := out || format('ok=%s/%s/%s/%s | ', r.status, r.doubloons, r.miles, r.balance - bal);
  select * into s from public.weekly_status();
  out := out || format('status_depois=%s | ', s.claimed);
  select * into r from public.claim_weekly();
  out := out || format('duplo=%s/%s/%s | ', r.status, r.doubloons, r.miles);
  -- corrida: a marca some (como se dois pedidos tivessem passado juntos); o razão segura
  delete from public.weekly_claims where user_id = a;
  select * into r from public.claim_weekly();
  out := out || format('corrida=%s | ', (select credits from public.profiles where id = a) - bal);
  out := out || format('razao_weekly=%s | ',
    (select count(*) from public.dobroes_ledger where user_id = a and reason = 'weekly' and ref = cw::text));

  -- semana seguinte: o resgate da anterior (marca e razão com a segunda passada) não bloqueia a atual
  update public.weekly_claims set week = cw - 7 where user_id = a;
  update public.dobroes_ledger set ref = (cw - 7)::text where user_id = a and reason = 'weekly';
  select * into r from public.claim_weekly();
  out := out || format('semana_seguinte=%s/%s | ', r.status, (select credits from public.profiles where id = a) - bal);

  ---------------------------------------------------------------- is_test
  perform pg_temp.como(t);
  perform pg_temp.fill(t, o, cw, cur.code, 1, cur.target);
  select * into r from public.claim_weekly();
  out := out || format('teste=%s/%s | ', r.status, (select count(*) from public.weekly_claims where user_id = t));

  ---------------------------------------------------------------- sem carteira
  perform pg_temp.como(nw);
  perform pg_temp.fill(nw, o, cw, cur.code, 1, cur.target);
  select * into r from public.claim_weekly();
  out := out || format('sem_carteira=%s/%s/%s | ', r.status, r.doubloons,
    (select count(*) from public.weekly_claims where user_id = nw) + (select 500 - credits from public.profiles where id = nw));

  ---------------------------------------------------------------- teto online cheio e teto de milhas
  perform public.dobroes_apply(cp, 500, 'online', 'ensaio-teto');
  perform pg_temp.como(cp);
  perform pg_temp.fill(cp, o, cw, cur.code, 1, cur.target);
  select * into r from public.claim_weekly();
  out := out || format('teto_online=%s/%s | ', r.status, r.doubloons);
  out := out || format('milhas_teto=%s/%s | ', r.miles, (select nautical_miles from public.profiles where id = cp));
  perform pg_temp.como(cp2);
  perform pg_temp.fill(cp2, o, cw, cur.code, 1, cur.target);
  select * into r from public.claim_weekly();
  out := out || format('milhas_no_teto=%s/%s | ', r.miles, (select miles from public.weekly_claims where user_id = cp2));

  ---------------------------------------------------------------- interruptor
  update public.app_config set value = 'false' where key = 'weekly_enabled';
  perform pg_temp.como(x);
  select count(*) into n from public.weekly_status();
  select * into r from public.claim_weekly();
  out := out || format('desligado=%s/%s | ', n, r.status);
  update public.app_config set value = 'true' where key = 'weekly_enabled';

  ---------------------------------------------------------------- privilégios
  out := out || format('privilegios=%s/%s/%s/%s/%s/%s',
    case when has_function_privilege('anon', 'public.weekly_status()', 'execute') then 't' else 'f' end,
    case when has_function_privilege('authenticated', 'public.weekly_status()', 'execute') then 't' else 'f' end,
    case when has_function_privilege('anon', 'public.claim_weekly()', 'execute') then 't' else 'f' end,
    case when has_function_privilege('authenticated', 'public.claim_weekly()', 'execute') then 't' else 'f' end,
    case when has_function_privilege('authenticated', 'public.weekly_progress_of(uuid, date)', 'execute') then 't' else 'f' end,
    case when has_table_privilege('authenticated', 'public.weekly_claims', 'insert') then 't' else 'f' end);

  raise exception 'RESULTADO (rollback): %', out;
end $$;
