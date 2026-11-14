-- Ensaio que desfaz tudo (#97 bloco B, 0.44.4): registro diário de atividade (supabase/behavior.sql e a
-- touch_presence de supabase/presence.sql). Rode no SQL Editor do projeto cwtslesnthbenxswdcbv ANTES de
-- aplicar as migrações: cole o texto inteiro de behavior.sql, o de presence.sql e este bloco na MESMA
-- execução. O raise no fim cancela a transação inteira (tabela, funções, views, usuários e linhas) e
-- devolve o resultado na mensagem de erro. Nenhuma conta real ou QA é tocada de forma durável; dentro do
-- bloco, os perfis REAIS criados a partir do dia da coorte do ensaio viram is_test só para a leitura das
-- views não misturar jogadores de verdade com os fictícios (o rollback devolve tudo).
-- Usuários fictícios: n1..n6 normais (coorte 1, criados há 10 dias), t (is_test), i (is_internal),
-- q (conta QA em allowed_players, is_test = false), m1..m3 normais (coorte 2, criados há 2 dias).
--
-- Resultado esperado (cada item vira um trecho de "RESULTADO"; qualquer FALHOU reprova o ensaio):
--   rls=bo                                tabela com RLS ligado e nenhuma política
--   anon_tab=negado auth_tab=negado       anon e authenticated não leem a tabela
--   anon_views=negado auth_views=negado   nem as três views
--   auth_fn=negado/negado                 authenticated não chama log_activity_day nem purge_activity
--   service_views=bo                      service_role lê as três views
--   views_sem_pii=bo                      nenhuma view tem user_id, e-mail ou apelido
--   touch_ontem=1 touch_de_novo=1         última visita de ontem: grava hoje; a segunda batida não duplica
--   touch_hoje_ja_visto=0                 última visita de hoje: não grava (só a 1ª batida do dia)
--   touch_nunca=1 touch_in_match=1        sem última visita grava; "in_match" (partida contra a IA) também
--   touch_erro_engolido=ok/0              sem log_activity_day a presença responde bo e nada é gravado
--   retorno coorte1: coorte=6 d1=nulo/6 completa d7=nulo/6 completa d30=nulo/incompleta
--                                         t, i e q ficam fora; D1 voltaram 5 de 6 e D7 2 de 6: não voltou < 5 ou voltou < 5, retorno nulo
--   retorno coorte3: coorte=10 d1=5/10 d7=nulo/10   5 voltaram e 5 não: aparece; 4 voltaram: nulo (voltou < 5)
--   retorno coorte2: coorte=nulo d1=nulo  coorte de 3 contas (< 5) sai toda nula
--   ativos_dia X+1=5 X+7=nulo             5 aparece; 2 (< 5) vira nulo
--   ativos_semana=6 (sem filtro 9)        semana da coorte 1: 6 contas, e sem o filtro seriam 9
--   marca_depois dia X: 6 -> 5            marcar n6 como interna depois corrige o histórico (leitura)
--   purge=1 resta_-90=1 resta_-89=1       apaga só o que passou de 90 dias
--   cascata=0                             apagar a conta apaga o histórico dela

do $$
declare
  n uuid[] := array[gen_random_uuid(), gen_random_uuid(), gen_random_uuid(), gen_random_uuid(), gen_random_uuid(), gen_random_uuid()];
  m uuid[] := array[gen_random_uuid(), gen_random_uuid(), gen_random_uuid()];
  p uuid[] := (select array_agg(gen_random_uuid()) from generate_series(1, 10));
  t uuid := gen_random_uuid(); i uuid := gen_random_uuid(); q uuid := gen_random_uuid();
  z uuid := gen_random_uuid();
  hoje date := public.brt_today();
  x date := public.brt_today() - 10;
  x2 date := public.brt_today() - 2;
  x3 date := public.brt_today() - 20;
  semx3 date := date_trunc('week', (public.brt_today() - 20)::timestamp)::date;
  semx date := date_trunc('week', (public.brt_today() - 10)::timestamp)::date;
  semx2 date := date_trunc('week', (public.brt_today() - 2)::timestamp)::date;
  out text := ''; k integer; k2 integer; r record; u uuid; bo boolean; k3 integer; v text;
  ts timestamptz;
begin
  ---------------------------------------------------------------- cenário
  insert into auth.users (id, email)
    select u2, 'ensaio-' || u2 || '@ensaio.invalid' from unnest(n || m || p || array[t, i, q, z]) u2;
  -- jogadores reais criados desde a coorte 1 saem da leitura (só dentro deste bloco)
  update public.profiles set is_test = true
   where (created_at at time zone 'America/Sao_Paulo')::date >= x3
     and id <> all (n || m || p || array[t, i, q, z]);
  update public.profiles set is_test = false where id = any (n || m || p || array[i, q, z]);
  update public.profiles set is_test = true where id = t;
  update public.profiles set is_internal = true where id = i;
  insert into public.allowed_players (user_id, source, note) values (q, 'qa', 'ensaio #97');
  update public.app_config set value = x3::text where key = 'activity_series_start';

  ts := (x::timestamp + interval '12 hours') at time zone 'America/Sao_Paulo';
  update public.profiles set created_at = ts where id = any (n || array[t, i, q]);
  ts := (x2::timestamp + interval '12 hours') at time zone 'America/Sao_Paulo';
  update public.profiles set created_at = ts where id = any (m);

  -- atividade (como service/dono do banco; o app nunca escreve a tabela)
  insert into public.activity_days (user_id, day) select u2, x from unnest(n || array[t, i, q]) u2;
  insert into public.activity_days (user_id, day) select u2, x + 1 from unnest(array[n[1], n[2], n[3], n[4], n[5], t, i]) u2;
  insert into public.activity_days (user_id, day) select u2, x + 7 from unnest(array[n[1], n[2], t]) u2;
  insert into public.activity_days (user_id, day) values (n[6], x + 2), (n[1], hoje);
  insert into public.activity_days (user_id, day) select u2, x2 from unnest(m) u2;
  insert into public.activity_days (user_id, day) select u2, x2 + 1 from unnest(array[m[1], m[2]]) u2;
  ts := (x3::timestamp + interval '12 hours') at time zone 'America/Sao_Paulo';
  update public.profiles set created_at = ts where id = any (p);
  insert into public.activity_days (user_id, day) select u2, x3 from unnest(p) u2;
  insert into public.activity_days (user_id, day) select u2, x3 + 1 from unnest(p[1:5]) u2;
  insert into public.activity_days (user_id, day) select u2, x3 + 7 from unnest(p[1:4]) u2;

  ---------------------------------------------------------------- RLS e direitos
  select (relrowsecurity and not exists (select 1 from pg_policy where polrelid = 'public.activity_days'::regclass))
    into bo from pg_class where oid = 'public.activity_days'::regclass;
  out := out || 'rls=' || case when bo then 'ok' else 'FALHOU' end || ' | ';

  perform set_config('request.jwt.claims', json_build_object('sub', n[1], 'role', 'authenticated')::text, true);
  v := '';
  begin execute 'set local role anon'; perform 1 from public.activity_days limit 1; v := 'FALHOU-leu';
  exception when insufficient_privilege then v := 'negado'; when others then v := 'FALHOU(' || sqlstate || ')'; end;
  execute 'reset role';
  out := out || 'anon_tab=' || v || ' | ';
  v := '';
  begin execute 'set local role authenticated'; perform 1 from public.activity_days limit 1; v := 'FALHOU-leu';
  exception when insufficient_privilege then v := 'negado'; when others then v := 'FALHOU(' || sqlstate || ')'; end;
  execute 'reset role';
  out := out || 'auth_tab=' || v || ' | ';

  v := '';
  begin execute 'set local role anon';
    perform 1 from public.behavior_ativos_diarios limit 1; v := 'FALHOU';
  exception when insufficient_privilege then v := 'negado'; when others then v := 'FALHOU(' || sqlstate || ')'; end;
  execute 'reset role';
  begin execute 'set local role anon';
    perform 1 from public.behavior_retorno_semanal limit 1; v := 'FALHOU';
  exception when insufficient_privilege then null; when others then v := 'FALHOU(' || sqlstate || ')'; end;
  execute 'reset role';
  out := out || 'anon_views=' || v || ' | ';
  v := '';
  begin execute 'set local role authenticated';
    perform 1 from public.behavior_ativos_diarios limit 1; v := 'FALHOU';
  exception when insufficient_privilege then v := 'negado'; when others then v := 'FALHOU(' || sqlstate || ')'; end;
  execute 'reset role';
  begin execute 'set local role authenticated';
    perform 1 from public.behavior_ativos_semanais limit 1; v := 'FALHOU';
  exception when insufficient_privilege then null; when others then v := 'FALHOU(' || sqlstate || ')'; end;
  execute 'reset role';
  begin execute 'set local role authenticated';
    perform 1 from public.behavior_retorno_semanal limit 1; v := 'FALHOU';
  exception when insufficient_privilege then null; when others then v := 'FALHOU(' || sqlstate || ')'; end;
  execute 'reset role';
  out := out || 'auth_views=' || v || ' | ';

  v := '';
  begin execute 'set local role authenticated'; perform public.log_activity_day(n[1]); v := 'FALHOU';
  exception when insufficient_privilege then v := 'negado'; when others then v := 'FALHOU(' || sqlstate || ')'; end;
  execute 'reset role';
  begin execute 'set local role authenticated'; perform public.purge_activity(); v := v || '/FALHOU';
  exception when insufficient_privilege then v := v || '/negado'; when others then v := v || '/FALHOU(' || sqlstate || ')'; end;
  execute 'reset role';
  out := out || 'auth_fn=' || v || ' | ';

  v := 'ok';
  begin
    execute 'set local role service_role';
    perform 1 from public.behavior_ativos_diarios limit 1;
    perform 1 from public.behavior_ativos_semanais limit 1;
    perform 1 from public.behavior_retorno_semanal limit 1;
  exception when others then v := 'FALHOU(' || sqlstate || ')'; end;
  execute 'reset role';
  out := out || 'service_views=' || v || ' | ';

  select count(*) = 0 into bo from information_schema.columns
   where table_schema = 'public'
     and table_name in ('behavior_ativos_diarios', 'behavior_ativos_semanais', 'behavior_retorno_semanal')
     and (column_name ilike '%user%' or column_name ilike '%mail%' or column_name ilike '%apelido%'
          or column_name ilike '%username%' or column_name = 'id');
  out := out || 'views_sem_pii=' || case when bo then 'ok' else 'FALHOU' end || ' | ';

  ---------------------------------------------------------------- gravação pela presença
  -- última visita de ontem: grava hoje; a segunda batida não duplica
  update public.profiles set last_seen_at = now() - interval '1 day' where id = n[1];
  delete from public.activity_days where user_id = n[1] and day = hoje;
  perform set_config('request.jwt.claims', json_build_object('sub', n[1], 'role', 'authenticated')::text, true);
  execute 'set local role authenticated';
  perform public.touch_presence('online');
  perform public.touch_presence('online');
  execute 'reset role';
  select count(*) into k from public.activity_days where user_id = n[1] and day = hoje;
  out := out || 'touch_ontem=' || k || ' touch_de_novo=' || k || ' | ';

  -- última visita de hoje: não grava (só a primeira batida do dia)
  update public.profiles set last_seen_at = now() where id = n[2];
  delete from public.activity_days where user_id = n[2] and day = hoje;
  perform set_config('request.jwt.claims', json_build_object('sub', n[2], 'role', 'authenticated')::text, true);
  execute 'set local role authenticated';
  perform public.touch_presence('online');
  execute 'reset role';
  select count(*) into k from public.activity_days where user_id = n[2] and day = hoje;
  out := out || 'touch_hoje_ja_visto=' || k || ' | ';

  -- sem última visita; e "in_match" (partida contra a IA)
  update public.profiles set last_seen_at = null where id = n[3];
  delete from public.activity_days where user_id = n[3] and day = hoje;
  perform set_config('request.jwt.claims', json_build_object('sub', n[3], 'role', 'authenticated')::text, true);
  execute 'set local role authenticated';
  perform public.touch_presence('online');
  execute 'reset role';
  select count(*) into k from public.activity_days where user_id = n[3] and day = hoje;
  update public.profiles set last_seen_at = now() - interval '1 day' where id = n[4];
  delete from public.activity_days where user_id = n[4] and day = hoje;
  perform set_config('request.jwt.claims', json_build_object('sub', n[4], 'role', 'authenticated')::text, true);
  execute 'set local role authenticated';
  perform public.touch_presence('in_match');
  execute 'reset role';
  select count(*) into k2 from public.activity_days where user_id = n[4] and day = hoje;
  out := out || 'touch_nunca=' || k || ' touch_in_match=' || k2 || ' | ';

  -- erro ao registrar nunca derruba a presença (função renomeada de propósito)
  alter function public.log_activity_day(uuid) rename to log_activity_day_ensaio;
  update public.profiles set last_seen_at = now() - interval '1 day' where id = n[5];
  delete from public.activity_days where user_id = n[5] and day = hoje;
  perform set_config('request.jwt.claims', json_build_object('sub', n[5], 'role', 'authenticated')::text, true);
  execute 'set local role authenticated';
  select (select t2.ok from public.touch_presence('online') t2) into bo;
  execute 'reset role';
  select count(*) into k from public.activity_days where user_id = n[5] and day = hoje;
  alter function public.log_activity_day_ensaio(uuid) rename to log_activity_day;
  out := out || 'touch_erro_engolido=' || case when bo then 'ok' else 'FALHOU' end || '/' || k || ' | ';

  ---------------------------------------------------------------- views
  -- os registros de hoje feitos acima não mexem nos dias do cenário (x, x+1, x+7); n[1] tem hoje desde o cenário
  select * into r from public.behavior_retorno_semanal where semana = semx;
  out := out || format('retorno coorte1: coorte=%s d1=%s/%s %s d7=%s/%s %s d30=%s/%s | ',
    r.coorte, coalesce(r.retorno_d1::text, 'nulo'), r.coorte_d1, case when r.completa_d1 then 'completa' else 'incompleta' end,
    coalesce(r.retorno_d7::text, 'nulo'), r.coorte_d7, case when r.completa_d7 then 'completa' else 'incompleta' end,
    coalesce(r.retorno_d30::text, 'nulo'), case when r.completa_d30 then 'completa' else 'incompleta' end);
  select * into r from public.behavior_retorno_semanal where semana = semx3;
  out := out || format('retorno coorte3: coorte=%s d1=%s/%s d7=%s/%s | ',
    r.coorte, coalesce(r.retorno_d1::text, 'nulo'), r.coorte_d1, coalesce(r.retorno_d7::text, 'nulo'), r.coorte_d7);
  select * into r from public.behavior_retorno_semanal where semana = semx2;
  out := out || format('retorno coorte2: coorte=%s d1=%s | ', coalesce(r.coorte::text, 'nulo'), coalesce(r.retorno_d1::text, 'nulo'));

  select ativos into k from public.behavior_ativos_diarios where dia = x + 1;
  select ativos into k2 from public.behavior_ativos_diarios where dia = x + 7;
  out := out || format('ativos_dia X+1=%s X+7=%s | ', coalesce(k::text, 'nulo'), coalesce(k2::text, 'nulo'));

  select ativos into k from public.behavior_ativos_semanais where semana = semx;
  select count(distinct user_id) into k2 from public.activity_days
   where day >= semx and day < semx + 7;
  out := out || format('ativos_semana=%s (sem filtro %s) | ', coalesce(k::text, 'nulo'), k2);

  -- marcar interna depois corrige o histórico (a exclusão é na leitura)
  select ativos into k from public.behavior_ativos_diarios where dia = x;
  update public.profiles set is_internal = true where id = n[6];
  select ativos into k2 from public.behavior_ativos_diarios where dia = x;
  out := out || format('marca_depois dia X: %s -> %s | ', coalesce(k::text, 'nulo'), coalesce(k2::text, 'nulo'));

  ---------------------------------------------------------------- retenção e cascata
  delete from public.activity_days where day < hoje - 85;
  insert into public.activity_days (user_id, day) values (n[1], hoje - 91), (n[1], hoje - 90), (n[1], hoje - 89);
  select public.purge_activity() into k;
  select count(*) into k2 from public.activity_days where user_id = n[1] and day = hoje - 90;
  select count(*) into k3 from public.activity_days where user_id = n[1] and day = hoje - 89;
  out := out || format('purge=%s resta_-90=%s resta_-89=%s | ', k, k2, k3);

  insert into public.activity_days (user_id, day) values (z, hoje);
  delete from auth.users where id = z;
  select count(*) into k from public.activity_days where user_id = z;
  out := out || 'cascata=' || k;

  raise exception 'RESULTADO (rollback): %', out;
end $$;
