-- Contas de teste automático (#23; regra de produção #35, 0.34.5).
--
-- Regra de 0.34.5: todo jogador da loja vale. Só deixam de valer (profiles.is_test = true)
-- os robôs da revisão do Google e de pré-lançamento, reconhecidos pelo e-mail de login
-- (padrão nome.00000@gmail.com) ou pelo aparelho que disse firebase.test.lab
-- (device_checks.test_lab); a marca só soma, nunca substitui o padrão do e-mail.
-- As contas QA ficam fora desta regra e seguem a rotina manual do QA (is_test = false só
-- durante o teste, true no fim): o código não as toca.
--
-- * device_checks é o log de diagnóstico: uma linha por conta e versão, com o que o
--   aparelho disse (versão, plataforma e a marca firebase.test.lab). Nada do aparelho
--   (modelo, ids) é guardado.
-- * profiles.is_test é decidido pelo interruptor app_config.allowlist_enforced:
--   'true' (produção): is_test = is_robot_account(conta), exceto contas QA.
--   'false': nada é classificado; vale o comportamento da 0.32.0 (a marca
--   firebase.test.lab só marca, falso nunca desmarca). Para reverter, basta 'false'.
-- * allowed_players (lista da Play, exceções e QA) NÃO decide mais is_test. Brinde
--   (beta_eligible, em dobroes.sql) só usa a linha 'qa', junto com app_config.beta_gift_emails
--   (a conta do John); 'play' e 'manual' não dão mais brinde (#35, 25/10). A lista segue para
--   o gate da licença (dobroes_gate_licenca) e a conferência de instalação (push_check.sql).
--   ads.sql só usa 'qa' para a unidade de teste do anúncio.
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

-- ---------------------------------------------------------------- lista de testadores

-- Lista da Play, exceções manuais e contas QA (fonte: skill beta-testers). O repositório é
-- público: aqui vai só a estrutura, e as linhas são carregadas direto no banco.
-- source: 'play' (lista da Play), 'manual' (exceção, em geral pelo user_id) e 'qa'
-- (contas QA, fora da rotina automática de is_test). Quem decide brinde é o beta_eligible
-- de dobroes.sql (só 'qa' e beta_gift_emails); quem decide is_test é a regra de robôs abaixo.
-- Depois do lançamento da produção ninguém novo entra como 'play'.

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

-- interruptor da regra de is_test (#35): só o valor da app_config, sem exigir linha na lista
create or replace function public.allowlist_on()
returns boolean
language sql security definer set search_path = public stable as $$
  select coalesce((select value = 'true' from public.app_config where key = 'allowlist_enforced'), false);
$$;
revoke all on function public.allowlist_on() from public, anon, authenticated;

-- conta QA: allowed_players 'qa' por user_id ou e-mail de login (auth.users, nunca profiles.email)
create or replace function public.is_qa_account(p_user uuid)
returns boolean
language sql security definer set search_path = public stable as $$
  select exists (
           select 1 from public.allowed_players a
             left join auth.users u on u.id = p_user
            where a.source = 'qa' and (a.user_id = p_user or a.email = lower(trim(u.email))));
$$;
revoke all on function public.is_qa_account(uuid) from public, anon, authenticated;

-- robô da revisão do Google (#35): e-mail de login (auth.users, nunca profiles.email, que o
-- cliente grava) no padrão nome.00000@gmail.com. Sinal extra: o aparelho disse
-- firebase.test.lab (só soma; a marca não é garantida, por isso não substitui o padrão).
create or replace function public.is_robot_account(p_user uuid)
returns boolean
language sql security definer set search_path = public stable as $$
  select exists (
           select 1 from auth.users u
            where u.id = p_user
              and lower(trim(u.email)) ~ '^[a-z]+\.[0-9]{5}@gmail\.com$')
      or exists (
           select 1 from public.device_checks d
            where d.user_id = p_user and d.test_lab);
$$;
revoke all on function public.is_robot_account(uuid) from public, anon, authenticated;

-- a função da lista de 0.32.x deixou de decidir is_test (#35)
drop function if exists public.is_allowed_player(uuid);

-- aplica a regra a uma conta, só onde o valor muda; sem o interruptor, nada; conta QA, nunca
create or replace function public.refresh_is_test(p_user uuid)
returns void
language plpgsql security definer set search_path = public as $$
begin
  if not public.allowlist_on() then return; end if;
  if public.is_qa_account(p_user) then return; end if;
  update public.profiles p
     set is_test = public.is_robot_account(p.id)
   where p.id = p_user
     and p.is_test is distinct from public.is_robot_account(p.id);
end $$;
revoke all on function public.refresh_is_test(uuid) from public, anon, authenticated;

-- passada em todas as contas: p_apply = false só conta (ensaio), true aplica.
-- Devolve quantas viram teste, quantas voltam a valer e os totais depois. Conta QA fica
-- como está (não entra na passada).
create or replace function public.refresh_all_is_test(p_apply boolean default false)
returns table (to_test integer, to_player integer, players_after integer, tests_after integer)
language plpgsql security definer set search_path = public as $$
begin
  if not public.allowlist_on() then
    raise exception 'allowlist desligada (app_config.allowlist_enforced)';
  end if;
  return query
    with e as (
      select p.id, p.is_test as was,
             case when public.is_qa_account(p.id) then p.is_test
                  else public.is_robot_account(p.id) end as now_test
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

-- Com o interruptor ligado, a conta é reclassificada pela regra de robôs (test_lab entra como
-- sinal extra). Desligado, vale a 0.32.0: a marca firebase.test.lab só marca.
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
