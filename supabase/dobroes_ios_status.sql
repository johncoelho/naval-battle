-- Naval Battle · #65 fatia 2: dobroes_store_status() passa a devolver real_ios_enabled.
-- Rode no SQL Editor (ou migração com este mesmo conteúdo). Mesmo corpo de dobroes.sql (fonte da verdade),
-- só com a coluna nova no fim; app antigo (Android e iPhone até 0.45.x) lê as colunas pelo nome e ignora a nova.
-- Depende de dobroes.sql e dobroes_ios.sql (flag real_billing_ios_enabled, nasce 'false').
--
-- ROLLBACK: reaplicar a definição antiga de dobroes.sql (sem a coluna); o app novo trata ausência como falso.

drop function if exists public.dobroes_store_status();
create or replace function public.dobroes_store_status()
returns table (beta boolean, real_enabled boolean, premio_enabled boolean,
               premio_claimed boolean, premio_resets_in_seconds integer, vitrine_beta boolean,
               real_ios_enabled boolean)
language sql stable security definer set search_path = public as $$
  select
    public.beta_eligible(auth.uid()),
    coalesce((select value = 'true' from public.app_config where key = 'real_billing_enabled'), false),
    coalesce((select value = 'true' from public.app_config where key = 'premio_beta_enabled'), false),
    exists (select 1 from public.dobroes_purchases d
             where d.user_id = auth.uid() and d.premio and d.day = public.brt_today()),
    public.brt_seconds_to_midnight(),
    public.beta_eligible(auth.uid()) and public.beta_store_on(),
    coalesce((select value = 'true' from public.app_config where key = 'real_billing_ios_enabled'), false);
$$;
revoke all on function public.dobroes_store_status() from public, anon;
grant execute on function public.dobroes_store_status() to authenticated;
