-- Aviso de lançamento na entrada do jogo (#36, 0.34.6).
-- Popup "Naval Battle Classic chegou à Google Play!" mostrado uma vez por conta, só no Android
-- (decisão do John, 25/10: no iPhone nenhum aviso aparece). Ligado pelo interruptor
-- app_config.launch_notice_enabled, que nasce 'false' (ligar quando a Play aprovar a produção e a
-- 0.34.6 estiver na produção). Duas variações: 'tester' (agradecimento) e 'new' (boas-vindas).
-- Aplicar depois de feedback.sql, test_accounts.sql e dobroes.sql.

insert into public.app_config (key, value) values ('launch_notice_enabled', 'false')
on conflict (key) do nothing;

-- Quem já viu o aviso (por conta, vale em qualquer aparelho). RLS ligada e sem política:
-- só as RPCs abaixo (security definer) mexem.
create table if not exists public.launch_notice_seen (
  user_id uuid primary key references auth.users(id) on delete cascade,
  variant text not null check (variant in ('tester', 'new')),
  seen_at timestamptz not null default now()
);
alter table public.launch_notice_seen enable row level security;
revoke all on public.launch_notice_seen from anon, authenticated;

-- A conta participou do teste fechado? NÃO usa beta_eligible() (desde a 0.34.5 vale só para QA e
-- John). Critérios: lista de testadores (play/manual/qa) pelo e-mail de login (auth.users, nunca
-- profiles.email) ou pelo user_id; aparelho iPhone registrado (testadores do .ipa); ou medalha
-- Beta Tester de conta criada antes de app_config.beta_ends_at. Com beta_ends_at vazio a medalha
-- NÃO conta: in_beta() é verdadeiro e toda conta nova da produção ganha a medalha.
create or replace function public.launch_was_beta_tester(p_user uuid)
returns boolean language sql stable security definer set search_path = public as $$
  select
    exists (
      select 1
      from public.allowed_players a
      where a.source in ('play', 'manual', 'qa')
        and (
          a.user_id = p_user
          or a.email = (select lower(trim(u.email)) from auth.users u where u.id = p_user)
        )
    )
    or exists (
      select 1 from public.device_checks d where d.user_id = p_user and d.platform = 'ios'
    )
    or exists (
      select 1
      from public.user_badges b
      join public.profiles p on p.id = b.user_id
      where b.user_id = p_user
        and b.badge_code = 'beta_tester'
        and p.created_at < (
          select nullif(trim(c.value), '')::timestamptz from public.app_config c where c.key = 'beta_ends_at'
        )
    );
$$;
revoke all on function public.launch_was_beta_tester(uuid) from public, anon, authenticated;

-- Estado do aviso para quem entra. Sem login: variante 'new', seen = false. No iPhone
-- (p_platform = 'ios') enabled é sempre false: o aviso é só do Android.
create or replace function public.launch_notice_status(p_platform text default 'android')
returns table (enabled boolean, variant text, seen boolean)
language plpgsql stable security definer set search_path = public as $$
declare
  uid uuid := auth.uid();
  on_ boolean;
begin
  on_ := coalesce(
    (select lower(trim(c.value)) = 'true' from public.app_config c where c.key = 'launch_notice_enabled'),
    false
  ) and lower(trim(coalesce(p_platform, 'android'))) <> 'ios';

  if uid is null then
    return query select on_, 'new'::text, false;
    return;
  end if;

  return query
    select on_,
           case when public.launch_was_beta_tester(uid) then 'tester' else 'new' end,
           exists (select 1 from public.launch_notice_seen s where s.user_id = uid);
end $$;
revoke all on function public.launch_notice_status(text) from public;
grant execute on function public.launch_notice_status(text) to anon, authenticated;

-- "Entendi": grava que a conta viu o aviso, com a variante calculada aqui (o cliente não manda).
create or replace function public.launch_notice_ack()
returns boolean language plpgsql security definer set search_path = public as $$
declare
  uid uuid := auth.uid();
begin
  if uid is null then
    return false;
  end if;
  insert into public.launch_notice_seen (user_id, variant)
  values (uid, case when public.launch_was_beta_tester(uid) then 'tester' else 'new' end)
  on conflict (user_id) do nothing;
  return true;
end $$;
revoke all on function public.launch_notice_ack() from public, anon;
grant execute on function public.launch_notice_ack() to authenticated;
