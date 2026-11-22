-- Ensaio que desfaz tudo (#137, 0.48.0): contadores de funil (supabase/funnel.sql).
-- Rode no SQL Editor do projeto cwtslesnthbenxswdcbv ANTES de aplicar a migração: cole o texto inteiro de
-- funnel.sql e este bloco na MESMA execução, dentro de `begin; ... ; rollback;` se o editor não for atômico.
-- O raise no fim cancela a transação inteira (tabelas, funções, views, usuários e linhas) e devolve o resultado
-- na mensagem de erro. Dentro do bloco, os perfis REAIS viram is_test só para as views não misturarem
-- jogadores de verdade com os fictícios, e a tabela de contadores é esvaziada (o rollback devolve tudo).
-- Usuários fictícios: u (normal), t (is_test), i (is_internal), q (conta QA em allowed_players),
-- v1..v10 (convidados normais) e w (convidado de teste).
--
-- Resultado esperado (qualquer FALHOU reprova o ensaio):
--   rls=ok                              as duas tabelas com RLS ligado e nenhuma política
--   anon_tab=negado auth_tab=negado     anon e authenticated não leem tabela nem catálogo
--   anon_views=negado auth_views=negado nem as três views
--   anon_fn=negado                      anon não executa log_funnel_event
--   auth_purge=negado                   authenticated não chama purge_funnel
--   service_views=ok                    service_role lê as três views
--   grava_1=1 grava_2=2                 a segunda chamada soma 1 (interno = false)
--   fora_catalogo=0                     evento ou origem ou plataforma fora da lista: nada gravado
--   erro_engolido=ok                    parâmetro nulo não levanta erro
--   sem_sessao=0                        sem auth.uid() nada é gravado
--   interno=3 normal=1                  teste, interna e QA (3 chamadas) caem em interno = true; a normal fica fora
--   celula_4=nulo celula_5=5            4 ocorrências viram nulo na view; 5 aparecem
--   plataformas=2                       android e ios em linhas separadas
--   sem_id=ok                           nenhuma tabela ou view tem user_id, e-mail ou apelido
--   convites aceitos=10 pagos=5         9 contas de teste/internas ficam fora; 5 pagos aparecem
--   convites pagos<5=nulo               com 4 pagos o campo é nulo
--   salas: fim_normal=5 wo=nulo expirou=nulo  5 aparecem, menos de 5 viram nulo, sala de teste fica fora
--   purge=2 resta_-365=1 resta_-366=0   apaga só o que passou de 365 dias (a linha de 2000 e a de -366)

do $$
declare
  u uuid := gen_random_uuid(); t uuid := gen_random_uuid(); i uuid := gen_random_uuid(); q uuid := gen_random_uuid();
  w uuid := gen_random_uuid();
  v uuid[] := (select array_agg(gen_random_uuid()) from generate_series(1, 10));
  h uuid[] := (select array_agg(gen_random_uuid()) from generate_series(1, 8));
  hoje date := public.brt_today();
  sem date := date_trunc('week', public.brt_today()::timestamp)::date;
  out text := ''; k integer; k2 integer; r record; s text; bo boolean; x integer;
begin
  ---------------------------------------------------------------- cenário
  insert into auth.users (id, email)
    select u2, 'ensaio-' || u2 || '@ensaio.invalid' from unnest(v || h || array[u, t, i, q, w]) u2;
  update public.profiles set is_test = true where id <> all (v || h || array[u, t, i, q, w]);
  update public.profiles set is_test = false where id = any (v || h || array[u, i, q]);
  update public.profiles set is_test = true where id = any (array[t, w]);
  update public.profiles set is_internal = true where id = i;
  insert into public.allowed_players (user_id, source, note) values (q, 'qa', 'ensaio #137');
  delete from public.funnel_counters;

  ---------------------------------------------------------------- RLS e direitos
  select (select bool_and(relrowsecurity) from pg_class where oid in ('public.funnel_counters'::regclass, 'public.funnel_catalog'::regclass))
         and not exists (select 1 from pg_policy where polrelid in ('public.funnel_counters'::regclass, 'public.funnel_catalog'::regclass))
    into bo;
  out := out || 'rls=' || case when bo then 'ok' else 'FALHOU' end || ' | ';

  perform set_config('request.jwt.claims', json_build_object('sub', u, 'role', 'authenticated')::text, true);
  s := '';
  begin execute 'set local role anon'; perform 1 from public.funnel_counters limit 1; s := 'FALHOU-leu';
  exception when insufficient_privilege then s := 'negado'; when others then s := 'FALHOU(' || sqlstate || ')'; end;
  execute 'reset role';
  begin execute 'set local role anon'; perform 1 from public.funnel_catalog limit 1; s := 'FALHOU-leu';
  exception when insufficient_privilege then null; when others then s := 'FALHOU(' || sqlstate || ')'; end;
  execute 'reset role';
  out := out || 'anon_tab=' || s || ' | ';
  s := '';
  begin execute 'set local role authenticated'; perform 1 from public.funnel_counters limit 1; s := 'FALHOU-leu';
  exception when insufficient_privilege then s := 'negado'; when others then s := 'FALHOU(' || sqlstate || ')'; end;
  execute 'reset role';
  begin execute 'set local role authenticated'; perform 1 from public.funnel_catalog limit 1; s := 'FALHOU-leu';
  exception when insufficient_privilege then null; when others then s := 'FALHOU(' || sqlstate || ')'; end;
  execute 'reset role';
  out := out || 'auth_tab=' || s || ' | ';

  s := '';
  begin execute 'set local role anon'; perform 1 from public.funnel_eventos_semanais limit 1; s := 'FALHOU';
  exception when insufficient_privilege then s := 'negado'; when others then s := 'FALHOU(' || sqlstate || ')'; end;
  execute 'reset role';
  begin execute 'set local role anon'; perform 1 from public.funnel_convites_semanais limit 1; s := 'FALHOU';
  exception when insufficient_privilege then null; when others then s := 'FALHOU(' || sqlstate || ')'; end;
  execute 'reset role';
  begin execute 'set local role anon'; perform 1 from public.funnel_salas_fim_semanais limit 1; s := 'FALHOU';
  exception when insufficient_privilege then null; when others then s := 'FALHOU(' || sqlstate || ')'; end;
  execute 'reset role';
  out := out || 'anon_views=' || s || ' | ';
  s := '';
  begin execute 'set local role authenticated'; perform 1 from public.funnel_eventos_semanais limit 1; s := 'FALHOU';
  exception when insufficient_privilege then s := 'negado'; when others then s := 'FALHOU(' || sqlstate || ')'; end;
  execute 'reset role';
  begin execute 'set local role authenticated'; perform 1 from public.funnel_convites_semanais limit 1; s := 'FALHOU';
  exception when insufficient_privilege then null; when others then s := 'FALHOU(' || sqlstate || ')'; end;
  execute 'reset role';
  begin execute 'set local role authenticated'; perform 1 from public.funnel_salas_fim_semanais limit 1; s := 'FALHOU';
  exception when insufficient_privilege then null; when others then s := 'FALHOU(' || sqlstate || ')'; end;
  execute 'reset role';
  out := out || 'auth_views=' || s || ' | ';

  s := '';
  begin execute 'set local role anon'; perform public.log_funnel_event('store_viewed', 'menu', 'android'); s := 'FALHOU';
  exception when insufficient_privilege then s := 'negado'; when others then s := 'FALHOU(' || sqlstate || ')'; end;
  execute 'reset role';
  out := out || 'anon_fn=' || s || ' | ';
  s := '';
  begin execute 'set local role authenticated'; perform public.purge_funnel(); s := 'FALHOU';
  exception when insufficient_privilege then s := 'negado'; when others then s := 'FALHOU(' || sqlstate || ')'; end;
  execute 'reset role';
  out := out || 'auth_purge=' || s || ' | ';

  s := 'ok';
  begin
    execute 'set local role service_role';
    perform 1 from public.funnel_eventos_semanais limit 1;
    perform 1 from public.funnel_convites_semanais limit 1;
    perform 1 from public.funnel_salas_fim_semanais limit 1;
  exception when others then s := 'FALHOU(' || sqlstate || ')'; end;
  execute 'reset role';
  out := out || 'service_views=' || s || ' | ';

  ---------------------------------------------------------------- gravação (como authenticated)
  perform set_config('request.jwt.claims', json_build_object('sub', u, 'role', 'authenticated')::text, true);
  execute 'set local role authenticated';
  perform public.log_funnel_event('store_viewed', 'menu', 'android');
  execute 'reset role';
  select n into k from public.funnel_counters
   where day = hoje and event = 'store_viewed' and origin = 'menu' and platform = 'android' and not interno;
  out := out || 'grava_1=' || coalesce(k::text, 'nada') || ' ';
  execute 'set local role authenticated';
  perform public.log_funnel_event('store_viewed', 'menu', 'android');
  execute 'reset role';
  select n into k from public.funnel_counters
   where day = hoje and event = 'store_viewed' and origin = 'menu' and platform = 'android' and not interno;
  out := out || 'grava_2=' || coalesce(k::text, 'nada') || ' | ';

  -- fora do catálogo: nada gravado
  select count(*) into k from public.funnel_counters;
  execute 'set local role authenticated';
  perform public.log_funnel_event('evento_que_nao_existe', 'menu', 'android');
  perform public.log_funnel_event('store_viewed', 'origem_que_nao_existe', 'android');
  perform public.log_funnel_event('store_viewed', 'menu', 'windows');
  perform public.log_funnel_event('store viewed; drop table x', 'menu', 'android');
  execute 'reset role';
  select count(*) into k2 from public.funnel_counters;
  out := out || 'fora_catalogo=' || (k2 - k) || ' | ';

  s := 'ok';
  begin
    execute 'set local role authenticated';
    perform public.log_funnel_event(null, null, null);
    perform public.log_funnel_event('store_viewed', null, 'android');
  exception when others then s := 'FALHOU(' || sqlstate || ')'; end;
  execute 'reset role';
  out := out || 'erro_engolido=' || s || ' | ';

  -- sem sessão (auth.uid() nulo): nada
  perform set_config('request.jwt.claims', '', true);
  select count(*) into k from public.funnel_counters;
  execute 'set local role authenticated';
  perform public.log_funnel_event('store_viewed', 'popup', 'ios');
  execute 'reset role';
  select count(*) into k2 from public.funnel_counters;
  out := out || 'sem_sessao=' || (k2 - k) || ' | ';

  -- conta de teste, interna e QA caem em interno = true; a normal fica em interno = false
  foreach s in array array['t', 'i', 'q', 'u'] loop
    perform set_config('request.jwt.claims', json_build_object('sub',
      case s when 't' then t when 'i' then i when 'q' then q else u end, 'role', 'authenticated')::text, true);
    execute 'set local role authenticated';
    perform public.log_funnel_event('invite_shared', 'referral_card', 'android');
    execute 'reset role';
  end loop;
  select coalesce(sum(n) filter (where interno), 0), coalesce(sum(n) filter (where not interno), 0) into k, k2
    from public.funnel_counters
   where day = hoje and event = 'invite_shared' and origin = 'referral_card' and platform = 'android';
  out := out || format('interno=%s normal=%s | ', k, k2);

  ---------------------------------------------------------------- célula pequena e plataformas
  perform set_config('request.jwt.claims', json_build_object('sub', u, 'role', 'authenticated')::text, true);
  execute 'set local role authenticated';
  for x in 1..4 loop perform public.log_funnel_event('hook_shown', 'win', 'android'); end loop;
  for x in 1..5 loop perform public.log_funnel_event('hook_shown', 'loss', 'android'); end loop;
  for x in 1..5 loop perform public.log_funnel_event('hook_rematch', 'win', 'android'); end loop;
  for x in 1..5 loop perform public.log_funnel_event('hook_rematch', 'win', 'ios'); end loop;
  execute 'reset role';
  select n into k from public.funnel_eventos_semanais where semana = sem and evento = 'hook_shown' and origem = 'win' and plataforma = 'android';
  out := out || 'celula_4=' || coalesce(k::text, 'nulo') || ' ';
  select n into k from public.funnel_eventos_semanais where semana = sem and evento = 'hook_shown' and origem = 'loss' and plataforma = 'android';
  out := out || 'celula_5=' || coalesce(k::text, 'nulo') || ' | ';
  select count(*) into k from public.funnel_eventos_semanais where semana = sem and evento = 'hook_rematch' and n = 5;
  out := out || 'plataformas=' || k || ' | ';
  -- o contador interno (3) e o normal (1) ficam abaixo de 5: nada aparece, e o interno nunca entra
  select count(*) into k from public.funnel_eventos_semanais where semana = sem and evento = 'invite_shared' and n is not null;
  out := out || 'interno_na_view=' || k || ' | ';

  -- nenhuma coluna identifica pessoa
  select not exists (
    select 1 from information_schema.columns
     where table_schema = 'public'
       and table_name in ('funnel_counters', 'funnel_catalog', 'funnel_eventos_semanais', 'funnel_convites_semanais', 'funnel_salas_fim_semanais')
       and (column_name ~* '(user|email|name|apelido|nick|device)')) into bo;
  out := out || 'sem_id=' || case when bo then 'ok' else 'FALHOU' end || ' | ';

  ---------------------------------------------------------------- convites
  -- 10 convidados normais de u: 5 pagos. t convida um 11º (fora), w (teste) é convidado de u (fora).
  insert into public.referrals (invitee_id, inviter_id, code)
    select x2, u, 'ENSAIO' from unnest(v) x2;
  update public.referrals set inviter_paid_at = now() where invitee_id = any (v[1:5]);
  insert into public.referrals (invitee_id, inviter_id, code) values (w, u, 'ENSAIO');
  update public.referrals set inviter_paid_at = now() where invitee_id = w;
  insert into public.referrals (invitee_id, inviter_id, code) values (h[1], t, 'ENSAIO');
  select aceitos, aceitos_pagos into r from public.funnel_convites_semanais where semana = sem;
  out := out || format('convites aceitos=%s pagos=%s | ', r.aceitos, r.aceitos_pagos);
  update public.referrals set inviter_paid_at = null where invitee_id = v[5];
  select aceitos_pagos into k from public.funnel_convites_semanais where semana = sem;
  out := out || 'convites pagos<5=' || coalesce(k::text, 'nulo') || ' | ';

  ---------------------------------------------------------------- salas que terminam
  -- 5 fim normal (u x v1), 4 WO, 3 expiradas em espera, e 1 sala com convidado de teste (fora)
  insert into public.online_matches (host_id, guest_id, host_name, guest_name, status)
    select u, v[1], 'a', 'b', 'finished' from generate_series(1, 5);
  insert into public.online_matches (host_id, guest_id, host_name, guest_name, status, walkover)
    select u, v[2], 'a', 'b', 'abandoned', true from generate_series(1, 4);
  insert into public.online_matches (host_id, host_name, status)
    select u, 'a', 'abandoned' from generate_series(1, 3);
  insert into public.online_matches (host_id, guest_id, host_name, guest_name, status)
    select u, w, 'a', 'b', 'finished' from generate_series(1, 6);
  select n into k from public.funnel_salas_fim_semanais
   where semana = sem and motivo = 'fim_normal' and not ranqueada and not partida_rapida;
  select n into k2 from public.funnel_salas_fim_semanais
   where semana = sem and motivo = 'wo' and not ranqueada and not partida_rapida;
  select n into x from public.funnel_salas_fim_semanais
   where semana = sem and motivo = 'expirou_em_espera' and not ranqueada and not partida_rapida;
  out := out || format('salas: fim_normal=%s wo=%s expirou=%s | ', coalesce(k::text, 'nulo'), coalesce(k2::text, 'nulo'), coalesce(x::text, 'nulo'));

  ---------------------------------------------------------------- retenção
  delete from public.funnel_counters;
  insert into public.funnel_counters (day, event, origin, platform, interno, n) values
    ('2000-01-01', 'store_viewed', 'menu', 'android', false, 1),
    (hoje - 366, 'store_viewed', 'menu', 'android', false, 1),
    (hoje - 365, 'store_viewed', 'menu', 'android', false, 1);
  select public.purge_funnel() into k;
  select count(*) into k2 from public.funnel_counters where day = hoje - 365;
  select count(*) into x from public.funnel_counters where day = hoje - 366;
  out := out || format('purge=%s resta_-365=%s resta_-366=%s', k, k2, x);

  raise exception 'RESULTADO (rollback): %', out;
end $$;
