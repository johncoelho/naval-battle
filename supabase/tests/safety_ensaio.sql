-- Ensaio que desfaz tudo (#58, 0.42.0): denunciar, bloquear e filtro de nome (supabase/safety.sql).
-- Rodar no SQL Editor do projeto cwtslesnthbenxswdcbv ANTES de aplicar a migração `player_safety`:
-- cole o texto inteiro de safety.sql e este bloco na MESMA execução. O raise no fim cancela a
-- transação inteira, DDL inclusive, e devolve o resultado na mensagem de erro.
-- Contas descartáveis criadas dentro do bloco (nenhuma conta real nem QA é tocada); o e-mail do aviso
-- ao John é trocado por um e-mail de ensaio só dentro do bloco.
--
-- Resultado esperado (cada item vira um trecho de "RESULTADO"):
--   migracao_trocou=N/total      nomes atuais que violam e foram trocados (também é a contagem da issue)
--   antes_B_ve=1                 sem bloqueio, B enxerga a sala rápida aberta de A
--   A_bloqueia_B=ok              block_player
--   B_select=0 B_oferta=0 B_join=0   B não vê, não recebe a oferta e não entra na sala de A
--   A_select=1                   o anfitrião vê a própria sala
--   C_select=1                   quem não foi bloqueado continua vendo (controle)
--   convite_B_para_A=barrado convite_A_para_B=barrado   política de criar sala
--   amizade_B_para_A=barrado amizade_A_para_B=barrado   política de pedir amizade
--   busca_B_acha_A=0 busca_A_acha_B=0 busca_C_acha_A=1  busca nos dois sentidos
--   amizade_apagada=0            a amizade aceita some ao bloquear
--   sala_convite_abandonada=abandoned   sala 'waiting' mirando um no outro é encerrada
--   desbloqueio=ok B_volta=1     desbloquear devolve a visão
--   bloqueio_self=self bloqueio_invalido=invalid
--   upsert_ofensivo=<nome antigo> upsert_erro=nao   app antigo: mantém o nome, sem erro
--   insert_ofensivo=Comandante-xxxxxx   insert com nome ofensivo vira o padrão
--   conta_nova_google=Comandante-xxxxxx handle_new_user
--   sala_nome=<nome do perfil>   host_name ofensivo vira o nome do perfil
--   set_username: ok/offensive/taken/invalid
--   termos_ofensivos=N/N termos_ok=N/N   variações (acento, leet, separador, repetição) e exceções
--   denuncia=ok repetida=already propria=self invalida=invalid limite=limit
--   terceiro_troca_nome=Comandante-xxxxxx denuncias_status=3 name_reset  reset_registrado=1
--   push_john=1 push_log=1       um único push, mesmo com 3 denúncias na hora
--   aviso_nome=1 aviso_de_novo=0 my_name_reset / name_reset_ack
--   nome_velho_volta=nao set_username_velho=taken   o nome trocado à força não volta por 30 dias
--   suspenso_no_ranking=0 normal_no_ranking=1 suspenso_na_busca=0 suspenso_cliente_grava=mantido
--   purge_apagou=0/0 recente_fica=true   (restam 0 denúncias e 0 trocas de nome com mais de 12 meses; as de agora ficam)
--   my_blocks=1 anon_executa=nenhuma auth_executa=todas admin_auth=nao
do $$
declare
  a uuid := gen_random_uuid(); b uuid := gen_random_uuid(); c uuid := gen_random_uuid();
  t uuid := gen_random_uuid(); j uuid := gen_random_uuid(); s uuid := gen_random_uuid();
  d uuid := gen_random_uuid(); g uuid := gen_random_uuid();
  room uuid := gen_random_uuid(); room2 uuid := gen_random_uuid();
  out text := ''; f_old int; n int; v text; tot int; fs text[]; f text; bad text := '';
  bad_names text[] := array['Caralho', 'CARÁLHO', 'c4r4lh0', 'c.a.r.a.l.h.o', 'caaaralhooo', 'Fuuuck', 'f-u-c-k-e-r',
    'Sh1ttyPlayer', 'FilhodaPuta', 'M3rd4', 'p0rr4', 'Puta', 'xX_Hitler_Xx', 'NaZiSta', 'm1erd@', 'Bitch', 'N1gg3r', 'c u z a o'];
  ok_names text[] := array['Cuca', 'Escudo', 'Computador', 'Enviado', 'Nigeria', 'Therapist', 'Capitao Silva', 'Scunthorpe',
    'Assassino', 'Cassino', 'Pirata', 'Almirante', 'Notario', 'Evergarden', 'Skipper'];
  x text;
begin
  -- ------------------------------------------------------------ cenário
  insert into auth.users (id, email) select u, 'ensaio-' || u || '@ensaio.invalid' from unnest(array[a, b, c, t, j, s, d]) u;
  update public.profiles set is_test = false where id in (a, b, c, t, j, s, d);
  update public.profiles set username = 'Ensq-a' where id = a;
  update public.profiles set username = 'Ensq-b' where id = b;
  update public.profiles set username = 'Ensq-c' where id = c;
  update public.profiles set username = 'Capitao Teste' where id = t;
  update public.profiles set username = 'Ensq-s' where id = s;
  insert into public.push_tokens (token, user_id) values ('ensaio-token-aaaaaaaaaaaaaaaaaaaa', j);
  update public.app_config set value = 'ensaio-' || j || '@ensaio.invalid' where key = 'moderation_notify_emails';

  select count(*) into n from public.name_resets where reason = 'migration';
  select count(*) into tot from public.profiles;
  out := out || format('migracao_trocou=%s/%s | ', n, tot);

  insert into public.online_matches (id, host_id, host_name, is_quick_match, status) values (room, a, 'Alfa', true, 'waiting');
  insert into public.friendships (requester_id, addressee_id, requester_username, addressee_username, status)
    values (a, b, 'Ensq-a', 'Ensq-b', 'accepted');

  -- ------------------------------------------------------------ bloqueio
  perform set_config('request.jwt.claims', json_build_object('sub', b, 'role', 'authenticated')::text, true);
  set local role authenticated;
  select count(*) into n from public.online_matches where id = room;
  out := out || format('antes_B_ve=%s | ', n);
  reset role;

  perform set_config('request.jwt.claims', json_build_object('sub', a, 'role', 'authenticated')::text, true);
  set local role authenticated;
  out := out || format('A_bloqueia_B=%s | ', public.block_player(b));
  reset role;

  -- B (bloqueado)
  perform set_config('request.jwt.claims', json_build_object('sub', b, 'role', 'authenticated')::text, true);
  set local role authenticated;
  select count(*) into n from public.online_matches where id = room;
  out := out || format('B_select=%s | ', n);
  select count(*) into n from public.find_quick_offer(true, true);
  out := out || format('B_oferta=%s | ', n);
  select count(*) into n from public.join_online_match(room, 'Bravo');
  out := out || format('B_join=%s | ', n);
  begin
    insert into public.online_matches (host_id, host_name, invited_id) values (b, 'Bravo', a);
    out := out || 'convite_B_para_A=PASSOU | ';
  exception when others then out := out || 'convite_B_para_A=barrado | ';
  end;
  begin
    insert into public.friendships (requester_id, addressee_id, requester_username, addressee_username) values (b, a, 'Ensq-b', 'Ensq-a');
    out := out || 'amizade_B_para_A=PASSOU | ';
  exception when others then out := out || 'amizade_B_para_A=barrado | ';
  end;
  select count(*) into n from public.search_commander('Ensq-a');
  out := out || format('busca_B_acha_A=%s | ', n);
  reset role;

  -- A (quem bloqueou)
  perform set_config('request.jwt.claims', json_build_object('sub', a, 'role', 'authenticated')::text, true);
  set local role authenticated;
  select count(*) into n from public.online_matches where id = room;
  out := out || format('A_select=%s | ', n);
  begin
    insert into public.online_matches (host_id, host_name, invited_id) values (a, 'Alfa', b);
    out := out || 'convite_A_para_B=PASSOU | ';
  exception when others then out := out || 'convite_A_para_B=barrado | ';
  end;
  begin
    insert into public.friendships (requester_id, addressee_id, requester_username, addressee_username) values (a, b, 'Ensq-a', 'Ensq-b');
    out := out || 'amizade_A_para_B=PASSOU | ';
  exception when others then out := out || 'amizade_A_para_B=barrado | ';
  end;
  select count(*) into n from public.search_commander('Ensq-b');
  out := out || format('busca_A_acha_B=%s | ', n);
  select count(*) into n from public.my_blocks();
  out := out || format('my_blocks=%s | ', n);
  out := out || format('bloqueio_self=%s bloqueio_invalido=%s | ', public.block_player(a), public.block_player(null));
  reset role;
  select count(*) into n from public.friendships where (requester_id = a and addressee_id = b) or (requester_id = b and addressee_id = a);
  out := out || format('amizade_apagada=%s | ', n);

  -- C (controle, ninguém bloqueou)
  perform set_config('request.jwt.claims', json_build_object('sub', c, 'role', 'authenticated')::text, true);
  set local role authenticated;
  select count(*) into n from public.online_matches where id = room;
  out := out || format('C_select=%s | ', n);
  select count(*) into n from public.search_commander('Ensq-a');
  out := out || format('busca_C_acha_A=%s | ', n);
  reset role;

  -- bloquear encerra a sala mirada de um no outro
  insert into public.online_matches (id, host_id, host_name, invited_id) values (room2, c, 'Charlie', d);
  perform set_config('request.jwt.claims', json_build_object('sub', d, 'role', 'authenticated')::text, true);
  perform public.block_player(c);
  select status into v from public.online_matches where id = room2;
  out := out || format('sala_convite_abandonada=%s | ', v);

  -- desbloquear devolve a visão
  perform set_config('request.jwt.claims', json_build_object('sub', a, 'role', 'authenticated')::text, true);
  out := out || format('desbloqueio=%s | ', public.unblock_player(b));
  perform set_config('request.jwt.claims', json_build_object('sub', b, 'role', 'authenticated')::text, true);
  set local role authenticated;
  select count(*) into n from public.online_matches where id = room;
  out := out || format('B_volta=%s | ', n);
  reset role;

  -- ------------------------------------------------------------ nome de guerra (app antigo não leva erro)
  perform set_config('request.jwt.claims', json_build_object('sub', c, 'role', 'authenticated')::text, true);
  set local role authenticated;
  begin
    insert into public.profiles (id, username) values (c, 'Caralho')
    on conflict (id) do update set username = excluded.username;
    out := out || 'upsert_erro=nao | ';
  exception when others then out := out || 'upsert_erro=SIM:' || sqlerrm || ' | ';
  end;
  reset role;
  select username into v from public.profiles where id = c;
  out := out || format('upsert_ofensivo=%s | ', v);

  -- insert direto com nome ofensivo (perfil apagado antes)
  delete from public.profiles where id = d;
  perform set_config('request.jwt.claims', json_build_object('sub', d, 'role', 'authenticated')::text, true);
  set local role authenticated;
  insert into public.profiles (id, username) values (d, 'Caralho');
  reset role;
  select username into v from public.profiles where id = d;
  out := out || format('insert_ofensivo=%s | ', v);

  insert into auth.users (id, email, raw_user_meta_data) values (g, 'ensaio-g@ensaio.invalid', '{"full_name":"Filho da Puta"}');
  select username into v from public.profiles where id = g;
  out := out || format('conta_nova_google=%s | ', v);

  -- cópias gravadas pelo cliente: nome ofensivo vira o do perfil
  perform set_config('request.jwt.claims', json_build_object('sub', c, 'role', 'authenticated')::text, true);
  set local role authenticated;
  insert into public.online_matches (id, host_id, host_name) values (gen_random_uuid(), c, 'Caralho') returning host_name into v;
  reset role;
  out := out || format('sala_nome=%s | ', v);

  -- set_username
  perform set_config('request.jwt.claims', json_build_object('sub', a, 'role', 'authenticated')::text, true);
  out := out || format('set_username=%s/%s/%s/%s/%s | ',
    public.set_username('Almirante Alfa'), public.set_username('Cabrón'), public.set_username('Ensq-b'),
    public.set_username('ab'), public.set_username('  '));
  -- (ok/offensive/taken/invalid/invalid)

  -- lista de termos
  n := 0;
  foreach x in array bad_names loop
    if public.name_is_offensive(x) then n := n + 1; else bad := bad || ' [deveria reprovar: ' || x || ']'; end if;
  end loop;
  out := out || format('termos_ofensivos=%s/%s | ', n, array_length(bad_names, 1));
  n := 0;
  foreach x in array ok_names loop
    if not public.name_is_offensive(x) then n := n + 1; else bad := bad || ' [deveria passar: ' || x || ']'; end if;
  end loop;
  out := out || format('termos_ok=%s/%s | ', n, array_length(ok_names, 1));

  -- ------------------------------------------------------------ denúncias
  perform set_config('request.jwt.claims', json_build_object('sub', a, 'role', 'authenticated')::text, true);
  out := out || format('denuncia=%s ', public.report_player(t, 'name', 'ranking'));
  out := out || format('repetida=%s ', public.report_player(t, 'name', 'ranking'));
  out := out || format('invalida=%s | ', public.report_player(t, 'xingamento', 'ranking'));
  perform set_config('request.jwt.claims', json_build_object('sub', t, 'role', 'authenticated')::text, true);
  out := out || format('propria=%s | ', public.report_player(t, 'name', 'search'));
  -- limite diário
  update public.app_config set value = '1' where key = 'reports_daily_cap';
  perform set_config('request.jwt.claims', json_build_object('sub', d, 'role', 'authenticated')::text, true);
  out := out || format('limite=%s/%s | ', public.report_player(t, 'abuse', 'match'), public.report_player(t, 'cheat', 'match'));
  update public.app_config set value = '10' where key = 'reports_daily_cap';
  -- 2º e 3º denunciantes distintos do nome
  perform set_config('request.jwt.claims', json_build_object('sub', b, 'role', 'authenticated')::text, true);
  perform public.report_player(t, 'name', 'friends');
  select username into v from public.profiles where id = t;
  out := out || format('antes_do_terceiro=%s | ', v);
  perform set_config('request.jwt.claims', json_build_object('sub', c, 'role', 'authenticated')::text, true);
  perform public.report_player(t, 'name', 'result');
  select username into v from public.profiles where id = t;
  out := out || format('terceiro_troca_nome=%s | ', v);
  out := out || format('denuncias_status=%s %s | ',
    (select count(*) from public.player_reports where reported_id = t and reason = 'name'),
    (select string_agg(distinct status, ',') from public.player_reports where reported_id = t and reason = 'name'));
  out := out || format('reset_registrado=%s | ', (select count(*) from public.name_resets where user_id = t and reason = 'reports'));
  out := out || format('push_john=%s push_log=%s | ',
    (select count(*) from public.push_outbox where user_id = j and kind = 'moderation'),
    (select count(*) from public.moderation_push_log));
  -- aviso único no app
  perform set_config('request.jwt.claims', json_build_object('sub', t, 'role', 'authenticated')::text, true);
  select count(*) into n from public.my_name_reset();
  perform public.name_reset_ack();
  out := out || format('aviso_nome=%s aviso_de_novo=%s | ', n, (select count(*) from public.my_name_reset()));
  -- fila do administrador
  -- o nome trocado à força não volta por 30 dias (nem pelo upsert do app antigo, nem por set_username)
  update public.profiles set username = 'Capitao Teste' where id = t;
  select username into v from public.profiles where id = t;
  out := out || format('nome_velho_volta=%s set_username_velho=%s | ',
    case when v = 'Capitao Teste' then 'VOLTOU' else 'nao' end, public.set_username('Capitao Teste'));
  out := out || format('pendentes=%s | ', (select count(*) from public.reports_pending()));
  out := out || format('revisao=%s | ', public.review_report(t, 'dismiss'));

  -- ------------------------------------------------------------ suspenso
  update public.profiles set suspended_until = now() + interval '1 day', ranked_matches = 1, ranked_rating = 1500 where id = s;
  update public.profiles set ranked_matches = 1, ranked_rating = 1400 where id = c;
  perform set_config('request.jwt.claims', json_build_object('sub', a, 'role', 'authenticated')::text, true);
  select count(*) into n from public.leaderboard_overall(100) where user_id = s;
  out := out || format('suspenso_no_ranking=%s ', n);
  select count(*) into n from public.leaderboard_overall(100) where user_id = c;
  out := out || format('normal_no_ranking=%s ', n);
  select count(*) into n from public.search_commander('Ensq-s');
  out := out || format('suspenso_na_busca=%s | ', n);
  perform set_config('request.jwt.claims', json_build_object('sub', s, 'role', 'authenticated')::text, true);
  set local role authenticated;
  update public.profiles set suspended_until = null where id = s;
  reset role;
  out := out || format('suspenso_cliente_grava=%s | ',
    case when (select suspended_until from public.profiles where id = s) is not null then 'mantido' else 'ZEROU' end);

  -- ------------------------------------------------------------ retenção de 12 meses (purge_moderation)
  insert into public.player_reports (reporter_id, reported_id, reason, context, created_at)
    values (a, t, 'abuse', 'match', now() - interval '13 months');
  insert into public.name_resets (user_id, old_name, reason, created_at) values (t, 'antigo', 'reports', now() - interval '13 months');
  select count(*) into n from public.player_reports where reported_id = t;   -- inclui as de hoje
  tot := n;
  select count(*) into f_old from public.purge_moderation();
  out := out || format('purge_apagou=%s/%s | ',
    (select count(*) from public.player_reports where reported_id = t and created_at < now() - interval '12 months'),
    (select count(*) from public.name_resets where user_id = t and created_at < now() - interval '12 months'));
  out := out || format('recente_fica=%s | ', (select count(*) from public.player_reports where reported_id = t) = tot - 1);

  -- ------------------------------------------------------------ privilégios
  fs := array['public.set_username(text)', 'public.block_player(uuid)', 'public.unblock_player(uuid)', 'public.my_blocks()',
    'public.report_player(uuid,text,text,uuid)', 'public.my_name_reset()', 'public.name_reset_ack()',
    'public.find_quick_offer(boolean,boolean)', 'public.join_online_match(uuid,text)', 'public.search_commander(text)'];
  v := ''; x := '';
  foreach f in array fs loop
    if has_function_privilege('anon', f, 'execute') then v := v || ' ' || f; end if;
    if not has_function_privilege('authenticated', f, 'execute') then x := x || ' ' || f; end if;
  end loop;
  out := out || format('anon_executa=%s auth_sem=%s | ', coalesce(nullif(v, ''), 'nenhuma'), coalesce(nullif(x, ''), 'nenhuma'));
  fs := array['public.reports_pending()', 'public.review_report(uuid,text,integer)', 'public.force_reset_name(uuid,text)',
    'public.name_is_offensive(text)', 'public.default_name(uuid)', 'public.sync_username_copies(uuid,text,boolean)', 'public.purge_moderation()'];
  v := '';
  foreach f in array fs loop
    if has_function_privilege('anon', f, 'execute') or has_function_privilege('authenticated', f, 'execute') then
      v := v || ' ' || f;
    end if;
  end loop;
  out := out || format('admin_exposta=%s | ', coalesce(nullif(v, ''), 'nenhuma'));
  out := out || format('view_leaderboard=%s | ', coalesce(to_regclass('public.leaderboard')::text, 'apagada'));
  out := out || format('tabelas_sem_acesso_cliente=%s | ',
    (not has_table_privilege('authenticated', 'public.player_reports', 'select')
     and not has_table_privilege('anon', 'public.name_terms', 'select')
     and not has_table_privilege('authenticated', 'public.player_blocks', 'insert'))::text);

  raise exception 'RESULTADO (rollback): % FALHAS:%', out, coalesce(nullif(bad, ''), ' nenhuma');
end $$;
