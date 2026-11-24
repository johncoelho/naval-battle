-- Ensaio que desfaz tudo (#124, 0.48.6): bandeira de nacionalidade (supabase/country.sql).
-- Rode no SQL Editor do projeto cwtslesnthbenxswdcbv ANTES de aplicar a migração: cole o texto inteiro de
-- country.sql e este bloco na MESMA execução, dentro de `begin; ... ; rollback;` se o editor não for atômico.
-- O raise no fim cancela a transação inteira (coluna, funções, gatilho, usuários e linhas) e devolve o resultado na
-- mensagem de erro. Usuários fictícios a e b (e-mail @ensaio.invalid); nenhum jogador real é tocado.
--
-- Resultado esperado (qualquer FALHOU reprova o ensaio):
--   coluna=ok check=ok lista=77         coluna nula por padrão, check de formato e a lista igual à do app
--   novo=nulo                           perfil novo nasce sem bandeira
--   grava=BR                            o upsert do app (ON CONFLICT) grava um código da lista
--   XX=BR br=BR BRA=BR                  fora da lista: ignorado sem erro, a bandeira anterior fica
--   insert_XX=nulo                      insert com código inválido: sem bandeira, sem erro
--   vazio=nulo                          "Prefiro não mostrar" (texto vazio) apaga
--   app_antigo=AR                       upsert sem o campo (app antigo) não apaga a bandeira
--   outro=negado b=nulo                 a não grava a bandeira de b (RLS de profiles)
--   proprio=CL                          a grava a própria pelo PostgREST (papel authenticated)
--   oponente=CL                         opponent_profile devolve a bandeira de a
--   placar=ok                           leaderboard_overall e leaderboard_season devolvem country_code
--   anon_fn=negado                      anon não executa opponent_profile nem os placares
--   auth_fn=ok                          authenticated executa os três

do $$
declare
  a uuid := gen_random_uuid(); b uuid := gen_random_uuid();
  out text := ''; s text; n integer; bo boolean;
begin
  ---------------------------------------------------------------- cenário
  insert into auth.users (id, email)
    select u, 'ensaio-' || u || '@ensaio.invalid' from unnest(array[a, b]) u;
  insert into public.profiles (id, username) values (a, 'EnsaioA' || left(a::text, 4))
    on conflict (id) do nothing;
  insert into public.profiles (id, username) values (b, 'EnsaioB' || left(b::text, 4))
    on conflict (id) do nothing;

  ---------------------------------------------------------------- estrutura
  select count(*) into n from information_schema.columns
   where table_schema = 'public' and table_name = 'profiles' and column_name = 'country_code'
     and is_nullable = 'YES' and column_default is null;
  out := out || 'coluna=' || case when n = 1 then 'ok' else 'FALHOU' end || ' ';
  select exists (select 1 from pg_constraint where conname = 'profiles_country_code_format') into bo;
  out := out || 'check=' || case when bo then 'ok' else 'FALHOU' end || ' ';
  out := out || 'lista=' || coalesce(array_length(public.country_codes(), 1), 0) || ' | ';

  select country_code into s from public.profiles where id = a;
  out := out || 'novo=' || coalesce(s, 'nulo') || ' | ';

  ---------------------------------------------------------------- gatilho (como o upsert do app)
  insert into public.profiles (id, country_code) values (a, 'BR')
    on conflict (id) do update set country_code = excluded.country_code;
  select country_code into s from public.profiles where id = a;
  out := out || 'grava=' || coalesce(s, 'nulo') || ' | ';

  insert into public.profiles (id, country_code) values (a, 'XX')
    on conflict (id) do update set country_code = excluded.country_code;
  select country_code into s from public.profiles where id = a;
  out := out || 'XX=' || coalesce(s, 'nulo') || ' ';
  insert into public.profiles (id, country_code) values (a, 'br')
    on conflict (id) do update set country_code = excluded.country_code;
  select country_code into s from public.profiles where id = a;
  out := out || 'br=' || coalesce(s, 'nulo') || ' ';
  begin
    update public.profiles set country_code = 'BRA' where id = a;
    select country_code into s from public.profiles where id = a;
    out := out || 'BRA=' || coalesce(s, 'nulo') || ' | ';
  exception when others then out := out || 'BRA=FALHOU(' || sqlstate || ') | ';
  end;

  -- insert de verdade (sem linha anterior) com código inválido: um terceiro usuário só para isto
  declare c uuid := gen_random_uuid();
  begin
    insert into auth.users (id, email) values (c, 'ensaio-' || c || '@ensaio.invalid');
    insert into public.profiles (id, username, country_code) values (c, 'EnsaioC' || left(c::text, 4), 'XX')
      on conflict (id) do update set country_code = excluded.country_code;
    select country_code into s from public.profiles where id = c;
    out := out || 'insert_XX=' || coalesce(s, 'nulo') || ' | ';
  exception when others then out := out || 'insert_XX=FALHOU(' || sqlstate || ') | ';
  end;

  update public.profiles set country_code = '' where id = a;
  select country_code into s from public.profiles where id = a;
  out := out || 'vazio=' || coalesce(s, 'nulo') || ' | ';

  update public.profiles set country_code = 'AR' where id = a;
  -- app antigo: o upsert não traz a coluna, então o ON CONFLICT não a toca
  insert into public.profiles (id, xp) values (a, 10)
    on conflict (id) do update set xp = excluded.xp;
  select country_code into s from public.profiles where id = a;
  out := out || 'app_antigo=' || coalesce(s, 'nulo') || ' | ';

  ---------------------------------------------------------------- RLS: só o dono grava
  perform set_config('request.jwt.claims', json_build_object('sub', a, 'role', 'authenticated')::text, true);
  execute 'set local role authenticated';
  update public.profiles set country_code = 'UY' where id = b;
  get diagnostics n = row_count;
  update public.profiles set country_code = 'CL' where id = a;
  execute 'reset role';
  select country_code into s from public.profiles where id = b;
  out := out || 'outro=' || case when n = 0 then 'negado' else 'FALHOU' end || ' b=' || coalesce(s, 'nulo') || ' ';
  select country_code into s from public.profiles where id = a;
  out := out || 'proprio=' || coalesce(s, 'nulo') || ' | ';

  ---------------------------------------------------------------- RPCs
  execute 'set local role authenticated';
  select o.country_code into s from public.opponent_profile(a) o;
  execute 'reset role';
  out := out || 'oponente=' || coalesce(s, 'nulo') || ' | ';

  select pg_get_function_result('public.leaderboard_overall(integer)'::regprocedure) like '%country_code text%'
     and pg_get_function_result('public.leaderboard_season(integer)'::regprocedure) like '%country_code text%'
    into bo;
  out := out || 'placar=' || case when bo then 'ok' else 'FALHOU' end || ' | ';

  select not has_function_privilege('anon', 'public.opponent_profile(uuid)', 'execute')
     and not has_function_privilege('anon', 'public.leaderboard_overall(integer)', 'execute')
     and not has_function_privilege('anon', 'public.leaderboard_season(integer)', 'execute')
    into bo;
  out := out || 'anon_fn=' || case when bo then 'negado' else 'FALHOU' end || ' ';
  select has_function_privilege('authenticated', 'public.opponent_profile(uuid)', 'execute')
     and has_function_privilege('authenticated', 'public.leaderboard_overall(integer)', 'execute')
     and has_function_privilege('authenticated', 'public.leaderboard_season(integer)', 'execute')
    into bo;
  out := out || 'auth_fn=' || case when bo then 'ok' else 'FALHOU' end;

  raise exception 'RESULTADO (rollback): %', out;
end $$;
