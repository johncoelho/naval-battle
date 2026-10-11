-- Naval Battle · base do jogo no Supabase
-- Rode este script uma vez no SQL Editor do projeto.
--
-- A carreira de cada comandante vive em uma linha de `profiles`, presa ao usuário
-- do Supabase Auth. As políticas garantem que ninguém leia ou escreva a carreira
-- de outro — a chave anônima do aplicativo é pública, quem protege é o RLS.

create table if not exists public.profiles (
  id           uuid primary key references auth.users(id) on delete cascade,
  email        text,
  username     text unique,
  insignia     text        not null default 'anc',

  xp           integer     not null default 0,
  credits      integer     not null default 500,
  matches      integer     not null default 0,
  wins         integer     not null default 0,
  shots        integer     not null default 0,
  hits         integer     not null default 0,
  sunk         integer     not null default 0,
  streak       integer     not null default 0,
  best_streak  integer     not null default 0,

  -- listas separadas por vírgula: o aplicativo grava e lê no mesmo formato
  owned        text        not null default 'std,br',   -- camuflagens conquistadas
  equipped     text        not null default 'br',       -- camuflagem em uso
  fleets       text        not null default 'std',      -- linhas de casco conquistadas
  fleet        text        not null default 'std',      -- linha de casco em uso

  updated_at   timestamptz not null default now(),
  created_at   timestamptz not null default now()
);

-- retrato e idioma acompanham a carreira: tudo que o comandante escolhe volta
-- igual em qualquer aparelho, sem botão de sincronizar
alter table public.profiles add column if not exists avatar text not null default 'om1';
alter table public.profiles add column if not exists lang_code text not null default '';

alter table public.profiles enable row level security;

drop policy if exists "carreira propria: ler" on public.profiles;
create policy "carreira propria: ler"
  on public.profiles for select
  using (auth.uid() = id);

drop policy if exists "carreira propria: criar" on public.profiles;
create policy "carreira propria: criar"
  on public.profiles for insert
  with check (auth.uid() = id);

drop policy if exists "carreira propria: atualizar" on public.profiles;
create policy "carreira propria: atualizar"
  on public.profiles for update
  using (auth.uid() = id)
  with check (auth.uid() = id);

-- carimbo de hora a cada gravação
create or replace function public.touch_profile()
returns trigger language plpgsql as $$
begin
  new.updated_at = now();
  return new;
end $$;

drop trigger if exists profiles_touch on public.profiles;
create trigger profiles_touch
  before update on public.profiles
  for each row execute function public.touch_profile();

-- conta de teste (#23): os testes automáticos da revisão do Google criam contas a
-- cada envio à Play. Marcadas aqui, ficam fora do ranking, da recompensa de
-- temporada e da partida rápida com jogador real (ver test_accounts.sql, online.sql,
-- quick_offer.sql e season.sql). A marca só vem do servidor.
alter table public.profiles add column if not exists is_test boolean not null default false;

-- profiles só deixa ler a própria linha: as duas funções abrem só o booleano
-- (anon também, porque a política de leitura das salas chama as duas)
create or replace function public.is_test_user(p_user uuid)
returns boolean
language sql security definer set search_path = public stable as $$
  select coalesce((select p.is_test from public.profiles p where p.id = p_user), false);
$$;
revoke all on function public.is_test_user(uuid) from public;
grant execute on function public.is_test_user(uuid) to anon, authenticated;

create or replace function public.my_is_test()
returns boolean
language sql security definer set search_path = public stable as $$
  select public.is_test_user(auth.uid());
$$;
revoke all on function public.my_is_test() from public;
grant execute on function public.my_is_test() to anon, authenticated;

-- trava: o cliente (gravação direta como anon/authenticated) não marca nem desmarca
-- a conta de teste; dentro das RPCs security definer o current_user é o dono e passa
create or replace function public.guard_profile_is_test()
returns trigger language plpgsql as $$
begin
  if current_user in ('anon', 'authenticated') then
    if tg_op = 'INSERT' then
      new.is_test := false;
    else
      new.is_test := coalesce(old.is_test, false);
    end if;
  end if;
  return new;
end $$;

drop trigger if exists profiles_guard_is_test on public.profiles;
create trigger profiles_guard_is_test
  before insert or update on public.profiles
  for each row execute function public.guard_profile_is_test();

-- cria a linha da carreira assim que a conta nasce, inclusive no login social,
-- para o Google cair na mesma carreira do e-mail
create or replace function public.handle_new_user()
returns trigger language plpgsql security definer set search_path = public as $$
declare
  wanted text := coalesce(
    new.raw_user_meta_data->>'username',
    new.raw_user_meta_data->>'full_name',
    split_part(coalesce(new.email, 'comandante'), '@', 1)
  );
begin
  begin
    insert into public.profiles (id, email, username)
    values (new.id, new.email, wanted)
    on conflict (id) do nothing;
  exception when unique_violation then
    -- nome de usuário já tomado: entra com sufixo em vez de barrar o cadastro
    insert into public.profiles (id, email, username)
    values (new.id, new.email, wanted || '-' || substr(new.id::text, 1, 4))
    on conflict (id) do nothing;
  end;
  return new;
end $$;

drop trigger if exists on_auth_user_created on auth.users;
create trigger on_auth_user_created
  after insert on auth.users
  for each row execute function public.handle_new_user();

-- Placar público (para a ranqueada da próxima fase): só leitura, sem e-mail.
create or replace view public.leaderboard as
  select username, xp, wins, matches, best_streak
  from public.profiles
  order by xp desc
  limit 100;
