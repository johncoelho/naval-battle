-- Contas de teste automático (#23).
--
-- A cada envio à faixa fechada da Play, os testes automáticos da revisão do Google
-- (aparelhos do Firebase Test Lab) entram no jogo com contas novas. O app Android lê
-- a marca `firebase.test.lab` do aparelho e chama report_device_check uma vez por
-- conta e versão (no iPhone vai sempre falso, como base de comparação).
--
-- * device_checks é o log de diagnóstico: uma linha por conta e versão, com o que o
--   aparelho disse. Serve para conferir, depois de cada revisão, se as contas criadas
--   na janela do upload vieram mesmo marcadas. Só versão, plataforma e a marca: nada
--   do aparelho (modelo, ids) é guardado.
-- * A marca em profiles.is_test só vai num sentido: falso nunca desmarca. Desfazer um
--   falso positivo é SQL manual.
-- * O cliente não lê nem grava device_checks direto (RLS ligado, sem política).

create table if not exists public.device_checks (
  user_id     uuid        not null references auth.users(id) on delete cascade,
  app_version text        not null,
  platform    text        not null,
  test_lab    boolean     not null,
  created_at  timestamptz not null default now(),
  primary key (user_id, app_version)
);

alter table public.device_checks enable row level security;

create or replace function public.report_device_check(p_test_lab boolean, p_app_version text, p_platform text)
returns void
language plpgsql security definer set search_path = public as $$
declare
  me uuid := auth.uid();
begin
  if me is null then return; end if;
  insert into public.device_checks as d (user_id, app_version, platform, test_lab)
  values (me, left(coalesce(p_app_version, ''), 32), left(coalesce(p_platform, ''), 16), coalesce(p_test_lab, false))
  on conflict (user_id, app_version) do update
    set platform = excluded.platform,
        test_lab = d.test_lab or excluded.test_lab,
        created_at = now();
  if coalesce(p_test_lab, false) then
    update public.profiles set is_test = true where id = me and not is_test;
  end if;
end $$;

revoke all on function public.report_device_check(boolean, text, text) from public;
grant execute on function public.report_device_check(boolean, text, text) to authenticated;
