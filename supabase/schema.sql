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

-- cria a linha da carreira assim que a conta nasce, inclusive no login social,
-- para o Google cair na mesma carreira do e-mail
create or replace function public.handle_new_user()
returns trigger language plpgsql security definer set search_path = public as $$
begin
  insert into public.profiles (id, email, username)
  values (
    new.id,
    new.email,
    coalesce(
      new.raw_user_meta_data->>'username',
      new.raw_user_meta_data->>'full_name',
      split_part(coalesce(new.email, 'comandante'), '@', 1)
    )
  )
  on conflict (id) do nothing;
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
