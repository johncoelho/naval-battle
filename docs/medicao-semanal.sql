-- Medição semanal do público e da receita (issue #94, plano #86; contas internas pela #96).
-- SÓ LEITURA: nenhum insert, update, delete nem chamada que grave. Não altera código, banco nem flags.
--
-- Como rodar neste checkout: `supabase db query --linked` só funciona com a variável
--   SUPABASE_PROJECT_ID=cwtslesnthbenxswdcbv supabase db query --linked -o csv -f docs/medicao-semanal.sql
-- (sem ela o comando não acha o projeto). Detalhes, rotina e aproximações: docs/MEDICAO_SEMANAL.md.
-- Alternativa (armadilha do project-ref): `supabase db query --linked` lê o projeto de
-- supabase/.temp/project-ref do diretório atual, e o repositório não guarda esse arquivo. Rode
-- numa pasta temporária, fora do repositório:
--   W=$(mktemp -d); mkdir -p "$W/supabase/.temp"
--   printf 'cwtslesnthbenxswdcbv' > "$W/supabase/.temp/project-ref"
--   (cd "$W" && supabase db query --linked -o table -f "<repo>/docs/medicao-semanal.sql")
--
-- Saída: 4 linhas = escopo x semana, só agregados (nunca user_id, apelido ou e-mail).
--   escopo 'sem_interna' = jogadores reais de fora; 'com_interna' = reais + contas internas.
--   semana 'fechada'  = a última semana completa, segunda 00:00 a domingo 23:59 (America/Sao_Paulo):
--                       w1 = date_trunc('week', now() at time zone 'America/Sao_Paulo'), w0 = w1 - 7 dias;
--          'anterior' = a semana antes dela (w0 - 7 dias a w0), para a variação.
-- Filtros (#96): conta interna = public.is_internal_account(id) = profiles.is_internal (lista
-- app_config.internal_accounts, a conta do dono, só no banco) OU conta QA em allowed_players (vale
-- mesmo com o QA em is_test = false durante um teste). 'sem_interna' exclui profiles.is_test e as
-- internas de TODAS as colunas; 'com_interna' = os mesmos mais as internas, só para comparação.
-- Robôs (is_test e não internos) ficam fora dos dois.
--
-- Medidas da semana (janela [w0, w1) aplicada à data de cada linha):
--   novos (profiles.created_at), compras por kind e receita (dobroes_purchases.created_at), vales
--   iniciados/aplicados/pendentes (ad_vales.created_at), contas que gastaram dobrões (dobroes_ledger
--   delta < 0, created_at), partidas online finalizadas e ranqueadas (online_matches.updated_at).
-- Medidas "no momento da consulta" (só na linha 'fechada'; na 'anterior' ficam nulas): DAU/WAU/MAU
--   e retorno 1/7 dias vêm de profiles.last_seen_at, que guarda só a última visita (não há
--   histórico de sessões; retorno N dias = última visita >= criação + N dias, superestima); push
--   ativo = conta com token cujo check_status não é 'unregistered'.
-- Receita: preço de tabela (dobroes_products.price_cents, #96) dos pacotes kind = 'real' (bruta) e
--   x 0,85 (líquida, taxa de 15% da Play), na semana e acumulada até o fim da semana. Não há valor
--   cobrado gravado no banco.
-- compras_real_externas: compra real de conta que não é interna nem teste (independe do escopo).
-- alerta_real_nao_marcada (#96): as mesmas compras nos últimos 7 dias corridos (igual em todas as
--   linhas). Maior que zero: conferir na view privada dobroes_purchases_com_origem se o comprador é
--   do time; se for, entra em app_config.internal_accounts + refresh_all_internal (skill
--   beta-testers); se não for, é receita de verdade. O relatório mostra só a contagem.
-- alertas (linha 'sem_interna'/'fechada'): gatilhos do #86 atingidos nesta consulta.
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
