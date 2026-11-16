-- Ensaio que desfaz tudo (#112, 0.45.0): recompensas do adversário virtual, revanche e versão mínima (supabase/bots.sql,
-- bloco "recompensas do adversário virtual" e `bot_rematch_start`; supabase/weekly.sql, `weekly_progress_of`).
-- Rode no SQL Editor do projeto cwtslesnthbenxswdcbv: cole o texto inteiro de bots.sql, o de weekly.sql e este bloco na
-- MESMA execução (o raise no fim cancela a transação inteira, DDL e dados inclusive). Contas fictícias criadas no bloco
-- (nenhuma conta real nem QA01/QA02 é tocada); o relógio é simulado recuando `started_at` e `ended_at`.
--
-- Resultado esperado:
--   versao_antiga=off                      app 0.44.6 não recebe bot
--   milha_gasta=ok 50->49                  a partida custa 1 milha (chave = id do bot)
--   sem_milha=miles                        sem milha o servidor não emite
--   pago_vitoria=ok N>0 saldo+N            vitória plausível (90 s, 18 acertos) paga como a humana
--   pago_de_novo=already                   idempotente (uma vez por partida)
--   milha_vitoria=11->... +1               casual paga 1 milha uma vez; repetir não paga de novo
--   vitoria_curta=not_eligible             vitória em 30 s: nada
--   vitoria_poucos_acertos=not_eligible    vitória com 10 acertos: nada
--   derrota_paga=ok N>0 / derrota_curta=not_eligible   derrota só paga com 30 s ou mais
--   aberta=not_eligible                    partida sem relato (aberta): nada
--   outra_conta=not_eligible               id de outro jogador: nada
--   sem_carteira=no_wallet
--   chave_off=capped                       bot_rewards_enabled='false': "limite do dia", sem pagar
--   teto_bot=ok parcial / depois=capped    teto de 250 dobrões do bot por dia
--   teto_unico=capped 0                    b já recebeu 40 do bot e 495 do online (535): nada sobra do teto de 500
--   teto_unico_online_depois_do_bot=ok 10   bot 250 + online 240 ja pagos: o online humano so recebe os 10 que faltam para 500
--   any_bot=ok any_humano=not_eligible     award_match_any escolhe o caminho pelo id
--   revanche_ok=ok mesma modalidade        bot_rematch_start da partida fechada (nova milha)
--   revanche_aberta=invalid revanche_outra_conta=invalid revanche_velha=invalid
--   grants=ok                              anon não executa nenhuma função nova
--   farm=ok                                view behavior_bot_farm lista a conta com 20 vitórias seguidas
create function pg_temp.nbr_as(u uuid) returns void language plpgsql as $$
begin
  perform set_config('request.jwt.claims', json_build_object('sub', u, 'role', 'authenticated')::text, true);
end $$;

-- partida contra bot já "jogada": sala cancelada + start + stats + relato fechado; devolve o id
create function pg_temp.nbr_play(u uuid, won boolean, hits integer, secs integer, rk boolean default false)
returns uuid language plpgsql as $$
declare rid uuid := gen_random_uuid(); s record; bid uuid;
begin
  perform pg_temp.nbr_as(u);
  insert into public.online_matches (id, host_id, host_name, status, is_quick_match, ranked, mode)
  values (rid, u, 'ens', 'waiting', true, rk, 'CLASSIC');
  perform public.cancel_quick_room(rid);
  select * into s from public.bot_match_start('CLASSIC', rid, 'Marujo', '0.45.0');
  bid := s.bot_id;
  if bid is null then return null; end if;
  perform public.bot_match_stats(bid, hits + 8, hits, case when hits >= 17 then 5 else 2 end, 25);
  update public.bot_matches set started_at = now() - make_interval(secs => secs) where id = bid;
  perform public.bot_match_report(bid, won);
  return bid;
end $$;

do $$
declare
  a uuid := gen_random_uuid();  -- jogador comum com carteira
  b uuid := gen_random_uuid();  -- outra conta (carteira)
  c uuid := gen_random_uuid();  -- sem carteira
  d uuid := gen_random_uuid();  -- conta de teste
  e uuid := gen_random_uuid();  -- sem milha
  f uuid := gen_random_uuid();  -- farm (20 vitórias)
  out text := ''; s record; bid uuid; bid2 uuid; n integer; bal0 integer; m0 integer; m1 integer; rid uuid; i integer;
  szn text;
begin
  select c0.season_key into szn from public.current_season() c0;
  insert into auth.users (id, email)
    select u, 'ensaio-' || u || '@ensaio.invalid' from unnest(array[a, b, c, d, e, f]) u;
  update public.profiles set is_test = false where id in (a, b, c, e, f);
  update public.profiles set is_test = true where id = d;
  update public.profiles set nautical_miles = 50, miles_day = public.brt_today() where id in (a, b, c, d, f);
  update public.profiles set nautical_miles = 0, miles_day = public.brt_today() where id = e;
  insert into public.dobroes_wallets (user_id, opening, source) select u, 0, 'admin' from unnest(array[a, b, d, e, f]) u;
  update public.app_config set value = 'true' where key = 'bots_enabled';
  update public.app_config set value = 'true' where key = 'bot_rewards_enabled';
  update public.app_config set value = '100' where key = 'bot_percent';
  -- bots_on_for exige conta elegível: a QA/teste segue a regra do bots_ensaio; aqui as contas comuns entram por 'true'
  update public.app_config set value = '1000' where key = 'bot_max_per_day';

  -- versão antiga
  perform pg_temp.nbr_as(a);
  rid := gen_random_uuid();
  insert into public.online_matches (id, host_id, host_name, status, is_quick_match, mode) values (rid, a, 'ens', 'waiting', true, 'CLASSIC');
  perform public.cancel_quick_room(rid);
  select * into s from public.bot_match_start('CLASSIC', rid, 'Marujo', '0.44.6');
  out := out || format('versao_antiga=%s | ', s.outcome);

  -- milha gasta
  select nautical_miles into m0 from public.profiles where id = a;
  bid := pg_temp.nbr_play(a, true, 18, 90);
  select nautical_miles into m1 from public.profiles where id = a;
  out := out || format('milha_gasta=%s %s->%s | ', case when bid is not null then 'ok' else 'FALHOU' end, m0, m1);

  -- sem milha
  perform pg_temp.nbr_as(e);
  rid := gen_random_uuid();
  insert into public.online_matches (id, host_id, host_name, status, is_quick_match, mode) values (rid, e, 'ens', 'waiting', true, 'CLASSIC');
  perform public.cancel_quick_room(rid);
  select * into s from public.bot_match_start('CLASSIC', rid, 'Marujo', '0.45.0');
  out := out || format('sem_milha=%s | ', s.outcome);

  -- pagamento da vitória plausível
  perform pg_temp.nbr_as(a);
  select p.credits into bal0 from public.profiles p where p.id = a;
  select * into s from public.award_match_any(bid, 0, 0, 0, 0, true);
  out := out || format('pago_vitoria=%s %s saldo+%s | ', s.status, s.amount, (select p.credits from public.profiles p where p.id = a) - bal0);
  select * into s from public.award_match_any(bid, 0, 0, 0, 0, true);
  out := out || format('pago_de_novo=%s | ', s.status);

  -- milha da vitória (casual +1), uma vez
  select nautical_miles into m0 from public.profiles where id = a;
  perform public.award_win_miles_any(bid);
  perform public.award_win_miles_any(bid);
  select nautical_miles into m1 from public.profiles where id = a;
  out := out || format('milha_vitoria=%s->%s | ', m0, m1);

  -- vitória curta e com poucos acertos
  bid2 := pg_temp.nbr_play(a, true, 18, 30);
  select * into s from public.award_match_any(bid2, 0, 0, 0, 0, true);
  out := out || format('vitoria_curta=%s | ', s.status);
  bid2 := pg_temp.nbr_play(a, true, 10, 120);
  select * into s from public.award_match_any(bid2, 0, 0, 0, 0, true);
  out := out || format('vitoria_poucos_acertos=%s | ', s.status);

  -- derrota
  bid2 := pg_temp.nbr_play(a, false, 8, 120);
  select * into s from public.award_match_any(bid2, 0, 0, 0, 0, false);
  out := out || format('derrota_paga=%s %s | ', s.status, s.amount);
  bid2 := pg_temp.nbr_play(a, false, 8, 10);
  select * into s from public.award_match_any(bid2, 0, 0, 0, 0, false);
  out := out || format('derrota_curta=%s | ', s.status);

  -- aberta (sem relato)
  rid := gen_random_uuid();
  insert into public.online_matches (id, host_id, host_name, status, is_quick_match, mode) values (rid, a, 'ens', 'waiting', true, 'CLASSIC');
  perform public.cancel_quick_room(rid);
  select * into s from public.bot_match_start('CLASSIC', rid, 'Marujo', '0.45.0');
  select * into s from public.award_match_any(s.bot_id, 0, 0, 0, 0, true);
  out := out || format('aberta=%s | ', s.status);

  -- outra conta tenta cobrar a partida de A
  perform pg_temp.nbr_as(b);
  select * into s from public.award_match_any(bid, 0, 0, 0, 0, true);
  out := out || format('outra_conta=%s | ', s.status);

  -- sem carteira, conta de teste
  bid2 := pg_temp.nbr_play(c, true, 18, 90);
  perform pg_temp.nbr_as(c);
  select * into s from public.award_match_any(bid2, 0, 0, 0, 0, true);
  out := out || format('sem_carteira=%s | ', s.status);

  -- chave de pagamento desligada
  update public.app_config set value = 'false' where key = 'bot_rewards_enabled';
  bid2 := pg_temp.nbr_play(b, true, 18, 90);
  perform pg_temp.nbr_as(b);
  select * into s from public.award_match_any(bid2, 0, 0, 0, 0, true);
  out := out || format('chave_off=%s | ', s.status);
  update public.app_config set value = 'true' where key = 'bot_rewards_enabled';

  -- teto do bot (250) e teto único (500)
  update public.app_config set value = '40' where key = 'dobroes_bot_daily_cap';
  select * into s from public.award_match_any(bid2, 0, 0, 0, 0, true);
  out := out || format('teto_bot=%s %s | ', s.status, s.amount);
  bid2 := pg_temp.nbr_play(b, true, 18, 90);
  select * into s from public.award_match_any(bid2, 0, 0, 0, 0, true);
  out := out || format('depois=%s | ', s.status);
  update public.app_config set value = '250' where key = 'dobroes_bot_daily_cap';
  -- 495 dobrões online já pagos hoje: só sobram 5 do teto de 500
  insert into public.dobroes_ledger (user_id, delta, reason, ref, balance_after) values (b, 495, 'online', gen_random_uuid()::text, 0);
  bid2 := pg_temp.nbr_play(b, true, 18, 90);
  select * into s from public.award_match_any(bid2, 0, 0, 0, 0, true);
  out := out || format('teto_unico=%s %s | ', s.status, s.amount);


  -- teto unico nos dois sentidos: bot 250 e depois o online humano so ate completar 500
  insert into public.dobroes_ledger (user_id, delta, reason, ref, balance_after) values (c, 250, 'bot', gen_random_uuid()::text, 0);
  insert into public.dobroes_wallets (user_id, opening, source) values (c, 0, 'admin') on conflict do nothing;
  perform pg_temp.nbr_as(c);
  rid := gen_random_uuid();
  insert into public.online_matches (id, host_id, guest_id, host_name, guest_name, status, is_quick_match, mode)
  values (rid, c, b, 'ens', 'ens2', 'active', true, 'CLASSIC');
  insert into public.online_messages (match_id, sender_id, body) values (rid, c, 'ACT|1|1'), (rid, b, 'ACT|2|2');
  insert into public.dobroes_ledger (user_id, delta, reason, ref, balance_after) values (c, 240, 'online', gen_random_uuid()::text, 0);
  select * into s from public.award_online_match(rid, 40, 30, 5, 20, true);
  out := out || format('teto_unico_online_depois_do_bot=%s %s (restavam 10) | ', s.status, s.amount);

  -- award_match_any: id que não é de bot cai no caminho humano (sem sala = not_eligible)
  select * into s from public.award_match_any(gen_random_uuid(), 5, 5, 1, 10, true);
  out := out || format('any_humano=%s | ', s.status);

  -- revanche
  perform pg_temp.nbr_as(a);
  select * into s from public.bot_rematch_start(bid, 'Marujo', '0.45.0');
  out := out || format('revanche_ok=%s ranked=%s | ', s.outcome, s.ranked);
  select * into s from public.bot_rematch_start(s.bot_id, 'Marujo', '0.45.0');
  out := out || format('revanche_aberta=%s | ', s.outcome);
  perform pg_temp.nbr_as(b);
  select * into s from public.bot_rematch_start(bid, 'Marujo', '0.45.0');
  out := out || format('revanche_outra_conta=%s | ', s.outcome);
  perform pg_temp.nbr_as(a);
  update public.bot_matches set ended_at = now() - interval '20 minutes' where id = bid;
  select * into s from public.bot_rematch_start(bid, 'Marujo', '0.45.0');
  out := out || format('revanche_velha=%s | ', s.outcome);

  -- grants
  out := out || format('grants=%s | ',
    case when not has_function_privilege('anon', 'public.award_bot_match(uuid)', 'execute')
          and not has_function_privilege('anon', 'public.award_match_any(uuid,integer,integer,integer,integer,boolean)', 'execute')
          and not has_function_privilege('anon', 'public.award_win_miles_any(uuid)', 'execute')
          and not has_function_privilege('anon', 'public.bot_rematch_start(uuid,text,text)', 'execute')
          and not has_function_privilege('anon', 'public.bot_match_stats(uuid,integer,integer,integer,integer)', 'execute')
          and has_function_privilege('authenticated', 'public.award_match_any(uuid,integer,integer,integer,integer,boolean)', 'execute')
         then 'ok' else 'FALHOU' end);

  -- farm: 20 vitórias seguidas de 80 s
  for i in 1..20 loop
    perform pg_temp.nbr_play(f, true, 18, 80);
  end loop;
  select count(*)::integer into n from public.behavior_bot_farm where user_id = f;
  out := out || format('farm=%s', case when n = 1 then 'ok' else 'FALHOU' end);

  raise exception 'RESULTADO (rollback): %', out;
end $$;
