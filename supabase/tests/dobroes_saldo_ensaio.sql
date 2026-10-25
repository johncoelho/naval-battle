-- Ensaio que desfaz tudo (#34, 0.34.9): saldo de dobrões no servidor (supabase/dobroes_saldo.sql).
-- Rodar no SQL Editor do projeto cwtslesnthbenxswdcbv. Para ensaiar ANTES de aplicar a migração, cole
-- o texto da migração (dobroes_saldo.sql + as funções alteradas) e este bloco na mesma execução: o
-- raise no fim cancela a transação inteira, DDL inclusive, e nada fica gravado. Depois de aplicada, rode
-- só este bloco. Usa as contas QA01 e QA02, nunca jogador real (a abertura em massa roda DENTRO do
-- bloco e volta no rollback; serve também para provar o invariante em todas as contas).
--
-- Resultado esperado (cada item vira um trecho de "RESULTADO"; os números dependem só deste bloco):
--   seeds=15/4/4                         pinturas, cascos e cartuchos em store_items            (DOB-30)
--   sem_carteira_credits=9999            conta legado: o cliente ainda grava credits            (DOB-32)
--   sem_carteira_online=no_wallet        award_online_match recusa conta sem carteira           (DOB-32)
--   legado_ad_ia=ok                      anúncio de vitória contra a IA segue no legado         (DOB-32)
--   abre_a=1500/t                        servidor 1000 > aparelho 800, +500 pendente: 1500      (DOB-30)
--   abre_a_entregue=t                    pendente que o aparelho já somou só ganha entrega      (DOB-30)
--   abre_a_de_novo=1500/f                chamar de novo não soma nem reabre                     (DOB-30)
--   abre_b_local=5000/t                  aparelho 5000 > servidor 300: vale o maior             (DOB-30)
--   abre_b_clamp=200000/t                aparelho 999999: limitado a dobroes_open_max           (DOB-30)
--   cliente_credits=1500                 cliente grava credits/owned com carteira: ignorado     (DOB-31)
--   cliente_owned=std,br                                                                        (DOB-31)
--   cliente_xp=77                        o resto da carreira segue sendo gravado (200)          (DOB-31)
--   upsert_credits=1500 upsert_xp=88     upsert do app antigo: credits ignorado, xp aceito      (DOB-31)
--   compra_oferta=ok                     pintura do dia com 30% de desconto                     (DOB-33)
--   compra_oferta_preco=<preço*0,7>      saldo cai o preço da oferta (hoje: spl, 770)           (DOB-33)
--   compra_repetida=owned/sem cobrar     item que a conta já tem                                (DOB-33)
--   casco_sem_saldo=insufficient/igual   nada muda                                              (DOB-34)
--   cartucho_2x=ok/-200                  duas compras de 100                                    (DOB-33)
--   cartucho_pedido=-100 repetido=ok/sem cobrar ledger 1   mesmo p_request reenviado: cobra uma vez   (DOB-52)
--   feedback_legado=1 linha/reward 150/saldo igual/2o 0    sem carteira o servidor não credita        (DOB-53)
--   feedback_carteira=1 linha/+300/ledger 1/2o 0 saldo igual   com carteira credita uma vez          (DOB-53)
--   tabelas=false/false/true/true/false  cliente sem TRUNCATE no razão; wallets só authenticated; store_items leitura (DOB-43)
--   item_inexistente=not_found           inclui pintura de temporada e item incluso             (DOB-33)
--   passe_sem_saldo=dobroes_insufficient/sem passe/saldo igual                                  (DOB-34)
--   passe_com_saldo=<saldo+500-1200>     debita, credita o bônus e põe a pintura da estação     (DOB-35)
--   milhas_sem_saldo=dobroes_insufficient/milhas iguais                                         (DOB-34)
--   milhas_com_saldo=-100                um pacote custa miles_pack_price                       (DOB-36)
--   checkin=+25/ledger 1                 duas chamadas, um crédito                              (DOB-37)
--   desafio=+50/ledger 1                                                                        (DOB-37)
--   compra_real=credited/+500            apply_dobroes_purchase credita uma vez                 (DOB-38)
--   compra_real_2=already/igual                                                                 (DOB-38)
--   pendente_aviso=1                     a compra segue em dobroes_pending (aviso do app)       (DOB-38)
--   anuncio=applied/+25                  apply_ad_reward credita uma vez                        (DOB-39)
--   anuncio_2=already/igual                                                                     (DOB-39)
--   online_sem_combate=not_eligible      o adversário não deu tiro                              (DOB-40)
--   online_1=ok/172                      casual, vitória: (220+66*2+3*15+120)/3                 (DOB-40)
--   online_repetido=already/172          mesma sala de novo                                     (DOB-40)
--   online_ranqueada_sem_resultado=no_result                                                    (DOB-40)
--   online_ranqueada_derrota=ok/53       winner_id do servidor manda, não o relato (80+66+15)/3 (DOB-40)
--   online_teto_zerado=capped/0          teto diário já gasto: nada paga                        (DOB-40)
--   online_teto=ok/100 e limit/0         teto parcial paga o que resta; a 4ª contra o mesmo é limit (DOB-40)
--   online_limite=limit                  4ª recompensa contra o mesmo adversário no dia         (DOB-40)
--   ia_ad_carteira=not_eligible          vitória contra a IA não dobra com carteira             (DOB-41)
--   ad_online=ok/150                     o valor sai do razão (172), cortado no teto ads_win_double_cap (DOB-41)
--   open_all=<n> soma X->X sem_carteira_depois=0  abertura em massa, soma conferida (b_admin=800)  (DOB-42)
--   invariante=0                         soma do razão = credits em toda carteira               (DOB-43)
--   privilegios=f/f/f                    cliente não executa dobroes_apply/open_all/open_core   (DOB-43)

do $$
declare
  a uuid; b uuid; r record; n int; t text; out text := '';
  bal int; bal2 int; offer text; oprice int; m1 uuid; m2 uuid; m3 uuid; m4 uuid; m5 uuid; fb1 uuid;
  vale uuid; tk text; mi int; seas text; soma_antes bigint; soma_depois bigint; mission record;
begin
  select id into a from auth.users where email = 'aigamesfactory.qa01@gmail.com';
  select id into b from auth.users where email = 'aigamesfactory.qa02@gmail.com';

  -- cenário limpo para as contas QA (tudo volta no rollback)
  delete from public.dobroes_ledger where user_id in (a, b);
  delete from public.dobroes_wallets where user_id in (a, b);
  delete from public.dobroes_purchases where user_id in (a, b);
  delete from public.ad_vales where user_id in (a, b);
  delete from public.daily_claims where user_id in (a, b);
  delete from public.daily_progress where user_id in (a, b);
  delete from public.season_passes where user_id in (a, b);
  delete from public.mile_ledger where user_id in (a, b);
  update public.profiles set credits = 1000, owned = 'std,br', fleets = 'std', xp = 0, nautical_miles = 10,
         miles_day = public.brt_today() where id = a;
  update public.profiles set credits = 300, owned = 'std,br', fleets = 'std', xp = 0, nautical_miles = 10,
         miles_day = public.brt_today() where id = b;
  insert into public.app_config (key, value) values
    ('real_billing_enabled', 'false'), ('ads_enabled', 'false'), ('dobroes_online_daily_cap', '500')
    on conflict (key) do update set value = excluded.value;

  out := out || 'seeds=' || (select count(*) from public.store_items where kind = 'paint')
       || '/' || (select count(*) from public.store_items where kind = 'fleet')
       || '/' || (select count(*) from public.store_items where kind = 'charge') || ' | ';

  ---------------------------------------------------------------- conta sem carteira (app antigo / legado)
  perform set_config('request.jwt.claims', json_build_object('sub', a, 'role', 'authenticated')::text, true);
  execute 'set local role authenticated';
  update public.profiles set credits = 9999 where id = a;
  execute 'reset role';
  select credits into n from public.profiles where id = a;
  out := out || 'sem_carteira_credits=' || n || ' | ';
  update public.profiles set credits = 1000 where id = a;

  -- sala de ensaio entre a e b (casual, com tiro dos dois lados)
  insert into public.online_matches (host_id, guest_id, host_name, guest_name, status)
    values (a, b, 'qa01', 'qa02', 'finished') returning id into m1;
  insert into public.online_messages (match_id, sender_id, body) values (m1, a, 'ACT|1,1'), (m1, b, 'ACT|2,2');
  perform set_config('request.jwt.claims', json_build_object('sub', b, 'role', 'authenticated')::text, true);
  select q.status into t from public.award_online_match(m1, 30, 20, 3, 25, true) q;
  out := out || 'sem_carteira_online=' || t || ' | ';

  -- anúncio de vitória contra a IA no legado: igual a antes
  update public.app_config set value = 'true' where key = 'ads_enabled';
  select q.status into t from public.ad_reward_start('win', 'ai:abcdefgh-1234', 50) q;
  out := out || 'legado_ad_ia=' || t || ' | ';
  delete from public.ad_vales where user_id in (a, b);
  update public.app_config set value = 'false' where key = 'ads_enabled';

  -- claim_feedback sem carteira (b): devolve a linha com a recompensa, mas o servidor não credita; 2º resgate vazio
  insert into public.feedback (user_id, username, kind, message, status, reward_credits, reviewed_at)
    values (b, 'qa02', 'bug', 'mensagem de ensaio com mais de vinte caracteres', 'approved', 150, now())
    returning id into fb1;
  select credits into bal from public.profiles where id = b;
  select count(*), coalesce(max(f.reward_credits), 0) into n, bal2 from public.claim_feedback(fb1) f;
  out := out || 'feedback_legado=' || n || ' linha/reward ' || bal2 || '/saldo '
       || case when (select credits from public.profiles where id = b) = bal then 'igual' else 'MUDOU' end
       || '/2o ' || (select count(*) from public.claim_feedback(fb1)) || ' | ';

  ---------------------------------------------------------------- abertura (conta a: servidor maior + pendentes)
  insert into public.dobroes_purchases (purchase_token, user_id, product_id, kind, credited, premio, delivered_at) values
    ('ensaio-p1', a, 'dobroes_bolsa', 'test', 500, false, null),            -- pendente, aparelho NAO somou
    ('ensaio-p2', a, 'dobroes_bau', 'test', 300, false, null),              -- pendente, aparelho JA somou (ack perdido)
    ('ensaio-p3', a, 'dobroes_bau', 'test', 100, false, now());             -- ja entregue: nao entra
  perform set_config('request.jwt.claims', json_build_object('sub', a, 'role', 'authenticated')::text, true);
  select o.balance, o.opened_now into r from public.dobroes_open(800, array['ensaio-p2']) o;
  out := out || 'abre_a=' || r.balance || '/' || case when r.opened_now then 't' else 'f' end || ' | ';
  out := out || 'abre_a_entregue=' || (select (delivered_at is not null)::text from public.dobroes_purchases where purchase_token = 'ensaio-p2')
       || ' | ';
  select o.balance, o.opened_now into r from public.dobroes_open(99999, '{}') o;
  out := out || 'abre_a_de_novo=' || r.balance || '/' || case when r.opened_now then 't' else 'f' end || ' | ';

  -- conta b: aparelho maior que o servidor; depois o teto
  perform set_config('request.jwt.claims', json_build_object('sub', b, 'role', 'authenticated')::text, true);
  select o.balance, o.opened_now into r from public.dobroes_open(5000, '{}') o;
  out := out || 'abre_b_local=' || r.balance || '/' || case when r.opened_now then 't' else 'f' end || ' | ';
  delete from public.dobroes_ledger where user_id = b;
  delete from public.dobroes_wallets where user_id = b;
  update public.profiles set credits = 300 where id = b;
  select o.balance, o.opened_now into r from public.dobroes_open(999999, '{}') o;
  out := out || 'abre_b_clamp=' || r.balance || '/' || case when r.opened_now then 't' else 'f' end || ' | ';

  ---------------------------------------------------------------- cliente nao grava saldo nem itens
  perform set_config('request.jwt.claims', json_build_object('sub', a, 'role', 'authenticated')::text, true);
  execute 'set local role authenticated';
  update public.profiles set credits = 999999, owned = 'std,br,jp,us', fleets = 'std,gho', xp = 77 where id = a;
  execute 'reset role';
  select credits, owned, xp into r from public.profiles where id = a;
  out := out || 'cliente_credits=' || r.credits || ' | cliente_owned=' || r.owned || ' | cliente_xp=' || r.xp || ' | ';
  execute 'set local role authenticated';
  insert into public.profiles (id, username, credits, owned, xp)
    values (a, (select username from public.profiles where id = a), 7777, 'std,br,slt', 88)
    on conflict (id) do update set credits = excluded.credits, owned = excluded.owned, xp = excluded.xp;
  execute 'reset role';
  select credits, xp into r from public.profiles where id = a;
  out := out || 'upsert_credits=' || r.credits || ' upsert_xp=' || r.xp || ' | ';

  ---------------------------------------------------------------- loja: compra, repetida, sem saldo, oferta
  select owned into t from public.profiles where id = a;
  offer := public.store_offer_today(t);
  select price into oprice from public.store_items where kind = 'paint' and item_id = offer;
  select credits into bal from public.profiles where id = a;
  select x.status, x.balance into r from public.dobroes_buy('paint', offer) x;
  out := out || 'compra_oferta=' || r.status || ' (' || offer || ') | compra_oferta_preco=' || (bal - r.balance)
       || '/esperado ' || (oprice * 70 / 100) || ' | ';
  bal := r.balance;
  select x.status, x.balance into r from public.dobroes_buy('paint', offer) x;
  out := out || 'compra_repetida=' || r.status || '/' || case when r.balance = bal then 'sem cobrar' else 'COBROU' end || ' | ';
  select x.status, x.balance into r from public.dobroes_buy('fleet', 'gho') x;      -- 1800 > saldo
  out := out || 'casco_sem_saldo=' || r.status || '/' || case when r.balance = bal then 'igual' else 'MUDOU' end || ' | ';
  select x.balance into bal2 from public.dobroes_buy('charge', '2X') x;
  select x.balance into bal2 from public.dobroes_buy('charge', '2X') x;
  out := out || 'cartucho_2x=ok/' || (bal2 - bal) || ' | ';
  bal := bal2;
  select x.balance into bal2 from public.dobroes_buy('charge', '2X', 'req-ensaio-1') x;
  out := out || 'cartucho_pedido=' || (bal2 - bal);
  bal := bal2;
  select x.status, x.balance into r from public.dobroes_buy('charge', '2X', 'req-ensaio-1') x;
  out := out || ' repetido=' || r.status || '/' || case when r.balance = bal then 'sem cobrar' else 'COBROU' end
       || ' ledger ' || (select count(*) from public.dobroes_ledger where user_id = a and reason = 'store_charge' and ref = 'req-ensaio-1') || ' | ';
  out := out || 'item_inexistente=' || (select x.status from public.dobroes_buy('paint', 'zzz') x)
       || '/' || (select x.status from public.dobroes_buy('paint', 's_ver') x)
       || '/' || (select x.status from public.dobroes_buy('fleet', 'std') x) || ' | ';

  ---------------------------------------------------------------- passe de temporada
  select c.season_key into seas from public.current_season() c;
  select credits into bal from public.profiles where id = a;      -- abaixo de 1200
  begin
    perform * from public.join_season('premium');
    out := out || 'passe_sem_saldo=SEM ERRO | ';
  exception when others then
    select count(*) into n from public.season_passes where user_id = a and season_key = seas;
    out := out || 'passe_sem_saldo=' || sqlerrm || '/passes ' || n || '/'
         || case when (select credits from public.profiles where id = a) = bal then 'saldo igual' else 'SALDO MUDOU' end || ' | ';
  end;
  perform public.dobroes_apply(a, 2000, 'ensaio', 'recarga-passe');
  select credits into bal from public.profiles where id = a;
  select j.tier into t from public.join_season('premium') j;
  select credits, owned into r from public.profiles where id = a;
  out := out || 'passe_com_saldo=' || t || ' saldo ' || r.credits || '/esperado ' || (bal - 1200 + 500)
       || ' pintura ' || (public.season_paint_id(seas) = any (string_to_array(r.owned, ','))) || ' | ';

  ---------------------------------------------------------------- milhas com dobroes
  select credits into bal from public.profiles where id = a;
  perform public.dobroes_apply(a, -bal, 'ensaio', 'zerar');
  select nautical_miles into mi from public.profiles where id = a;
  begin
    perform * from public.buy_miles(1);
    out := out || 'milhas_sem_saldo=SEM ERRO | ';
  exception when others then
    out := out || 'milhas_sem_saldo=' || sqlerrm || '/milhas '
         || case when (select nautical_miles from public.profiles where id = a) = mi then 'iguais' else 'MUDARAM' end || ' | ';
  end;
  perform public.dobroes_apply(a, 1000, 'ensaio', 'recarga-milhas');
  perform * from public.buy_miles(1);
  out := out || 'milhas_com_saldo=' || ((select credits from public.profiles where id = a) - 1000) || ' | ';

  ---------------------------------------------------------------- diario
  select credits into bal from public.profiles where id = a;
  perform * from public.daily_checkin();
  perform * from public.daily_checkin();
  out := out || 'checkin=+' || ((select credits from public.profiles where id = a) - bal) || '/ledger '
       || (select count(*) from public.dobroes_ledger where user_id = a and reason = 'checkin') || ' | ';
  select credits into bal from public.profiles where id = a;
  select * into mission from public.daily_mission(public.brt_today());
  perform * from public.claim_daily_challenge(mission.code, mission.target);
  perform * from public.claim_daily_challenge(mission.code, mission.target);
  out := out || 'desafio=+' || ((select credits from public.profiles where id = a) - bal) || '/ledger '
       || (select count(*) from public.dobroes_ledger where user_id = a and reason = 'challenge') || ' | ';

  ---------------------------------------------------------------- compra real (Play) e anuncio
  update public.app_config set value = 'true' where key = 'real_billing_enabled';
  select credits into bal from public.profiles where id = a;
  tk := 'ensaio-real-' || replace(gen_random_uuid()::text, '-', '');
  select p.status into t from public.apply_dobroes_purchase(a, 'dobroes_bolsa', tk, 'o-' || tk, null, 0, true, 'vitrine') p;
  out := out || 'compra_real=' || t || '/+' || ((select credits from public.profiles where id = a) - bal) || ' | ';
  select credits into bal from public.profiles where id = a;
  select p.status into t from public.apply_dobroes_purchase(a, 'dobroes_bolsa', tk, 'o-' || tk, null, 0, true, 'vitrine') p;
  out := out || 'compra_real_2=' || t || '/' || case when (select credits from public.profiles where id = a) = bal then 'igual' else 'MUDOU' end || ' | ';
  perform set_config('request.jwt.claims', json_build_object('sub', a, 'role', 'authenticated')::text, true);
  out := out || 'pendente_aviso=' || (select count(*) from public.dobroes_pending() p where p.purchase_token = tk) || ' | ';

  update public.app_config set value = 'true' where key = 'ads_enabled';
  insert into public.ad_vales (user_id, place, ref, amount) values (a, 'checkin', public.brt_today()::text, 25)
    returning id into vale;
  select credits into bal from public.profiles where id = a;
  select x.status into t from public.apply_ad_reward(vale, 'ensaio-tx-1', a::text) x;
  out := out || 'anuncio=' || t || '/+' || ((select credits from public.profiles where id = a) - bal) || ' | ';
  select credits into bal from public.profiles where id = a;
  select x.status into t from public.apply_ad_reward(vale, 'ensaio-tx-1', a::text) x;
  out := out || 'anuncio_2=' || t || '/' || case when (select credits from public.profiles where id = a) = bal then 'igual' else 'MUDOU' end || ' | ';
  delete from public.ad_vales where user_id = a;

  ---------------------------------------------------------------- partida online
  -- m1 (a x b) casual: so o b deu tiro? nao: os dois deram. Cria m2 sem combate do adversario.
  insert into public.online_matches (host_id, guest_id, host_name, guest_name, status)
    values (a, b, 'qa01', 'qa02', 'finished') returning id into m2;
  insert into public.online_messages (match_id, sender_id, body) values (m2, a, 'ACT|1,1');
  perform set_config('request.jwt.claims', json_build_object('sub', a, 'role', 'authenticated')::text, true);
  select q.status into t from public.award_online_match(m2, 30, 20, 3, 25, true) q;
  out := out || 'online_sem_combate=' || t || ' | ';
  select q.status, q.amount into r from public.award_online_match(m1, 30, 20, 3, 25, true) q;
  out := out || 'online_1=' || r.status || '/' || r.amount || ' | ';
  select q.status, q.amount into r from public.award_online_match(m1, 30, 20, 3, 25, true) q;
  out := out || 'online_repetido=' || r.status || '/' || r.amount || ' | ';

  -- ranqueada: sem winner_id ainda, depois o servidor diz que o b venceu (relato do app diz que a venceu)
  insert into public.online_matches (host_id, guest_id, host_name, guest_name, status, ranked)
    values (a, b, 'qa01', 'qa02', 'finished', true) returning id into m3;
  insert into public.online_messages (match_id, sender_id, body) values (m3, a, 'ACT|1,1'), (m3, b, 'ABIL|SNR');
  select q.status into t from public.award_online_match(m3, 30, 10, 1, 25, true) q;
  out := out || 'online_ranqueada_sem_resultado=' || t || ' | ';
  update public.online_matches set winner_id = b where id = m3;
  select q.status, q.amount into r from public.award_online_match(m3, 30, 10, 1, 25, true) q;
  out := out || 'online_ranqueada_derrota=' || r.status || '/' || r.amount || ' (esperado ' || ((80 + 33 * 2 + 15) / 3) || ') | ';

  -- teto diario parcial: a ja levou 172 + a derrota; teto 300 deixa o resto parcial e depois zera
  select coalesce(sum(delta), 0) into n from public.dobroes_ledger where user_id = a and reason = 'online';
  insert into public.online_matches (host_id, guest_id, host_name, guest_name, status)
    values (a, b, 'qa01', 'qa02', 'finished') returning id into m4;
  insert into public.online_messages (match_id, sender_id, body) values (m4, a, 'ACT|1,1'), (m4, b, 'ACT|2,2');
  update public.app_config set value = n::text where key = 'dobroes_online_daily_cap';
  select q.status, q.amount into r from public.award_online_match(m4, 30, 20, 3, 25, true) q;
  out := out || 'online_teto_zerado=' || r.status || '/' || r.amount || ' | ';
  update public.app_config set value = (n + 100)::text where key = 'dobroes_online_daily_cap';
  select q.status, q.amount into r from public.award_online_match(m4, 30, 20, 3, 25, true) q;
  out := out || 'online_teto=' || r.status || '/' || r.amount || ' (parcial, teto restante 100) e ';
  insert into public.online_matches (host_id, guest_id, host_name, guest_name, status)
    values (a, b, 'qa01', 'qa02', 'finished') returning id into m5;
  insert into public.online_messages (match_id, sender_id, body) values (m5, a, 'ACT|1,1'), (m5, b, 'ACT|2,2');
  select q.status, q.amount into r from public.award_online_match(m5, 30, 20, 3, 25, true) q;
  out := out || r.status || '/' || r.amount || ' | ';
  update public.app_config set value = '500' where key = 'dobroes_online_daily_cap';
  -- a ja tem 3 recompensas hoje contra o b (m1, m3, m4): a proxima e o limite por adversario
  select q.status into t from public.award_online_match(m5, 30, 20, 3, 25, true) q;
  out := out || 'online_limite=' || t || ' | ';

  ---------------------------------------------------------------- anuncio de vitoria com carteira
  -- claim_feedback com carteira (a): +300 uma vez, uma linha no razão, 2º resgate vazio
  insert into public.feedback (user_id, username, kind, message, status, reward_credits, reviewed_at)
    values (a, 'qa01', 'bug', 'mensagem de ensaio com mais de vinte caracteres', 'approved', 300, now())
    returning id into fb1;
  select credits into bal from public.profiles where id = a;
  select count(*) into n from public.claim_feedback(fb1);
  out := out || 'feedback_carteira=' || n || ' linha/+' || ((select credits from public.profiles where id = a) - bal)
       || '/ledger ' || (select count(*) from public.dobroes_ledger where user_id = a and reason = 'feedback' and ref = fb1::text);
  select credits into bal from public.profiles where id = a;
  out := out || '/2o ' || (select count(*) from public.claim_feedback(fb1)) || ' saldo '
       || case when (select credits from public.profiles where id = a) = bal then 'igual' else 'MUDOU' end || ' | ';

  update public.app_config set value = 'true' where key = 'ads_enabled';
  select q.status into t from public.ad_reward_start('win', 'ai:abcdefgh-1234', 50) q;
  out := out || 'ia_ad_carteira=' || t || ' | ';
  select q.status, q.amount into r from public.ad_reward_start('win', 'online:' || m1::text, 9999) q;
  out := out || 'ad_online=' || r.status || '/' || r.amount || ' (credito da sala 172, p_amount 9999 ignorado) | ';
  delete from public.ad_vales where user_id in (a, b);
  update public.app_config set value = 'false' where key = 'ads_enabled';

  ---------------------------------------------------------------- abertura em massa e invariante
  -- b volta ao legado; abre_all abre b e toda conta real sem carteira (rollback no fim)
  delete from public.dobroes_ledger where user_id = b;
  delete from public.dobroes_wallets where user_id = b;
  update public.profiles set credits = 300 where id = b;
  insert into public.dobroes_purchases (purchase_token, user_id, product_id, kind, credited) values
    ('ensaio-p4', b, 'dobroes_bolsa', 'test', 500);
  select coalesce(sum(p.credits + coalesce((select sum(d.credited) from public.dobroes_purchases d
                    where d.user_id = p.id and d.delivered_at is null and d.credited > 0), 0)), 0)
    into soma_antes
    from public.profiles p where not exists (select 1 from public.dobroes_wallets w where w.user_id = p.id);
  n := public.dobroes_open_all();
  select coalesce(sum(p.credits), 0) into soma_depois from public.profiles p
    where p.id in (select w.user_id from public.dobroes_wallets w where w.opened_at >= now() - interval '1 hour' and w.source = 'admin');
  out := out || 'open_all=' || n || ' soma ' || soma_antes || '->' || soma_depois
       || ' sem_carteira_depois=' || (select count(*) from public.profiles p
            where not exists (select 1 from public.dobroes_wallets w where w.user_id = p.id)) || ' | ';
  out := out || 'b_admin=' || (select credits from public.profiles where id = b) || ' (esperado 800) | ';

  out := out || 'invariante=' || (select count(*) from public.dobroes_wallets w join public.profiles p on p.id = w.user_id
            where p.credits <> (select coalesce(sum(l.delta), 0) from public.dobroes_ledger l where l.user_id = w.user_id)) || ' | ';
  out := out || 'tabelas=' || has_table_privilege('authenticated', 'public.dobroes_ledger', 'truncate')
       || '/' || has_table_privilege('anon', 'public.dobroes_wallets', 'select')
       || '/' || has_table_privilege('authenticated', 'public.dobroes_wallets', 'select')
       || '/' || has_table_privilege('anon', 'public.store_items', 'select')
       || '/' || has_table_privilege('authenticated', 'public.store_items', 'insert') || ' | ';
  out := out || 'privilegios=' || has_function_privilege('authenticated', 'public.dobroes_apply(uuid,integer,text,text)', 'execute')
       || '/' || has_function_privilege('authenticated', 'public.dobroes_open_all()', 'execute')
       || '/' || has_function_privilege('authenticated', 'public.dobroes_open_core(uuid,integer,text[],text)', 'execute');

  raise exception 'RESULTADO (rollback): %', out;
end $$;
