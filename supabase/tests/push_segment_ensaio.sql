-- Ensaio que desfaz tudo (#117): push segmentado por versão e plataforma (supabase/push_segment.sql).
-- Rode no SQL Editor do projeto cwtslesnthbenxswdcbv: cole o texto inteiro de push_segment.sql e este arquivo na
-- MESMA execução (a migração ainda não precisa estar aplicada). O raise no fim cancela a transação inteira (DDL,
-- usuários, tokens e linhas da fila) e devolve o resultado na mensagem de erro. O pg_net só envia depois do commit,
-- então NADA é enviado. Usuários e tokens fictícios (prefixo ensaio-seg-); nenhuma conta real é tocada.
--
-- Cenário fictício (todos android, salvo o G):
--   A anônimo (sem conta)            -> desconhecida  : só "atualize"
--   B conta com 0.44.6 e 0.45.1      -> usa a maior   : só convite
--   C conta com 0.44.6               -> abaixo        : só "atualize"
--   D conta sem linha em device_checks -> desconhecida: só "atualize"
--   E conta com 0.9.0                -> numérico (0.9 < 0.45): só "atualize"
--   F token 'unregistered' de conta 0.45.1                 : fora de tudo
--   G iPhone de conta 0.45.1 (device_checks ios)           : fora do filtro platform=android
--   H conta cuja versão 0.45.1 é do iPhone, token Android  : versão do Android desconhecida -> "atualize", nunca convite
-- Resultado esperado:
--   real_convite_android ~1 | real_atualize_android ~241 | real_overlap=0 | real_ios_convite=0
--   (18/11: 291 tokens Android ativos; 49 são contas robô/Test Lab e ficam de fora, 2 já tinham 0.45+ e 1 era robô;
--    sem o filtro de robô seriam 2 e 289)
--   fict convite=B | atualize=A,C,D,E,H | overlap=0 | ios_convite=B,G (sem filtro de plataforma: B android e G iPhone)
--   outbox_convite: linha com min_version=0.45.0 e platform=android | vazio=sem linha | sem_filtro=linha sem filtro
do $$
declare
  u_b uuid := gen_random_uuid(); u_c uuid := gen_random_uuid(); u_d uuid := gen_random_uuid();
  u_e uuid := gen_random_uuid(); u_f uuid := gen_random_uuid(); u_g uuid := gen_random_uuid(); u_h uuid := gen_random_uuid();
  out text := ''; c1 int; c2 int; c3 int; s text; n int; o record;
begin
  -- contagens reais, antes de qualquer dado fictício
  select count(*) into c1 from public.push_segment_tokens('0.45.0', null, 'android');
  select count(*) into c2 from public.push_segment_tokens(null, '0.45.0', 'android');
  select count(*) into c3 from public.push_segment_tokens('0.45.0', null, 'android') a
    join public.push_segment_tokens(null, '0.45.0', 'android') b using (token);
  out := out || format('real_convite_android=%s | real_atualize_android=%s | real_overlap=%s | ', c1, c2, c3);
  select count(*) into c1 from public.push_segment_tokens('0.45.0', null, 'ios');
  out := out || format('real_ios_convite=%s | ', c1);

  insert into auth.users (id, email) select x, 'ensaio-seg-' || x || '@example.invalid'
    from unnest(array[u_b, u_c, u_d, u_e, u_f, u_g, u_h]) x;
  insert into public.push_tokens (token, user_id, platform, check_status) values
    ('ensaio-seg-A-aaaaaaaaaaaaaaaaaaaa', null, 'android', null),
    ('ensaio-seg-B-aaaaaaaaaaaaaaaaaaaa', u_b, 'android', null),
    ('ensaio-seg-C-aaaaaaaaaaaaaaaaaaaa', u_c, 'android', null),
    ('ensaio-seg-D-aaaaaaaaaaaaaaaaaaaa', u_d, 'android', null),
    ('ensaio-seg-E-aaaaaaaaaaaaaaaaaaaa', u_e, 'android', null),
    ('ensaio-seg-F-aaaaaaaaaaaaaaaaaaaa', u_f, 'android', 'unregistered'),
    ('ensaio-seg-G-aaaaaaaaaaaaaaaaaaaa', u_g, 'ios', null),
    ('ensaio-seg-H-aaaaaaaaaaaaaaaaaaaa', u_h, 'android', null);
  insert into public.device_checks (user_id, app_version, platform, test_lab) values
    (u_b, '0.44.6', 'android', false), (u_b, '0.45.1', 'android', false),
    (u_c, '0.44.6', 'android', false),
    (u_e, '0.9.0', 'android', false),
    (u_f, '0.45.1', 'android', false),
    (u_g, '0.45.1', 'ios', false),
    (u_h, '0.45.1', 'ios', false);

  select string_agg(substr(token, 12, 1), ',' order by token) into s
    from public.push_segment_tokens('0.45.0', null, 'android') where token like 'ensaio-seg-%';
  out := out || 'fict convite=' || coalesce(s, '') || ' | ';
  select string_agg(substr(token, 12, 1), ',' order by token) into s
    from public.push_segment_tokens(null, '0.45.0', 'android') where token like 'ensaio-seg-%';
  out := out || 'atualize=' || coalesce(s, '') || ' | ';
  select count(*) into n from public.push_segment_tokens('0.45.0', null, 'android') a
    join public.push_segment_tokens(null, '0.45.0', 'android') b using (token) where a.token like 'ensaio-seg-%';
  out := out || 'overlap=' || n || ' | ';
  select string_agg(substr(token, 12, 1), ',' order by token) into s
    from public.push_segment_tokens('0.45.0', null, null) where token like 'ensaio-seg-%';
  out := out || 'ios_convite=' || coalesce(s, '') || ' | ';

  -- enfileiramento: só confere a linha; o pg_net não envia (rollback)
  perform public.push_broadcast('t', 'b', 'quick_match_invite', '0.45.0', null, 'android');
  select * into o from public.push_outbox where kind = 'quick_match_invite' order by id desc limit 1;
  out := out || format('outbox_convite=%s/%s/%s/%s | ', o.user_id is null, o.min_version, o.max_version_excl, o.platform);
  delete from public.push_outbox where id = o.id;
  perform public.push_broadcast('t', 'b', 'quick_match_invite', '99.0.0', null, 'android');
  select count(*) into n from public.push_outbox where kind = 'quick_match_invite';
  out := out || 'vazio=' || n || ' | ';
  perform public.push_broadcast('t', 'b', 'ensaio_sem_filtro');
  select * into o from public.push_outbox where kind = 'ensaio_sem_filtro';
  out := out || format('sem_filtro=%s/%s/%s | ', o.min_version is null, o.max_version_excl is null, o.platform is null);

  raise exception 'RESULTADO (rollback): %', out;
end $$;
