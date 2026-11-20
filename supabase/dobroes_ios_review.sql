-- Naval Battle · #65 Plano B (revisão da Apple, 2.1(b)): a aba Dobrões do iPhone abre por conta, só no servidor.
-- dobroes_store_status().real_ios_enabled = (flag real_billing_ios_enabled = 'true')
--                                           OR (beta_eligible(auth.uid()) AND beta_store_on()).
-- Efeito: a conta de demonstração da revisão, as contas QA e a do John (beta_eligible) veem a aba com a vitrine
-- beta ligada e compram em Sandbox (o servidor só credita Sandbox para essas contas); o público continua sem a aba
-- até o John ligar a flag global. Nada muda no app (a 0.46.0 lê a coluna pelo nome) nem no Android.
-- Idempotente: create or replace com a mesma assinatura. Mesmo corpo de dobroes.sql (fonte da verdade).
-- Depende de dobroes.sql e dobroes_ios.sql.
--
-- ROLLBACK (volta a só a flag global): reaplicar supabase/dobroes_ios_status.sql na versão anterior, isto é,
--   trocar a última coluna por
--   coalesce((select value = 'true' from public.app_config where key = 'real_billing_ios_enabled'), false);

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
    -- #65 (Plano B): a aba Dobrões do iPhone abre com a flag global OU para conta de brinde
    -- (QA e e-mails de beta_gift_emails) com a vitrine beta ligada; o público só com a flag.
    coalesce((select value = 'true' from public.app_config where key = 'real_billing_ios_enabled'), false)
      or (public.beta_eligible(auth.uid()) and public.beta_store_on());
$$;
revoke all on function public.dobroes_store_status() from public, anon;
grant execute on function public.dobroes_store_status() to authenticated;
