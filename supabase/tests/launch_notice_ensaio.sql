-- Ensaio que desfaz tudo (#36, 0.34.6): aviso de lançamento (supabase/launch_notice.sql).
-- Rodar no SQL Editor do projeto cwtslesnthbenxswdcbv. Antes de aplicar a migração, cole o conteúdo
-- de launch_notice.sql logo acima deste bloco, na MESMA execução: o raise no fim cancela a
-- transação inteira (tabela, funções, contas de ensaio e interruptor somem). Depois de aplicada,
-- basta rodar só este bloco. Sem e-mail real: contas de ensaio em example.test.
--
-- Resultado esperado:
--   interruptor_desligado: enabled=false                      nasce 'false', nada aparece
--   ligado_android: enabled=true | ligado_ios: enabled=false  servidor esconde no iPhone
--   play=tester manual=tester qa=tester ios=tester            lista e aparelho iPhone contam
--   medalha_sem_corte=new                                     beta_ends_at vazio: medalha não conta
--   medalha_antes_do_corte=tester medalha_depois_do_corte=new
--   nova=new                                                  conta comum nova
--   ack_linhas=1 seen_depois=true variante_gravada=tester     ack duas vezes grava uma linha
--   anon: variante=new seen=false | anon_ack=false            sem login; ack exige login
--   chave_ja_existia_preservada=true                          on conflict do nothing não sobrescreve

do $$
declare
  p_play uuid := gen_random_uuid(); p_man uuid := gen_random_uuid(); p_qa uuid := gen_random_uuid();
  p_ios uuid := gen_random_uuid(); p_med uuid := gen_random_uuid(); p_nova uuid := gen_random_uuid();
  r record; n int; v text; ack1 boolean; ack2 boolean; ack3 boolean;
  out text := '';
begin
  -- contas de ensaio (somem no rollback); o trigger de perfil cria o profile e, com beta_ends_at
  -- vazio, também a medalha Beta Tester de todas elas
  insert into auth.users (id, aud, role, email) values
    (p_play, 'authenticated', 'authenticated', 'ensaio.play@example.test'),
    (p_man,  'authenticated', 'authenticated', 'ensaio.manual@example.test'),
    (p_qa,   'authenticated', 'authenticated', 'ensaio.qa@example.test'),
    (p_ios,  'authenticated', 'authenticated', 'ensaio.ios@example.test'),
    (p_med,  'authenticated', 'authenticated', 'ensaio.medalha@example.test'),
    (p_nova, 'authenticated', 'authenticated', 'ensaio.nova@example.test');

  -- cenário: só p_med fica com a medalha; as outras entram por lista ou aparelho
  delete from public.user_badges where user_id in (p_play, p_man, p_qa, p_ios, p_nova);
  insert into public.allowed_players (email, source, note) values ('ensaio.play@example.test', 'play', 'ensaio');
  insert into public.allowed_players (user_id, source, note) values (p_man, 'manual', 'ensaio');
  insert into public.allowed_players (email, source, note) values ('ensaio.qa@example.test', 'qa', 'ensaio');
  insert into public.device_checks (user_id, app_version, platform, test_lab) values (p_ios, '0.0.0', 'ios', false);
  insert into public.user_badges (user_id, badge_code) values (p_med, 'beta_tester') on conflict do nothing;

  -- 1) interruptor desligado
  update public.app_config set value = 'false' where key = 'launch_notice_enabled';
  perform set_config('request.jwt.claims', json_build_object('sub', p_play, 'role', 'authenticated')::text, true);
  select * into r from public.launch_notice_status('android');
  out := out || format('interruptor_desligado: enabled=%s | ', r.enabled);

  -- 2) ligado: Android mostra, iPhone não
  update public.app_config set value = 'true' where key = 'launch_notice_enabled';
  select * into r from public.launch_notice_status('android');
  out := out || format('ligado_android: enabled=%s | ', r.enabled);
  select * into r from public.launch_notice_status('ios');
  out := out || format('ligado_ios: enabled=%s | ', r.enabled);

  -- 3) variantes (beta_ends_at vazio)
  update public.app_config set value = null where key = 'beta_ends_at';
  out := out || format('play=%s ', case when public.launch_was_beta_tester(p_play) then 'tester' else 'new' end);
  out := out || format('manual=%s ', case when public.launch_was_beta_tester(p_man) then 'tester' else 'new' end);
  out := out || format('qa=%s ', case when public.launch_was_beta_tester(p_qa) then 'tester' else 'new' end);
  out := out || format('ios=%s | ', case when public.launch_was_beta_tester(p_ios) then 'tester' else 'new' end);
  out := out || format('medalha_sem_corte=%s | ', case when public.launch_was_beta_tester(p_med) then 'tester' else 'new' end);

  -- medalha com corte: conta criada antes do corte conta; criada depois não
  update public.app_config set value = (now() + interval '1 day')::text where key = 'beta_ends_at';
  out := out || format('medalha_antes_do_corte=%s ', case when public.launch_was_beta_tester(p_med) then 'tester' else 'new' end);
  update public.app_config set value = (now() - interval '1 day')::text where key = 'beta_ends_at';
  out := out || format('medalha_depois_do_corte=%s | ', case when public.launch_was_beta_tester(p_med) then 'tester' else 'new' end);
  out := out || format('nova=%s | ', case when public.launch_was_beta_tester(p_nova) then 'tester' else 'new' end);

  -- 4) ack duas vezes grava uma linha, com a variante do servidor
  perform set_config('request.jwt.claims', json_build_object('sub', p_play, 'role', 'authenticated')::text, true);
  ack1 := public.launch_notice_ack();
  ack2 := public.launch_notice_ack();
  select count(*) into n from public.launch_notice_seen where user_id = p_play;
  select s.variant into v from public.launch_notice_seen s where s.user_id = p_play;
  select * into r from public.launch_notice_status('android');
  out := out || format('ack_linhas=%s seen_depois=%s variante_gravada=%s | ', n, r.seen, v);

  -- 5) sem login
  perform set_config('request.jwt.claims', '{}', true);
  select * into r from public.launch_notice_status('android');
  out := out || format('anon: variante=%s seen=%s | ', r.variant, r.seen);
  ack3 := public.launch_notice_ack();
  out := out || format('anon_ack=%s | ', ack3);

  -- 6) o seed não sobrescreve a chave que já existe
  update public.app_config set value = 'true' where key = 'launch_notice_enabled';
  insert into public.app_config (key, value) values ('launch_notice_enabled', 'false') on conflict (key) do nothing;
  out := out || format('chave_ja_existia_preservada=%s',
                       (select value from public.app_config where key = 'launch_notice_enabled') = 'true');

  raise exception 'RESULTADO (rollback): %', out;
end $$;
