-- Ensaio que desfaz tudo (#42): prêmio de retorno no check-in (supabase/daily.sql, migração
-- daily_return_bonus). Rodar no SQL Editor do projeto cwtslesnthbenxswdcbv. Para ensaiar ANTES de
-- aplicar a migração, cole o texto novo de daily.sql e este arquivo na mesma execução: o raise no fim
-- cancela a transação inteira, DDL inclusive, e nada fica gravado. Depois de aplicada, rode só este.
-- Usa contas descartáveis criadas dentro do bloco (nunca QA nem jogador real). Sem e-mail real.
--
-- Resultado esperado:
--   quatro_dias=0/0/25                   check-in com intervalo de 4 dias não paga retorno; doubloons = só o check-in
--   primeiro_checkin=0/0                 conta sem check-in anterior não recebe retorno
--   status_antes=t/100/3                 daily_status avisa o prêmio antes do check-in (5 dias)
--   cinco_dias=100/3/25                  paga 100 + 3; doubloons segue só check-in (app antigo)
--   saldo=1125 milhas=13                 1000 + 25 + 100; 10 + 3
--   razao_return=1 claim_return=1        uma linha no razão (reason 'return') e uma em daily_claims
--   repetido=0/0/0                       segundo check-in do dia: nada, nada de novo no razão
--   status_depois=f                      depois do resgate o aviso some
--   carencia_20d=0/0                     segundo retorno 20 dias depois: não paga
--   carencia_31d=100/3                   31 dias depois: paga de novo
--   is_test=0/0/25 milhas=10 claims=0    conta de teste não recebe, nem registra (25 = só o check-in)
--   sem_carteira=0/3 credits=500 claim=1 legado: registra e paga milhas, não credita dobrões
--   teto_milhas=1/0                      49 -> 50 (cap) paga 1; já no teto paga 0
--   privilegios=f/t/f                    anon não executa daily_status; authenticated sim; daily_return_ready fechada

create or replace function pg_temp.volta(u uuid, last_dt date, ret_day date) returns void
language plpgsql as $$
declare d date := public.brt_today();
begin
  -- recua o relógio da conta: apaga o que foi pago hoje, antecipa o retorno anterior
  delete from public.daily_claims where user_id = u and day = d and kind <> 'return';
  update public.dobroes_ledger set ref = ref || '-' || gen_random_uuid()::text where user_id = u and ref = d::text;
  if ret_day is not null then
    update public.daily_claims set day = ret_day where user_id = u and kind = 'return';
  end if;
  insert into public.daily_progress (user_id, streak, last_checkin) values (u, 3, last_dt)
  on conflict (user_id) do update set streak = 3, last_checkin = excluded.last_checkin;
end $$;

do $$
declare
  a uuid := gen_random_uuid(); t uuid := gen_random_uuid(); c uuid := gen_random_uuid();
  e uuid := gen_random_uuid(); f uuid := gen_random_uuid();
  d date := public.brt_today();
  r record; out text := ''; n int; n2 int;
begin
  insert into auth.users (id, email)
    select x, 'ensaio-' || x || '@ensaio.invalid' from unnest(array[a, t, c, e, f]) x;
  -- o gatilho de auth.users criou os perfis; carteira só em a, e, f
  update public.profiles set credits = 1000, nautical_miles = 10, miles_day = d where id = a;
  update public.profiles set credits = 500, nautical_miles = 10, miles_day = d, is_test = true where id = t;
  update public.profiles set credits = 500, nautical_miles = 10, miles_day = d where id = c;
  update public.profiles set credits = 0, nautical_miles = 49, miles_day = d where id = e;
  update public.profiles set credits = 0, nautical_miles = 10, miles_day = d where id = f;
  insert into public.dobroes_wallets (user_id, opening, source)
    select x, 0, 'admin' from unnest(array[a, e, f]) x;
  insert into public.app_config (key, value) values
    ('daily_return_days', '5'), ('daily_return_cooldown_days', '30'),
    ('daily_return_doubloons', '100'), ('daily_return_miles', '3'), ('miles_cap', '50')
  on conflict (key) do update set value = excluded.value;

  ---------------------------------------------------------------- conta a (carteira)
  perform set_config('request.jwt.claims', json_build_object('sub', a, 'role', 'authenticated')::text, true);

  -- 4 dias: não paga
  perform pg_temp.volta(a, d - 4, null);
  select * into r from public.daily_checkin();
  out := out || format('quatro_dias=%s/%s/%s | ', r.return_doubloons, r.return_miles, r.doubloons);

  -- primeira vez: sem check-in anterior
  delete from public.daily_progress where user_id = f;
  perform set_config('request.jwt.claims', json_build_object('sub', f, 'role', 'authenticated')::text, true);
  select * into r from public.daily_checkin();
  out := out || format('primeiro_checkin=%s/%s | ', r.return_doubloons, r.return_miles);
  perform set_config('request.jwt.claims', json_build_object('sub', a, 'role', 'authenticated')::text, true);

  -- 5 dias: paga. Zera o que o caso anterior mexeu.
  perform pg_temp.volta(a, d - 5, null);
  update public.profiles set credits = 1000, nautical_miles = 10, miles_day = d where id = a;
  delete from public.dobroes_ledger where user_id = a;
  select * into r from public.daily_status();
  out := out || format('status_antes=%s/%s/%s | ', case when r.return_ready then 't' else 'f' end,
                       r.return_doubloons, r.return_miles);
  select * into r from public.daily_checkin();
  out := out || format('cinco_dias=%s/%s/%s | ', r.return_doubloons, r.return_miles, r.doubloons);
  out := out || format('saldo=%s milhas=%s | ',
    (select credits from public.profiles where id = a), (select nautical_miles from public.profiles where id = a));
  out := out || format('razao_return=%s claim_return=%s | ',
    (select count(*) from public.dobroes_ledger where user_id = a and reason = 'return'),
    (select count(*) from public.daily_claims where user_id = a and kind = 'return'));

  -- repetido no mesmo dia
  select * into r from public.daily_checkin();
  out := out || format('repetido=%s/%s/%s | ', r.return_doubloons, r.return_miles, r.doubloons);
  select * into r from public.daily_status();
  out := out || format('status_depois=%s | ', case when r.return_ready then 't' else 'f' end);

  -- 20 dias depois do retorno: carência
  perform pg_temp.volta(a, d - 6, d - 20);
  select * into r from public.daily_checkin();
  out := out || format('carencia_20d=%s/%s | ', r.return_doubloons, r.return_miles);

  -- 31 dias depois: paga de novo
  perform pg_temp.volta(a, d - 6, d - 31);
  select * into r from public.daily_checkin();
  out := out || format('carencia_31d=%s/%s | ', r.return_doubloons, r.return_miles);

  ---------------------------------------------------------------- conta de teste (is_test)
  perform set_config('request.jwt.claims', json_build_object('sub', t, 'role', 'authenticated')::text, true);
  perform pg_temp.volta(t, d - 10, null);
  select * into r from public.daily_checkin();
  out := out || format('is_test=%s/%s/%s milhas=%s claims=%s | ', r.return_doubloons, r.return_miles, r.doubloons,
    (select nautical_miles from public.profiles where id = t),
    (select count(*) from public.daily_claims where user_id = t and kind = 'return'));

  ---------------------------------------------------------------- sem carteira (legado)
  perform set_config('request.jwt.claims', json_build_object('sub', c, 'role', 'authenticated')::text, true);
  perform pg_temp.volta(c, d - 10, null);
  select * into r from public.daily_checkin();
  out := out || format('sem_carteira=%s/%s credits=%s claim=%s | ', r.return_doubloons, r.return_miles,
    (select credits from public.profiles where id = c),
    (select count(*) from public.daily_claims where user_id = c and kind = 'return'));

  ---------------------------------------------------------------- teto de milhas (49 -> 50, depois 50)
  perform set_config('request.jwt.claims', json_build_object('sub', e, 'role', 'authenticated')::text, true);
  perform pg_temp.volta(e, d - 10, null);
  select * into r from public.daily_checkin();
  n := r.return_miles;
  perform pg_temp.volta(e, d - 10, d - 40);
  select * into r from public.daily_checkin();
  out := out || format('teto_milhas=%s/%s | ', n, r.return_miles);

  ---------------------------------------------------------------- privilégios
  out := out || format('privilegios=%s/%s/%s',
    case when has_function_privilege('anon', 'public.daily_status()', 'execute') then 't' else 'f' end,
    case when has_function_privilege('authenticated', 'public.daily_status()', 'execute') then 't' else 'f' end,
    case when has_function_privilege('authenticated', 'public.daily_return_ready(uuid, date)', 'execute') then 't' else 'f' end);

  raise exception 'RESULTADO (rollback): %', out;
end $$;
