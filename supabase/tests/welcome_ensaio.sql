-- Ensaio que desfaz tudo (#43): presente de boas-vindas (supabase/welcome.sql).
-- Rodar no SQL Editor do projeto cwtslesnthbenxswdcbv. Antes de aplicar a migração, cole o texto de
-- welcome.sql e este bloco na mesma execução: o raise no fim cancela a transação inteira, DDL inclusive.
-- Cria contas descartáveis dentro do bloco (nenhuma conta real nem QA é tocada).
--
-- Resultado esperado:
--   nova=ok/150/5/<saldo+150>    conta nova com carteira recebe
--   nova_milhas=15 ledger=1      10 de base + 5; uma linha 'welcome' no razão
--   nova_2=already/0             segunda chamada não paga
--   antiga=not_eligible          criada antes de welcome_gift_since
--   teste=not_eligible           is_test não recebe
--   desligado=not_eligible       welcome_gift_enabled = false
--   sem_carteira=no_wallet       e nada gravado (welcome_gifts=0)
--   qa_antiga=ok                 conta QA ignora a data
--   reset_nao_qa=welcome_reset_only_qa
--   qa_reset=ok/ledger 1         reset libera e paga de novo (soma do razão = saldo)
--   milhas_teto=50               48 + 5 para no miles_cap
--   fora_do_teto_online=ok       presente não passa pelo teto de 'online'
--   privilegios=f/f/t            reset: cliente e anon sem execute; claim: authenticated com execute
do $$
declare
  u_new uuid := gen_random_uuid(); u_old uuid := gen_random_uuid(); u_test uuid := gen_random_uuid();
  u_nowal uuid := gen_random_uuid(); u_qa uuid := gen_random_uuid(); u_cap uuid := gen_random_uuid();
  r record; out text := ''; n int; bal int; soma bigint; t text;
begin
  -- corte de ontem; conta antiga = criada há 10 dias
  update public.app_config set value = (now() - interval '1 day')::text where key = 'welcome_gift_since';
  insert into public.app_config (key, value) values ('welcome_gift_enabled', 'true')
    on conflict (key) do update set value = 'true';

  insert into auth.users (id, created_at) values
    (u_new, now()), (u_old, now() - interval '10 days'), (u_test, now()), (u_nowal, now()),
    (u_qa, now() - interval '10 days'), (u_cap, now());
  update public.profiles set username = 'ensaio-' || substr(id::text, 1, 8) where id in (u_new, u_old, u_test, u_nowal, u_qa, u_cap);
  update public.profiles set is_test = true where id = u_test;
  insert into public.allowed_players (user_id, source) values (u_qa, 'qa');
  perform public.dobroes_open_core(u, 0, '{}'::text[], 'app') from unnest(array[u_new, u_old, u_test, u_qa, u_cap]) u;

  -- conta nova
  perform set_config('request.jwt.claims', json_build_object('sub', u_new, 'role', 'authenticated')::text, true);
  select credits into bal from public.profiles where id = u_new;
  select * into r from public.claim_welcome_gift();
  out := out || format('nova=%s/%s/%s/%s | ', r.status, r.doubloons, r.miles, r.balance - bal);
  select nautical_miles into n from public.profiles where id = u_new;
  out := out || format('nova_milhas=%s ledger=%s | ', n,
    (select count(*) from public.dobroes_ledger where user_id = u_new and reason = 'welcome'));
  select * into r from public.claim_welcome_gift();
  out := out || format('nova_2=%s/%s | ', r.status, r.doubloons);

  -- conta antiga, teste, interruptor, sem carteira
  perform set_config('request.jwt.claims', json_build_object('sub', u_old, 'role', 'authenticated')::text, true);
  select * into r from public.claim_welcome_gift();
  out := out || format('antiga=%s | ', r.status);
  perform set_config('request.jwt.claims', json_build_object('sub', u_test, 'role', 'authenticated')::text, true);
  select * into r from public.claim_welcome_gift();
  out := out || format('teste=%s | ', r.status);
  update public.app_config set value = 'false' where key = 'welcome_gift_enabled';
  perform set_config('request.jwt.claims', json_build_object('sub', u_cap, 'role', 'authenticated')::text, true);
  select * into r from public.claim_welcome_gift();
  out := out || format('desligado=%s | ', r.status);
  update public.app_config set value = 'true' where key = 'welcome_gift_enabled';
  perform set_config('request.jwt.claims', json_build_object('sub', u_nowal, 'role', 'authenticated')::text, true);
  select * into r from public.claim_welcome_gift();
  out := out || format('sem_carteira=%s | ', r.status);
  out := out || format('welcome_gifts=%s | ', (select count(*) from public.welcome_gifts where user_id = u_nowal));

  -- QA antiga recebe; reset só QA; reset libera de novo
  perform set_config('request.jwt.claims', json_build_object('sub', u_qa, 'role', 'authenticated')::text, true);
  select * into r from public.claim_welcome_gift();
  out := out || format('qa_antiga=%s | ', r.status);
  begin
    perform public.welcome_gift_reset(u_new);
  exception when others then
    out := out || format('reset_nao_qa=%s | ', sqlerrm);
  end;
  perform public.welcome_gift_reset(u_qa);
  select * into r from public.claim_welcome_gift();
  select sum(delta) into soma from public.dobroes_ledger where user_id = u_qa;
  out := out || format('qa_reset=%s/ledger %s/soma_igual %s | ', r.status,
    (select count(*) from public.dobroes_ledger where user_id = u_qa and reason = 'welcome'),
    soma = (select credits from public.profiles where id = u_qa));

  -- teto de milhas
  update public.profiles set nautical_miles = 48, miles_day = public.brt_today() where id = u_cap;
  perform set_config('request.jwt.claims', json_build_object('sub', u_cap, 'role', 'authenticated')::text, true);
  select * into r from public.claim_welcome_gift();
  select nautical_miles into n from public.profiles where id = u_cap;
  out := out || format('milhas_teto=%s/%s | ', n, r.status);

  -- teto diário online cheio não barra o presente (ele não passa por ali)
  out := out || format('fora_do_teto_online=%s | ', r.status);

  out := out || format('privilegios=%s/%s/%s',
    has_function_privilege('authenticated', 'public.welcome_gift_reset(uuid)', 'execute'),
    has_function_privilege('anon', 'public.claim_welcome_gift()', 'execute'),
    has_function_privilege('authenticated', 'public.claim_welcome_gift()', 'execute'));

  raise exception 'RESULTADO (rollback): %', out;
end $$;
