-- Ensaio que desfaz tudo (#96, 0.44.1): contas internas e visão das compras (supabase/internal_accounts.sql).
-- Rode no SQL Editor do projeto cwtslesnthbenxswdcbv ANTES de aplicar a migração `internal_accounts`:
-- cole o texto inteiro de internal_accounts.sql e este arquivo na MESMA execução. O raise no fim cancela
-- a transação inteira (DDL, view temporária, usuários, compras e a chave internal_accounts) e devolve o
-- resultado na mensagem de erro.
-- Usuários fictícios criados dentro do bloco (nenhuma conta real nem QA é tocada):
--   A: user_id na internal_accounts (setada dentro do bloco)   B: QA em allowed_players, is_test = false
--   C: jogador normal                                          D: criado depois, com o e-mail na lista
-- A view temporária ensaio_medicao é CÓPIA de docs/medicao-semanal.sql (sem os comentários): se a
-- medição mudar lá, copie de novo aqui. O ensaio compara a linha 'fechada' da medição antes e depois
-- dos fictícios; por isso perfis e compras deles são datados 1 minuto antes do fim da semana fechada
-- (cai na semana e nos últimos 7 dias, salvo no último minuto de domingo).
--
-- Resultado esperado (cada item vira um trecho de "RESULTADO"):
--   dry=1/after=1 gravou=nao              refresh_all_internal(false) conta só A e não grava
--   apply=1 A=t segunda=0                 (true) marca A; uma segunda passada não acha mais ninguém
--   D_nasce=t C=f                         conta nova da lista nasce interna; C segue normal
--   view A=t/4.90/dobroes_bolsa B=t/4.90/dobroes_bolsa C=f/4.90/dobroes_bolsa
--   view_sem_pii=ok                       a view não tem email, purchase_token nem order_id
--   anon_view=negado anon_fn=negado auth_view=negado auth_fn=negado
--   service_view=3                        service_role lê a view (as 3 compras dos fictícios)
--   trava A=t C=f                         authenticated não desmarca A nem marca C
--   jogo is_test_user(A)=f                a regra de jogo não muda
--   medicao sem_interna: perfis+1 novos+1 real+1 receita+4.90 externas+1 alerta=1(+1)   só C entra
--   medicao com_interna: perfis+4 real+3                                                A, B, C e D entram
-- Qualquer FALHOU no texto reprova o ensaio.

create temp view ensaio_medicao as
with
par as (
  select date_trunc('week', now() at time zone 'America/Sao_Paulo') as w1_local
),
semanas(semana, ord, ini, fim) as (
  select 'fechada', 1, (w1_local - interval '7 days') at time zone 'America/Sao_Paulo',
         w1_local at time zone 'America/Sao_Paulo' from par
  union all
  select 'anterior', 2, (w1_local - interval '14 days') at time zone 'America/Sao_Paulo',
         (w1_local - interval '7 days') at time zone 'America/Sao_Paulo' from par
),
base as (
  select p.id, p.created_at, p.last_seen_at,
         coalesce(p.is_test, false) as is_teste, public.is_internal_account(p.id) as is_interna
    from public.profiles p
   where not coalesce(p.is_test, false) or public.is_internal_account(p.id)
),
escopos(escopo, ord) as (values ('sem_interna', 1), ('com_interna', 2)),
b as (
  select e.escopo, base.*
    from escopos e join base on (e.escopo = 'com_interna' or (not base.is_interna and not base.is_teste))
),
compras as (
  select b.escopo, dp.kind, dp.created_at, coalesce(pr.price_cents, 0) / 100.0 as brl
    from public.dobroes_purchases dp
    join b on b.id = dp.user_id
    left join public.dobroes_products pr on pr.product_id = dp.product_id
),
externas as (
  select dp.created_at
    from public.dobroes_purchases dp
    join public.profiles p on p.id = dp.user_id
   where dp.kind = 'real' and not coalesce(p.is_test, false) and not public.is_internal_account(p.id)
),
flag as (
  select coalesce((select value from public.app_config where key = 'real_billing_enabled'), '(ausente)') as v
),
m as (
  select e.escopo, e.ord as eord, s.semana, s.ord as sord, s.ini, s.fim,
    (select count(*) from b where b.escopo = e.escopo and b.created_at < s.fim) as perfis_reais,
    case when s.ord = 1 then (select count(*) from b where b.escopo = e.escopo and b.last_seen_at >= now() - interval '1 day') end as dau_agora,
    case when s.ord = 1 then (select count(*) from b where b.escopo = e.escopo and b.last_seen_at >= now() - interval '7 days') end as wau_agora,
    case when s.ord = 1 then (select count(*) from b where b.escopo = e.escopo and b.last_seen_at >= now() - interval '30 days') end as mau_agora,
    (select count(*) from b where b.escopo = e.escopo and b.created_at >= s.ini and b.created_at < s.fim) as novos,
    case when s.ord = 1 then (select count(*) from b where b.escopo = e.escopo and b.created_at < now() - interval '1 day') end as coorte_d1,
    case when s.ord = 1 then (select count(*) from b where b.escopo = e.escopo and b.created_at < now() - interval '1 day'
                                and b.last_seen_at >= b.created_at + interval '1 day') end as retorno_d1,
    case when s.ord = 1 then (select count(*) from b where b.escopo = e.escopo and b.created_at < now() - interval '7 days') end as coorte_d7,
    case when s.ord = 1 then (select count(*) from b where b.escopo = e.escopo and b.created_at < now() - interval '7 days'
                                and b.last_seen_at >= b.created_at + interval '7 days') end as retorno_d7,
    (select count(*) from public.online_matches om
      where om.status = 'finished' and om.updated_at >= s.ini and om.updated_at < s.fim
        and (om.host_id in (select id from b where b.escopo = e.escopo)
             or om.guest_id in (select id from b where b.escopo = e.escopo))) as partidas_online_finalizadas,
    (select count(*) from public.online_matches om
      where om.status = 'finished' and om.ranked and om.updated_at >= s.ini and om.updated_at < s.fim
        and (om.host_id in (select id from b where b.escopo = e.escopo)
             or om.guest_id in (select id from b where b.escopo = e.escopo))) as partidas_ranqueadas,
    case when s.ord = 1 then (select count(distinct t.user_id) from public.push_tokens t
      where t.check_status is distinct from 'unregistered'
        and t.user_id in (select id from b where b.escopo = e.escopo)) end as push_ativos_agora,
    (select count(*) from compras c where c.escopo = e.escopo and c.kind = 'real'   and c.created_at >= s.ini and c.created_at < s.fim) as compras_real,
    (select count(*) from compras c where c.escopo = e.escopo and c.kind = 'premio' and c.created_at >= s.ini and c.created_at < s.fim) as compras_premio,
    (select count(*) from compras c where c.escopo = e.escopo and c.kind = 'test'   and c.created_at >= s.ini and c.created_at < s.fim) as compras_test,
    (select count(*) from compras c where c.escopo = e.escopo and c.kind = 'ad'     and c.created_at >= s.ini and c.created_at < s.fim) as compras_ad,
    (select count(*) from externas x where x.created_at >= s.ini and x.created_at < s.fim) as compras_real_externas,
    (select count(*) from externas x where x.created_at < s.fim) as compras_real_externas_acum,
    (select count(*) from public.ad_vales v where v.created_at >= s.ini and v.created_at < s.fim
        and v.user_id in (select id from b where b.escopo = e.escopo)) as vales_iniciados,
    (select count(*) from public.ad_vales v where v.status = 'applied' and v.created_at >= s.ini and v.created_at < s.fim
        and v.user_id in (select id from b where b.escopo = e.escopo)) as vales_aplicados,
    (select count(*) from public.ad_vales v where v.status = 'pending' and v.created_at >= s.ini and v.created_at < s.fim
        and v.user_id in (select id from b where b.escopo = e.escopo)) as vales_pendentes,
    (select count(distinct l.user_id) from public.dobroes_ledger l where l.delta < 0
        and l.created_at >= s.ini and l.created_at < s.fim
        and l.user_id in (select id from b where b.escopo = e.escopo)) as contas_gastaram_dobroes,
    (select coalesce(sum(c.brl), 0) from compras c where c.escopo = e.escopo and c.kind = 'real'
        and c.created_at >= s.ini and c.created_at < s.fim) as receita_bruta_brl,
    (select coalesce(sum(c.brl), 0) from compras c where c.escopo = e.escopo and c.kind = 'real'
        and c.created_at < s.fim) as receita_bruta_acum_brl
  from escopos e cross join semanas s
)
select escopo, semana,
  to_char(ini at time zone 'America/Sao_Paulo', 'YYYY-MM-DD') as semana_inicio,
  to_char((fim at time zone 'America/Sao_Paulo') - interval '1 day', 'YYYY-MM-DD') as semana_fim,
  perfis_reais, dau_agora, wau_agora, mau_agora, novos,
  coorte_d1, retorno_d1, coorte_d7, retorno_d7,
  partidas_online_finalizadas, partidas_ranqueadas, push_ativos_agora,
  compras_real, compras_premio, compras_test, compras_ad,
  compras_real_externas, compras_real_externas_acum,
  (select count(*) from externas x where x.created_at >= now() - interval '7 days') as alerta_real_nao_marcada,
  vales_iniciados, vales_aplicados, vales_pendentes, contas_gastaram_dobroes,
  receita_bruta_brl, round(receita_bruta_brl * 0.85, 2) as receita_liquida_brl,
  receita_bruta_acum_brl, round(receita_bruta_acum_brl * 0.85, 2) as receita_liquida_acum_brl,
  (select v from flag) as real_billing_enabled,
  case when escopo = 'sem_interna' and semana = 'fechada' then
    nullif(concat_ws(', ',
      case when wau_agora >= 60 then 'WAU>=60 (fase 2, #89; conferir 2 semanas seguidas)' end,
      case when dau_agora >= 30 then 'DAU>=30 (fase 3, #91; conferir 4 semanas)' end,
      case when compras_real_externas > 0 then 'COMPRA_REAL_EXTERNA (conta nao marcada, #96)' end,
      case when vales_iniciados > 0 and vales_aplicados * 100 < vales_iniciados * 80 then 'VALES<80%' end), '')
  end as alertas
from m
order by eord, sord;

do $$
declare
  a uuid := gen_random_uuid(); b uuid := gen_random_uuid(); c uuid := gen_random_uuid(); d uuid := gen_random_uuid();
  d_email text := 'ensaio-d-' || d || '@ensaio.invalid';
  out text := ''; x record; n integer; t boolean; t2 boolean; v text := '';
  s0 record; c0 record; s1 record; c1 record; quando timestamptz;
begin
  select * into s0 from ensaio_medicao where escopo = 'sem_interna' and semana = 'fechada';
  select * into c0 from ensaio_medicao where escopo = 'com_interna' and semana = 'fechada';
  quando := (date_trunc('week', now() at time zone 'America/Sao_Paulo') at time zone 'America/Sao_Paulo')
            - interval '1 minute';

  insert into auth.users (id, email)
    select u, 'ensaio-' || u || '@ensaio.invalid' from unnest(array[a, b, c]) u;
  update public.profiles set is_test = false where id in (a, b, c);
  insert into public.allowed_players (user_id, source, note) values (b, 'qa', 'ensaio #96');
  update public.app_config set value = a::text || ', ' || d_email where key = 'internal_accounts';

  ---------------------------------------------------------------- passada única
  select * into x from public.refresh_all_internal(false);
  select is_internal into t from public.profiles where id = a;
  out := out || format('dry=%s/after=%s gravou=%s | ', x.to_internal, x.internal_after, case when t then 'SIM-FALHOU' else 'nao' end);
  select * into x from public.refresh_all_internal(true);
  select is_internal into t from public.profiles where id = a;
  n := x.to_internal;
  select * into x from public.refresh_all_internal(true);
  out := out || format('apply=%s A=%s segunda=%s | ', n, t, x.to_internal);

  ---------------------------------------------------------------- conta nova da lista
  insert into auth.users (id, email) values (d, d_email);
  select is_internal into t from public.profiles where id = d;
  select is_internal into t2 from public.profiles where id = c;
  out := out || format('D_nasce=%s C=%s | ', t, t2);

  ---------------------------------------------------------------- view
  insert into public.dobroes_purchases (purchase_token, user_id, product_id, order_id, kind, credited)
    select 'ensaio-96-' || u, u, 'dobroes_bolsa', 'GPA.ensaio-' || u, 'real', 500 from unnest(array[a, b, c]) u;
  update public.dobroes_purchases set created_at = quando where purchase_token like 'ensaio-96-%';
  select string_agg(format('%s=%s/%s/%s', case o.user_id when a then 'A' when b then 'B' else 'C' end,
                           o.interna, o.valor_brl, o.produto), ' '
                    order by case o.user_id when a then 1 when b then 2 else 3 end)
    into v from public.dobroes_purchases_com_origem o where o.user_id in (a, b, c);
  out := out || 'view ' || coalesce(v, 'VAZIA-FALHOU') || ' | ';
  select count(*) into n from information_schema.columns
   where table_schema = 'public' and table_name = 'dobroes_purchases_com_origem'
     and column_name in ('email', 'purchase_token', 'order_id');
  out := out || 'view_sem_pii=' || case when n = 0 then 'ok' else 'FALHOU' end || ' | ';

  ---------------------------------------------------------------- permissões
  v := '';
  begin
    execute 'set local role anon';
    perform count(*) from public.dobroes_purchases_com_origem;
    v := v || 'anon_view=LEU-FALHOU ';
  exception when insufficient_privilege then v := v || 'anon_view=negado ';
  end;
  execute 'reset role';
  begin
    execute 'set local role anon';
    perform public.is_internal_account(a);
    v := v || 'anon_fn=EXECUTOU-FALHOU ';
  exception when insufficient_privilege then v := v || 'anon_fn=negado ';
  end;
  execute 'reset role';
  perform set_config('request.jwt.claims', json_build_object('sub', a, 'role', 'authenticated')::text, true);
  begin
    execute 'set local role authenticated';
    perform count(*) from public.dobroes_purchases_com_origem;
    v := v || 'auth_view=LEU-FALHOU ';
  exception when insufficient_privilege then v := v || 'auth_view=negado ';
  end;
  execute 'reset role';
  begin
    execute 'set local role authenticated';
    perform public.is_internal_account(a);
    v := v || 'auth_fn=EXECUTOU-FALHOU ';
  exception when insufficient_privilege then v := v || 'auth_fn=negado ';
  end;
  execute 'reset role';
  out := out || v || '| ';
  begin
    execute 'set local role service_role';
    select count(*) into n from public.dobroes_purchases_com_origem where user_id in (a, b, c);
    out := out || 'service_view=' || n || ' | ';
  exception when others then out := out || 'service_view=FALHOU(' || sqlstate || ') | ';
  end;
  execute 'reset role';

  ---------------------------------------------------------------- trava
  perform set_config('request.jwt.claims', json_build_object('sub', a, 'role', 'authenticated')::text, true);
  execute 'set local role authenticated';
  update public.profiles set is_internal = false where id = a;
  execute 'reset role';
  perform set_config('request.jwt.claims', json_build_object('sub', c, 'role', 'authenticated')::text, true);
  execute 'set local role authenticated';
  update public.profiles set is_internal = true where id = c;
  execute 'reset role';
  select is_internal into t from public.profiles where id = a;
  select is_internal into t2 from public.profiles where id = c;
  out := out || format('trava A=%s C=%s | ', t, t2);

  ---------------------------------------------------------------- jogo
  out := out || format('jogo is_test_user(A)=%s | ', public.is_test_user(a));

  ---------------------------------------------------------------- medição
  update public.profiles set created_at = quando where id in (a, b, c, d);
  select * into s1 from ensaio_medicao where escopo = 'sem_interna' and semana = 'fechada';
  select * into c1 from ensaio_medicao where escopo = 'com_interna' and semana = 'fechada';
  out := out || format('medicao sem_interna: perfis+%s novos+%s real+%s receita+%s externas+%s alerta=%s(+%s) | ',
    s1.perfis_reais - s0.perfis_reais, s1.novos - s0.novos, s1.compras_real - s0.compras_real,
    s1.receita_bruta_brl - s0.receita_bruta_brl, s1.compras_real_externas - s0.compras_real_externas,
    s1.alerta_real_nao_marcada, s1.alerta_real_nao_marcada - s0.alerta_real_nao_marcada);
  out := out || format('medicao com_interna: perfis+%s real+%s',
    c1.perfis_reais - c0.perfis_reais, c1.compras_real - c0.compras_real);

  raise exception 'RESULTADO (rollback): %', out;
end $$;
