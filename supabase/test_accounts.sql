-- Contas de teste automático (#23).
--
-- A cada envio à faixa fechada da Play, os testes automáticos da revisão do Google
-- entram no jogo com contas novas. O app chama report_device_check uma vez por conta
-- e versão, com a marca `firebase.test.lab` do aparelho (no iPhone vai sempre falso)
-- e a plataforma.
--
-- * device_checks é o log de diagnóstico: uma linha por conta e versão, com o que o
--   aparelho disse. Só versão, plataforma e a marca: nada do aparelho (modelo, ids)
--   é guardado.
-- * Quem decide profiles.is_test é a regra dos jogadores registrados (abaixo), quando
--   o interruptor allowlist_enforced está ligado. Desligado, vale o comportamento da
--   0.32.0: a marca firebase.test.lab só marca, falso nunca desmarca.
-- * O cliente não lê nem grava device_checks nem allowed_players direto (RLS ligado,
--   sem política).

create table if not exists public.device_checks (
  user_id     uuid        not null references auth.users(id) on delete cascade,
  app_version text        not null,
  platform    text        not null,
  test_lab    boolean     not null,
  created_at  timestamptz not null default now(),
  primary key (user_id, app_version)
);

alter table public.device_checks enable row level security;

-- ---------------------------------------------------------------- jogadores registrados

-- Decisão do John (12/10): só conta de testador registrado vale como jogador
-- (ranking, partida rápida, recompensas). Os robôs da revisão do Google entram com
-- Gmail comum e sem a marca firebase.test.lab, então a regra passou a ser a lista.
-- Vale quem (1) tem o e-mail de login na lista "Testadores fechados" da Play, ou
-- (2) já abriu o app no iPhone (o .ipa não passa pela revisão do Google), ou
-- (3) é exceção manual por perfil. Todo o resto fica is_test = true.
--
-- * allowed_players guarda a lista. O repositório é público: aqui vai só a
--   estrutura, e as linhas são carregadas direto no banco, fora do repo (skill
--   beta-testers). source: 'play' (lista da Play), 'manual' (exceção, em geral pelo
--   user_id) e 'qa' (contas QA: a linha espelha a lista da Play, mas não faz a conta
--   valer; QA01/QA02 seguem como conta de teste e pareiam entre si).
-- * Interruptor app_config.allowlist_enforced: com 'false' (padrão) nada muda.
--   allowlist_on() também exige ao menos uma linha 'play', para a regra nunca rodar
--   com a tabela vazia.
-- * Com a regra ligada a marca vale nos dois sentidos: quem entra na lista volta a
--   valer com os pontos que já tinha (a marca só filtra placar e bloqueia gravação
--   nova; pontos, XP, dobrões e milhas não são tocados aqui).
-- * Manutenção: depois de inserir ou remover alguém, `select * from
--   refresh_all_is_test(true)`. Não há gatilho em allowed_players de propósito, para
--   uma carga parcial nunca disparar a passada.

create table if not exists public.allowed_players (
  id         bigint      generated always as identity primary key,
  email      text        check (email = lower(trim(email))),
  user_id    uuid        references auth.users(id) on delete cascade,
  source     text        not null check (source in ('play', 'manual', 'qa')),
  note       text,
  created_at timestamptz not null default now(),
  check (email is not null or user_id is not null)
);

create unique index if not exists allowed_players_email_key on public.allowed_players (email) where email is not null;
create unique index if not exists allowed_players_user_key on public.allowed_players (user_id) where user_id is not null;

alter table public.allowed_players enable row level security;
revoke all on public.allowed_players from anon, authenticated;

insert into public.app_config (key, value) values ('allowlist_enforced', 'false')
on conflict (key) do nothing;

-- a regra só vale com o interruptor ligado e a lista da Play carregada
create or replace function public.allowlist_on()
returns boolean
language sql security definer set search_path = public stable as $$
  select coalesce((select value = 'true' from public.app_config where key = 'allowlist_enforced'), false)
     and exists (select 1 from public.allowed_players where source = 'play');
$$;
revoke all on function public.allowlist_on() from public, anon, authenticated;

-- vale como jogador: e-mail de login (auth.users, nunca profiles.email, que o
-- cliente grava) ou o perfil na lista ('play'/'manual'), ou já abriu no iPhone
create or replace function public.is_allowed_player(p_user uuid)
returns boolean
language sql security definer set search_path = public stable as $$
  select exists (
           select 1 from public.allowed_players a
           join auth.users u on u.id = p_user
           where a.source in ('play', 'manual')
             and a.email = lower(trim(u.email)))
      or exists (
           select 1 from public.allowed_players a
           where a.source in ('play', 'manual') and a.user_id = p_user)
      or exists (
           select 1 from public.device_checks d
           where d.user_id = p_user and lower(d.platform) = 'ios');
$$;
revoke all on function public.is_allowed_player(uuid) from public, anon, authenticated;

-- aplica a regra a uma conta, só onde o valor muda; sem a regra ligada, nada
create or replace function public.refresh_is_test(p_user uuid)
returns void
language plpgsql security definer set search_path = public as $$
begin
  if not public.allowlist_on() then return; end if;
  update public.profiles p
     set is_test = not public.is_allowed_player(p.id)
   where p.id = p_user
     and p.is_test is distinct from not public.is_allowed_player(p.id);
end $$;
revoke all on function public.refresh_is_test(uuid) from public, anon, authenticated;

-- passada em todas as contas: p_apply = false só conta (ensaio), true aplica.
-- Devolve quantas viram teste, quantas voltam a valer e os totais depois.
create or replace function public.refresh_all_is_test(p_apply boolean default false)
returns table (to_test integer, to_player integer, players_after integer, tests_after integer)
language plpgsql security definer set search_path = public as $$
begin
  if not public.allowlist_on() then
    raise exception 'allowlist desligada ou sem linha play em allowed_players';
  end if;
  return query
    with e as (
      select p.id, p.is_test as was, not public.is_allowed_player(p.id) as now_test
        from public.profiles p
    ), upd as (
      update public.profiles p set is_test = e.now_test
        from e
       where p_apply and e.id = p.id and p.is_test is distinct from e.now_test
      returning p.id
    )
    select (count(*) filter (where e.now_test and not e.was))::integer,
           (count(*) filter (where not e.now_test and e.was))::integer,
           (count(*) filter (where not e.now_test))::integer,
           (count(*) filter (where e.now_test))::integer
      from e;
end $$;
revoke all on function public.refresh_all_is_test(boolean) from public, anon, authenticated;

-- conta nova já nasce classificada (handle_new_user e insert direto do app);
-- security definer para passar pela trava guard_profile_is_test
create or replace function public.classify_new_profile()
returns trigger
language plpgsql security definer set search_path = public as $$
begin
  perform public.refresh_is_test(new.id);
  return null;
end $$;
revoke all on function public.classify_new_profile() from public, anon, authenticated;

drop trigger if exists profiles_classify_test on public.profiles;
create trigger profiles_classify_test
  after insert on public.profiles
  for each row execute function public.classify_new_profile();

-- ---------------------------------------------------------------- diagnóstico

-- Com a regra da lista ligada, test_lab fica só como diagnóstico e a conta é
-- reclassificada pela lista (o iPhone vira jogador aqui, no primeiro login).
-- Desligada, vale a 0.32.0: a marca firebase.test.lab só marca.
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
  if public.allowlist_on() then
    perform public.refresh_is_test(me);
  elsif coalesce(p_test_lab, false) then
    update public.profiles set is_test = true where id = me and not is_test;
  end if;
end $$;

revoke all on function public.report_device_check(boolean, text, text) from public;
grant execute on function public.report_device_check(boolean, text, text) to authenticated;
