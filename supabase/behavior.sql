-- Naval Battle · registro diário de atividade e retenção de verdade (issue #97, bloco B, 0.44.4)
-- Rode no SQL Editor depois de schema.sql, economy.sql (brt_today), test_accounts.sql e
-- internal_accounts.sql. Idempotente. Migração no banco: `behavior_activity_days` (mesmo conteúdo) e,
-- em seguida, `presence_activity_day` (a nova `touch_presence` de supabase/presence.sql, que chama
-- `log_activity_day`; sem a função ela não grava e a presença segue normal).
-- Teste que desfaz tudo: supabase/tests/behavior_ensaio.sql (cole este arquivo, presence.sql e o ensaio
-- na MESMA execução; o raise no fim cancela a transação inteira, DDL inclusive).
--
-- O problema: `profiles.last_seen_at` guarda só a última visita, então retorno 1/7 dias e DAU/WAU
-- superestimam e não há como medir D1/D7/D30 de verdade (#94, #97). Aqui entra um registro de
-- "esta conta abriu o app neste dia", uma linha por conta por dia, e nada além disso.
--
-- O que é medido (e o que não é): o ponto é gravado dentro de `touch_presence`, que o app chama a cada
-- minuto com o jogo aberto, com conta e com rede (App.kt: LaunchedEffect de presença, em qualquer tela).
-- A partida contra a IA também conta, porque durante ela o ponto sai como "in_match". Fora: convidado
-- (sem conta) e aparelho sem rede o dia inteiro. Logo, D1 = "abriu o app com conta e com rede",
-- NÃO "jogou". Jogar de fato fica para os eventos de partida (bloco seguinte da #97).
--
-- PONTO DE ENCAIXE (#105, partidas contra bot): `bot_matches` virá na #105 com user_id, `started_at` e
-- `purge_bots()` próprios. NÃO existe aqui e esta tabela não distingue o motivo da abertura: quem só
-- jogou contra bot também marca o dia. Quando a #105 existir, a separação é uma view nova que compara
-- `activity_days` com `bot_matches` (dia com bot e sem partida humana); nada nesta tabela muda.
--
-- Privacidade (política do #57, inventário em site/privacy.html e README → Especificação técnica):
--  * `activity_days` tem user_id, então: RLS ligado SEM política, `revoke all` de anon e authenticated,
--    só service_role lê (as views) e só as funções abaixo escrevem. O app não lê nem escreve a tabela.
--  * Só (user_id, dia). Sem horário, aparelho, versão, tela nem texto livre.
--  * Retenção de 90 dias: `purge_activity()` na rodada diária da esteira (não há pg_cron), como
--    `purge_moderation()`. `on delete cascade` em auth.users apaga a conta e o histórico dela.
--  * A tabela grava TODAS as contas (teste e internas também); a exclusão é na leitura, assim marcar uma
--    conta como interna depois corrige o histórico (mesmo desenho da #96).
--  * As views devolvem só agregado por dia ou semana, sem user_id, apelido nem e-mail, e anulam toda
--    célula com menos de 5 contas (`activity_min_cell`), para uma semana pequena não identificar pessoa.
--    Nunca postar linha por conta em relatório ou issue (repositório e issues são públicos).
--
-- Início da série: `app_config.activity_series_start` = primeiro dia COMPLETO com registro (o dia
-- seguinte ao da aplicação; no dia da aplicação quem já tinha aberto o app antes não tem linha). As views
-- só leem coortes criadas a partir dessa data; contas mais antigas ficam com a medição aproximada de
-- docs/medicao-semanal.sql. Coortes com mais de 89 dias saem da leitura (os dias de retorno delas já
-- foram apagados pela retenção; mostrar 0 seria mentira).
--
-- Desfazer na produção:
--   drop view if exists public.behavior_retorno_semanal;
--   drop view if exists public.behavior_ativos_semanais;
--   drop view if exists public.behavior_ativos_diarios;
--   drop function if exists public.purge_activity();
--   drop function if exists public.log_activity_day(uuid);
--   drop table if exists public.activity_days;
--   delete from public.app_config where key in ('activity_series_start', 'activity_min_cell');
--   (e recolocar a touch_presence anterior, sem a chamada a log_activity_day)

-- ------------------------------------------------------------------ tabela

create table if not exists public.activity_days (
  user_id uuid not null references auth.users(id) on delete cascade,
  day     date not null,
  primary key (user_id, day)
);
create index if not exists activity_days_day_idx on public.activity_days (day);

alter table public.activity_days enable row level security;
revoke all on public.activity_days from public, anon, authenticated;
grant select, delete on public.activity_days to service_role;

-- primeiro dia completo da série (seed só na primeira aplicação) e menor célula publicável
insert into public.app_config (key, value)
values ('activity_series_start', (public.brt_today() + 1)::text), ('activity_min_cell', '5')
on conflict (key) do nothing;

-- ------------------------------------------------------------------ gravação

-- Chamada por touch_presence, dentro de um bloco que engole qualquer erro: registrar o dia nunca pode
-- derrubar a presença. Uma linha por conta por dia; repetir é inofensivo.
create or replace function public.log_activity_day(p_user uuid)
returns void
language sql security definer set search_path = public as $$
  insert into public.activity_days (user_id, day)
  values (p_user, public.brt_today())
  on conflict do nothing;
$$;
revoke all on function public.log_activity_day(uuid) from public, anon, authenticated;

-- ------------------------------------------------------------------ retenção

-- 90 dias cobrem D30 com folga. A rodada diária da esteira chama `select public.purge_activity();`
-- (como `purge_moderation()`); devolve quantas linhas apagou.
create or replace function public.purge_activity()
returns bigint
language plpgsql security definer set search_path = public as $$
declare n bigint;
begin
  delete from public.activity_days where day < public.brt_today() - 90;
  get diagnostics n = row_count;
  return n;
end $$;
revoke all on function public.purge_activity() from public, anon, authenticated;
grant execute on function public.purge_activity() to service_role;

-- ------------------------------------------------------------------ views (só service_role)

-- Contas que contam: não são de teste nem internas (#96). security_invoker: roda com o direito de quem lê.

-- Ativos por dia (DAU de verdade). Célula com menos de activity_min_cell contas vira nula.
create or replace view public.behavior_ativos_diarios
with (security_invoker = true) as
with a as (
  select d.day, count(*) as n
    from public.activity_days d
    join public.profiles p on p.id = d.user_id
   where not coalesce(p.is_test, false) and not public.is_internal_account(d.user_id)
   group by d.day
)
select a.day as dia,
       case when a.n >= public.config_int('activity_min_cell', 5) then a.n end as ativos,
       (a.day < (select coalesce(nullif(value, '')::date, public.brt_today()) from public.app_config
                  where key = 'activity_series_start')) as antes_da_serie
  from a
 order by a.day;

-- Ativos por semana de calendário (segunda a domingo, Brasília): contas distintas (WAU de verdade).
-- semana_completa = false na semana em curso (ainda soma).
create or replace view public.behavior_ativos_semanais
with (security_invoker = true) as
with a as (
  select date_trunc('week', d.day::timestamp)::date as semana, count(distinct d.user_id) as n
    from public.activity_days d
    join public.profiles p on p.id = d.user_id
   where not coalesce(p.is_test, false) and not public.is_internal_account(d.user_id)
   group by 1
)
select a.semana,
       case when a.n >= public.config_int('activity_min_cell', 5) then a.n end as ativos,
       (a.semana + 7 <= public.brt_today()) as semana_completa
  from a
 order by a.semana;

-- Retorno D1/D7/D30 por coorte de semana de criação (segunda a domingo, Brasília).
-- Dn = a conta tem registro no dia (criação + n), exatamente. Só entram contas cujo dia Dn já terminou
-- (coorte_dN) e cujo registro ainda existe na retenção; completa_dN diz se a coorte inteira já passou
-- pelo Dn (senão a taxa é parcial e vem marcada). Toda contagem abaixo de activity_min_cell é nula,
-- inclusive coorte. retorno_dN também é nulo quando quem voltou (r) ou quem não voltou (coorte_dN - r) é
-- menor que activity_min_cell: senão uma célula pequena de um lado identificaria contas. Taxa nula com coorte
-- preenchida = célula pequena, não erro. Taxa = retorno_dN / coorte_dN calculada por quem lê.
create or replace view public.behavior_retorno_semanal
with (security_invoker = true) as
with ini as (
  select coalesce(nullif((select value from public.app_config where key = 'activity_series_start'), '')::date,
                  public.brt_today()) as d0,
         public.config_int('activity_min_cell', 5) as minc,
         public.brt_today() as hoje
),
c as (
  select p.id,
         (p.created_at at time zone 'America/Sao_Paulo')::date as criado
    from public.profiles p, ini
   where not coalesce(p.is_test, false) and not public.is_internal_account(p.id)
     and (p.created_at at time zone 'America/Sao_Paulo')::date >= ini.d0
     and (p.created_at at time zone 'America/Sao_Paulo')::date >= ini.hoje - 89
),
r as (
  select c.*,
         n.n,
         (c.criado + n.n < ini.hoje) as maduro,
         exists (select 1 from public.activity_days d where d.user_id = c.id and d.day = c.criado + n.n) as voltou
    from c cross join ini cross join (values (1), (7), (30)) as n(n)
),
g as (
  select date_trunc('week', criado::timestamp)::date as semana,
         count(distinct id)                                   as coorte,
         count(*) filter (where n = 1  and maduro)            as c1,
         count(*) filter (where n = 1  and maduro and voltou) as r1,
         count(*) filter (where n = 7  and maduro)            as c7,
         count(*) filter (where n = 7  and maduro and voltou) as r7,
         count(*) filter (where n = 30 and maduro)            as c30,
         count(*) filter (where n = 30 and maduro and voltou) as r30
    from r
   group by 1
)
select g.semana,
       case when g.coorte >= ini.minc then g.coorte end as coorte,
       case when g.c1  >= ini.minc then g.c1  end as coorte_d1,
       case when g.c1  >= ini.minc and g.r1  >= ini.minc and g.c1  - g.r1  >= ini.minc then g.r1  end as retorno_d1,
       (g.c1  = g.coorte) as completa_d1,
       case when g.c7  >= ini.minc then g.c7  end as coorte_d7,
       case when g.c7  >= ini.minc and g.r7  >= ini.minc and g.c7  - g.r7  >= ini.minc then g.r7  end as retorno_d7,
       (g.c7  = g.coorte) as completa_d7,
       case when g.c30 >= ini.minc then g.c30 end as coorte_d30,
       case when g.c30 >= ini.minc and g.r30 >= ini.minc and g.c30 - g.r30 >= ini.minc then g.r30 end as retorno_d30,
       (g.c30 = g.coorte) as completa_d30
  from g cross join ini
 order by g.semana;

revoke all on public.behavior_ativos_diarios, public.behavior_ativos_semanais, public.behavior_retorno_semanal
  from public, anon, authenticated;
grant select on public.behavior_ativos_diarios, public.behavior_ativos_semanais, public.behavior_retorno_semanal
  to service_role;
