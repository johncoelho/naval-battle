-- Naval Battle · contadores de funil, só agregados (issue #137, parte do plano #132, 0.48.0)
-- Rode no SQL Editor depois de schema.sql, economy.sql (brt_today, config_int), test_accounts.sql,
-- internal_accounts.sql, online.sql, walkover.sql e referral.sql. Idempotente. Migração no banco:
-- `funnel_counters` (mesmo conteúdo). Aplicar ANTES de publicar o app 0.48.0: o app novo contra um banco sem
-- esta migração recebe 404 no RPC e ignora o erro (nada quebra, mas o evento se perde).
-- Teste que desfaz tudo: supabase/tests/funnel_ensaio.sql (cole este arquivo e o ensaio na MESMA execução; o
-- raise no fim cancela a transação inteira, DDL inclusive).
--
-- O problema: não existe medição das ações que o plano #132 quer mudar (loja vista, convite compartilhado, gancho
-- do fim da 1ª partida, pedido de avaliação, push aberto). `activity_days` (#97) só diz que a conta abriu o app.
--
-- Decisão de desenho (Tech Lead, #137): CONTADOR DIÁRIO, não tabela de eventos. Uma linha por
-- (dia, evento, origem, plataforma, interno) com `n`; sem user_id, sem hora, sem aparelho, sem texto livre. Uma
-- linha por evento convidaria a guardar `user_id` e hora e viraria dado pessoal.
--
-- Privacidade (site/privacy.html, linha "Contadores de uso"; README → Especificação técnica):
--  * A tabela não tem identificador de pessoa. O servidor vê a conta (auth.uid()) só no instante da chamada, para
--    saber se é de teste ou interna, e NÃO a guarda.
--  * Vocabulário fechado (`funnel_catalog`): evento ou origem fora da lista é descartado em silêncio; nenhum texto
--    livre chega ao banco. Evento novo = migração nova do catálogo (e linha na política, se mudar a natureza).
--  * RLS ligado SEM política; `revoke all` de anon e authenticated; só service_role lê e apaga. O app só chama o RPC.
--  * Conta de teste ou interna cai em `interno = true` NA GRAVAÇÃO (não há id para excluir depois). Marcar uma conta
--    como interna depois NÃO corrige o passado (em `activity_days` corrige): contas QA e de teste já nascem marcadas.
--  * Views só service_role, `security_invoker`, semana de segunda a domingo (Brasília), `interno = false`; toda
--    célula com menos de `activity_min_cell` (5) vira nula. Sem linha de total (um total publicado permitiria
--    subtrair uma célula nula). Sem quebra por versão. Nunca acrescentar dimensão: o público é pequeno.
--  * Retenção de 365 dias: `purge_funnel()` na rodada diária da esteira, ao lado de `purge_activity()`.
--  * Convidado e aparelho offline: o app não chama (sem sessão) e o evento é descartado.
--
-- O que mede o quê (catálogo abaixo, 0.48.0): store_viewed, invite_shared, first_match_end, hook_shown,
-- hook_rematch, hook_invite, hook_challenge, review_requested, push_opened. Convite ACEITO não tem origem (o código
-- é digitado): a origem do convite sai de invite_shared e a conversão é por semana (funnel_convites_semanais).
-- funnel_salas_fim_semanais lê online_matches e vale para todas as versões do app, retroativa.
--
-- Desfazer na produção:
--   drop view if exists public.funnel_salas_fim_semanais;
--   drop view if exists public.funnel_convites_semanais;
--   drop view if exists public.funnel_eventos_semanais;
--   drop function if exists public.purge_funnel();
--   drop function if exists public.log_funnel_event(text, text, text);
--   drop table if exists public.funnel_counters;
--   drop table if exists public.funnel_catalog;

-- ------------------------------------------------------------------ catálogo (vocabulário fechado)

create table if not exists public.funnel_catalog (
  event  text not null check (event  ~ '^[a-z_]{1,32}$'),
  origin text not null check (origin ~ '^[a-z_]{1,32}$'),
  primary key (event, origin)
);

alter table public.funnel_catalog enable row level security;
revoke all on public.funnel_catalog from public, anon, authenticated;
grant select on public.funnel_catalog to service_role;

insert into public.funnel_catalog (event, origin) values
  ('store_viewed', 'menu'), ('store_viewed', 'hook'), ('store_viewed', 'popup'), ('store_viewed', 'outro'),
  ('invite_shared', 'referral_card'), ('invite_shared', 'hook'), ('invite_shared', 'settings_store'),
  ('first_match_end', 'bot_win'), ('first_match_end', 'bot_loss'), ('first_match_end', 'bot_abandon'),
  ('first_match_end', 'human_win'), ('first_match_end', 'human_loss'), ('first_match_end', 'human_abandon'),
  ('hook_shown', 'win'), ('hook_shown', 'loss'),
  ('hook_rematch', 'win'), ('hook_rematch', 'loss'),
  ('hook_invite', 'win'), ('hook_invite', 'loss'),
  ('hook_challenge', 'win'), ('hook_challenge', 'loss'),
  ('review_requested', 'win'), ('review_requested', 'rankup'),
  ('push_opened', 'new_version'), ('push_opened', 'quick_match_invite'), ('push_opened', 'daily_invite'),
  ('push_opened', 'friend_online'), ('push_opened', 'match_invite'), ('push_opened', 'outro')
on conflict do nothing;

-- ------------------------------------------------------------------ contadores

create table if not exists public.funnel_counters (
  day      date    not null,
  event    text    not null,
  origin   text    not null,
  platform text    not null check (platform in ('android', 'ios')),
  interno  boolean not null,
  n        integer not null default 0,
  primary key (day, event, origin, platform, interno)
);

alter table public.funnel_counters enable row level security;
revoke all on public.funnel_counters from public, anon, authenticated;
grant select, delete on public.funnel_counters to service_role;

-- ------------------------------------------------------------------ gravação

-- Só `authenticated` executa. Instrumentação nunca derruba o jogo: qualquer erro é engolido (mesmo padrão de
-- `log_activity_day`). Evento, origem ou plataforma fora da lista: nada é gravado. `interno` = conta de teste
-- (profiles.is_test) ou interna (is_internal_account), decidido aqui e não guardado por conta.
create or replace function public.log_funnel_event(p_event text, p_origin text, p_platform text)
returns void
language plpgsql security definer set search_path = public as $$
declare
  me uuid := auth.uid();
  interna boolean;
begin
  if me is null or p_platform is null or p_platform not in ('android', 'ios') then
    return;
  end if;
  if not exists (select 1 from public.funnel_catalog c where c.event = p_event and c.origin = p_origin) then
    return;
  end if;
  interna := coalesce((select p.is_test from public.profiles p where p.id = me), false)
             or public.is_internal_account(me);
  insert into public.funnel_counters (day, event, origin, platform, interno, n)
  values (public.brt_today(), p_event, p_origin, p_platform, interna, 1)
  on conflict (day, event, origin, platform, interno)
  do update set n = public.funnel_counters.n + 1;
exception when others then
  null;
end $$;
revoke all on function public.log_funnel_event(text, text, text) from public, anon;
grant execute on function public.log_funnel_event(text, text, text) to authenticated;

-- ------------------------------------------------------------------ retenção

-- 365 dias. A rodada diária da esteira chama `select public.purge_funnel();` (ao lado de `purge_activity()`);
-- devolve quantas linhas apagou.
create or replace function public.purge_funnel()
returns bigint
language plpgsql security definer set search_path = public as $$
declare n bigint;
begin
  delete from public.funnel_counters where day < public.brt_today() - 365;
  get diagnostics n = row_count;
  return n;
end $$;
revoke all on function public.purge_funnel() from public, anon, authenticated;
grant execute on function public.purge_funnel() to service_role;

-- ------------------------------------------------------------------ views (só service_role)

-- Eventos por semana, evento, origem e plataforma. Conta de teste e interna ficam fora (interno = false).
-- semana_completa = false na semana em curso (ainda soma). n < activity_min_cell vira nulo.
create or replace view public.funnel_eventos_semanais
with (security_invoker = true) as
with a as (
  select date_trunc('week', c.day::timestamp)::date as semana, c.event, c.origin, c.platform, sum(c.n) as n
    from public.funnel_counters c
   where not c.interno
   group by 1, 2, 3, 4
)
select a.semana,
       a.event    as evento,
       a.origin   as origem,
       a.platform as plataforma,
       case when a.n >= public.config_int('activity_min_cell', 5) then a.n end as n,
       (a.semana + 7 <= public.brt_today()) as semana_completa
  from a
 order by a.semana, a.event, a.origin, a.platform;

-- Convites aceitos por semana de aceite (Brasília) e quantos já pagaram o convidante (inviter_paid_at). Fora as
-- duas pontas de teste ou internas. Sem origem: o código de convite é digitado. aceitos_pagos nulo também quando
-- quem não pagou (aceitos - pagos) é menor que o limite, senão uma célula pequena identificaria contas.
create or replace view public.funnel_convites_semanais
with (security_invoker = true) as
with r as (
  select date_trunc('week', (f.redeemed_at at time zone 'America/Sao_Paulo')::date::timestamp)::date as semana,
         count(*) as aceitos,
         count(*) filter (where f.inviter_paid_at is not null) as pagos
    from public.referrals f
    join public.profiles pi on pi.id = f.inviter_id
    join public.profiles pv on pv.id = f.invitee_id
   where not coalesce(pi.is_test, false) and not public.is_internal_account(f.inviter_id)
     and not coalesce(pv.is_test, false) and not public.is_internal_account(f.invitee_id)
   group by 1
)
select r.semana,
       case when r.aceitos >= public.config_int('activity_min_cell', 5) then r.aceitos end as aceitos,
       case when r.aceitos >= public.config_int('activity_min_cell', 5)
             and r.pagos >= public.config_int('activity_min_cell', 5)
             and r.aceitos - r.pagos >= public.config_int('activity_min_cell', 5) then r.pagos end as aceitos_pagos
  from r
 order by r.semana;

-- Como as salas online terminam, por semana do fim (Brasília). Lê online_matches: vale para todas as versões do
-- app, retroativo, sem tabela nova. motivo: fim_normal (finished), wo (ausência resolvida pelo servidor),
-- abandono (abandoned com os dois lados na sala, saída ou sala parada), expirou_em_espera (abandoned sem
-- convidado: ninguém entrou). Salas com anfitrião ou convidado de teste ou interno ficam fora.
create or replace view public.funnel_salas_fim_semanais
with (security_invoker = true) as
with s as (
  select date_trunc('week', (m.updated_at at time zone 'America/Sao_Paulo')::date::timestamp)::date as semana,
         case when m.status = 'finished'          then 'fim_normal'
              when m.walkover                     then 'wo'
              when m.guest_id is null             then 'expirou_em_espera'
              else 'abandono' end as motivo,
         m.ranked as ranqueada,
         m.is_quick_match as partida_rapida,
         count(*) as n
    from public.online_matches m
    join public.profiles ph on ph.id = m.host_id
    left join public.profiles pg on pg.id = m.guest_id
   where m.status in ('finished', 'abandoned')
     and not coalesce(ph.is_test, false) and not public.is_internal_account(m.host_id)
     and not coalesce(pg.is_test, false)
     and (m.guest_id is null or not public.is_internal_account(m.guest_id))
   group by 1, 2, 3, 4
)
select s.semana, s.motivo, s.ranqueada, s.partida_rapida,
       case when s.n >= public.config_int('activity_min_cell', 5) then s.n end as n
  from s
 order by s.semana, s.motivo, s.ranqueada, s.partida_rapida;

revoke all on public.funnel_eventos_semanais, public.funnel_convites_semanais, public.funnel_salas_fim_semanais
  from public, anon, authenticated;
grant select on public.funnel_eventos_semanais, public.funnel_convites_semanais, public.funnel_salas_fim_semanais
  to service_role;
