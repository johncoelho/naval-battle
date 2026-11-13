-- Pré-voo da venda de dobrões (#93). SÓ LEITURA: nenhum insert, update, delete nem chamada que grave.
-- Rodar pelo tools/dobroes-prevoo.sh (que também confere a Edge e o secret) ou sozinho:
--   supabase db query --linked -o csv -f tools/dobroes-prevoo.sql
-- (fora do repositório, num workdir com supabase/.temp/project-ref; ver o cabeçalho do .sh).
-- Saída: uma coluna `linha` por conferência, "STATUS item: detalhe". STATUS: OK | FALHA | INFO.
-- Só contagens e valores de flag: nunca e-mail, user_id ou apelido.
with
flags as (
  select key, value from public.app_config
   where key in ('real_billing_enabled', 'premio_beta_enabled', 'beta_store_enabled')
),
gate as (
  select count(*) as n from public.dobroes_gate_licenca()
),
sem_carteira as (
  select count(*) as n, coalesce(sum(p.credits), 0) as soma
    from public.profiles p
   where coalesce(p.credits, 0) > 0
     and not exists (select 1 from public.dobroes_wallets w where w.user_id = p.id)
),
divergentes as (
  select count(*) as n
    from public.profiles p
    join public.dobroes_wallets w on w.user_id = p.id
   where coalesce(p.credits, 0) <> coalesce((select l.balance_after from public.dobroes_ledger l
                                              where l.user_id = p.id order by l.id desc limit 1), p.credits)
),
produtos as (
  select count(*) as n from public.dobroes_products
   where (product_id, doubloons) in (('dobroes_bolsa', 500), ('dobroes_bau', 1100),
                                     ('dobroes_cofre', 3000), ('dobroes_tesouro', 6500))
),
mig_esperadas(name) as (
  values ('dobroes_billing'), ('dobroes_premio_beta'), ('dobroes_gate_licenca_fix'),
         ('dobroes_saldo'), ('dobroes_online_cap_500'), ('dobroes_saldo_volta1')
),
mig_faltando as (
  select string_agg(e.name, ', ' order by e.name) as nomes, count(*) as n
    from mig_esperadas e
   where not exists (select 1 from supabase_migrations.schema_migrations m where m.name = e.name)
),
ultima_versao as (
  select version_name, version_code, android_live
    from public.app_releases order by version_code desc limit 1
)
select rpad(status, 6) || item || ': ' || detalhe as linha from (
  select 1 as ord, 'flag real_billing_enabled' as item,
         case when coalesce((select value from flags where key = 'real_billing_enabled'), '') in ('false', 'true')
              then 'INFO' else 'FALHA' end as status,
         'valor = ' || coalesce((select value from flags where key = 'real_billing_enabled'), '(ausente)')
           || ' (antes do passo h deve ser false)' as detalhe
  union all
  select 2, 'flag premio_beta_enabled', 'INFO',
         'valor = ' || coalesce((select value from flags where key = 'premio_beta_enabled'), '(ausente)')
  union all
  select 3, 'flag beta_store_enabled', 'INFO',
         'valor = ' || coalesce((select value from flags where key = 'beta_store_enabled'), '(ausente)')
           || ' (DOB-22 precisa de true)'
  union all
  select 4, 'gate dobroes_gate_licenca()',
         case when (select n from gate) = 0 then 'OK' else 'FALHA' end,
         (select n from gate)::text || ' testador(es) sem app 0.33.3+ (precisa 0 antes de mudar a licenca)'
  union all
  select 5, 'contas sem carteira com credits > 0',
         case when (select n from sem_carteira) = 0 then 'OK' else 'FALHA' end,
         (select n from sem_carteira)::text || ' conta(s), soma ' || (select soma from sem_carteira)::text
           || ' (zera com dobroes_open_all, passo f)'
  union all
  select 6, 'divergencia credits x razao',
         case when (select n from divergentes) = 0 then 'OK' else 'FALHA' end,
         (select n from divergentes)::text || ' carteira(s) com credits diferente do ultimo balance_after'
  union all
  select 7, 'produtos em dobroes_products',
         case when (select n from produtos) = 4 then 'OK' else 'FALHA' end,
         (select n from produtos)::text || ' de 4 (bolsa 500, bau 1100, cofre 3000, tesouro 6500)'
  union all
  select 8, 'migracoes dobroes_* aplicadas',
         case when (select n from mig_faltando) = 0 then 'OK' else 'FALHA' end,
         case when (select n from mig_faltando) = 0 then '6 de 6'
              else 'faltando: ' || (select nomes from mig_faltando) end
  union all
  select 9, 'ultima linha de app_releases',
         case when (select android_live from ultima_versao)
               and public.app_version_at_least((select version_name from ultima_versao), '0.34.9')
              then 'OK' else 'FALHA' end,
         coalesce((select version_name || ' (' || version_code || ') android_live = ' || android_live
                     from ultima_versao), '(sem linha)')
) c
order by ord;
