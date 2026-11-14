-- Vales de anúncio premiado por desfecho no aparelho x situação no servidor (#95, plano #86).
-- SÓ LEITURA (um select): nenhum insert, update, delete nem chamada que grave. Só agregados:
-- nunca user_id, e-mail nem apelido. Roda DEPOIS da migração `ad_reward_report` (a coluna
-- ad_vales.client_outcome só existe a partir dela).
--
--   SUPABASE_PROJECT_ID=cwtslesnthbenxswdcbv supabase db query --linked -o table -f docs/medicao-vales.sql
--
-- Como ler (últimos 30 dias, fora is_test e contas internas, mesmo filtro de docs/medicao-semanal.sql):
--   desfecho  = o que o app relatou logo depois do show(): earned | closed_early | unavailable;
--               'sem_relato' = app antigo (sem a RPC) ou o relato não chegou.
--   situacao  = applied (a Edge ads-reward aplicou) | pending (ainda dentro dos 15 min) | vencido.
--   earned + vencido  = o jogador assistiu e o crédito não veio: é falha de SSV (Edge ou Google);
--                       só aqui se abre correção na Edge.
--   closed_early e unavailable vencidos são esperados (abandono, sem preenchimento, falha de exibição).
--   Taxa que importa para a meta de 80% do #86: applied / (applied + earned vencido).
select coalesce(v.client_outcome, 'sem_relato') as desfecho,
       case when v.status = 'applied' then 'applied'
            when v.expires_at < now() then 'vencido'
            else 'pending' end as situacao,
       v.place as lugar,
       count(*) as vales
  from public.ad_vales v
  join public.profiles p on p.id = v.user_id
 where v.created_at >= now() - interval '30 days'
   and not coalesce(p.is_test, false)
   and not public.is_internal_account(p.id)
 group by 1, 2, 3
 order by 1, 2, 3;
