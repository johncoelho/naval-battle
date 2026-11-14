-- Retorno de verdade D1/D7/D30 e ativos por semana, a partir de activity_days (#97 bloco B, 0.44.4).
-- SÓ LEITURA (selects nas views privadas): nenhum insert, update, delete nem chamada que grave. Só
-- agregados, nunca user_id, e-mail nem apelido. Roda DEPOIS da migração `behavior_activity_days` (as views
-- só existem a partir dela) e só com service_role (o CLI do projeto já é):
--
--   SUPABASE_PROJECT_ID=cwtslesnthbenxswdcbv supabase db query --linked -o table -f docs/medicao-retorno.sql
--
-- Como ler:
--  * Série real começa em app_config.activity_series_start (o dia seguinte ao da aplicação). Antes disso a
--    tabela não existia: coortes mais antigas NÃO aparecem aqui, e o retorno delas segue só aproximado em
--    docs/medicao-semanal.sql (última visita). Coorte nova leva 30 dias para fechar o D30.
--  * retorno_dN / coorte_dN = taxa; só vale com completa_dN = true (a coorte inteira já passou pelo dia N).
--    Incompleta = parcial, não comparar com a semana fechada.
--  * Célula com menos de 5 contas vem nula (regra n < 5): não é erro, é privacidade. Não reconstruir por
--    subtração nem postar linha por conta. Vale também para o retorno: retorno_dN nulo com coorte_dN preenchida
--    = quem voltou ou quem não voltou é menos de 5 (célula pequena), não falta de dado.
--  * D1 = "abriu o app com conta e com rede" (não "jogou"; a partida contra a IA conta, convidado e aparelho
--    sem rede o dia todo não). Partidas contra bot (#105) ainda não se separam: ver o cabeçalho de
--    supabase/behavior.sql (ponto de encaixe `bot_matches`).
--  * Exclui is_test e contas internas (#96) na leitura.
select 'serie_desde' as item, value as valor from public.app_config where key = 'activity_series_start';

select * from public.behavior_retorno_semanal order by semana desc;
select * from public.behavior_ativos_semanais order by semana desc limit 12;
select * from public.behavior_ativos_diarios order by dia desc limit 30;
