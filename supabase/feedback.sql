-- Naval Battle · feedback, badges e fila de beta testers
-- Rode este script uma vez no SQL Editor do projeto, depois de schema.sql e online.sql.

-- ---------------------------------------------------------------- feedback

-- Bug ou sugestão enviado pelo comandante. Avaliação é sempre manual (SQL Editor,
-- por enquanto): ninguém além de quem tem acesso direto ao banco muda `status`,
-- `reward_credits` ou `reward_ability` — não existe policy de update para
-- `authenticated`, só a leitura da própria linha e o insert do próprio feedback.
create table if not exists public.feedback (
  id             uuid primary key default gen_random_uuid(),
  user_id        uuid not null references auth.users(id) on delete cascade,
  username       text not null,
  kind           text not null check (kind in ('bug', 'improvement')),
  message        text not null,
  status         text not null default 'pending' check (status in ('pending', 'approved', 'rejected')),
  -- preenchidos só ao aprovar: crédito na loja (bug) OU carga de habilidade (melhoria) —
  -- nunca os dois ao mesmo tempo, mas o app não impõe isso, é convenção de quem revisa
  reward_credits integer not null default 0,
  reward_ability text,
  -- true depois que o app já aplicou a recompensa localmente e confirmou — evita
  -- conceder de novo se o comandante abrir o app em outro aparelho
  claimed        boolean not null default false,
  created_at     timestamptz not null default now(),
  reviewed_at    timestamptz
);

alter table public.feedback enable row level security;

drop policy if exists "feedback: enviar" on public.feedback;
create policy "feedback: enviar"
  on public.feedback for insert
  with check (auth.uid() = user_id);

drop policy if exists "feedback: ler o proprio" on public.feedback;
create policy "feedback: ler o proprio"
  on public.feedback for select
  using (auth.uid() = user_id);

-- só troca `claimed`, nunca `status`/`reward_*` — por isso é função, não policy de
-- update direta na tabela (que deixaria o próprio comandante mexer na recompensa)
create or replace function public.claim_feedback(p_feedback_id uuid)
returns void language plpgsql security definer set search_path = public as $$
begin
  update public.feedback
  set claimed = true
  where id = p_feedback_id and user_id = auth.uid()
    and status in ('approved', 'rejected') and claimed = false;
end $$;

revoke all on function public.claim_feedback(uuid) from public;
grant execute on function public.claim_feedback(uuid) to authenticated;

-- ---------------------------------------------------------------- badges

-- Conquistas permanentes — diferente das medalhas de fim de partida (calculadas
-- na hora, nunca gravadas). Só o servidor grava aqui: sem policy de insert para
-- `authenticated`, os badges só nascem pelos gatilhos abaixo.
create table if not exists public.user_badges (
  user_id    uuid not null references auth.users(id) on delete cascade,
  badge_code text not null,
  earned_at  timestamptz not null default now(),
  primary key (user_id, badge_code)
);

alter table public.user_badges enable row level security;

drop policy if exists "badges: ler os proprios" on public.user_badges;
create policy "badges: ler os proprios"
  on public.user_badges for select
  using (auth.uid() = user_id);

-- feedback aprovado sempre rende o badge de colaborador, além da recompensa em si
create or replace function public.grant_feedback_badge()
returns trigger language plpgsql security definer set search_path = public as $$
begin
  if new.status = 'approved' and coalesce(old.status, '') is distinct from 'approved' then
    insert into public.user_badges (user_id, badge_code)
    values (new.user_id, 'feedback_contributor')
    on conflict do nothing;
  end if;
  return new;
end $$;

drop trigger if exists feedback_grant_badge on public.feedback;
create trigger feedback_grant_badge
  after update on public.feedback
  for each row execute function public.grant_feedback_badge();

-- ---------------------------------------------------------------- beta testers

-- Fila alimentada pelo formulário do site — sem autenticação nenhuma, só um
-- e-mail. Nada além do INSERT é público: a fila em si só é lida via SQL Editor
-- (não existe policy de select para `anon` nem para `authenticated`).
create table if not exists public.beta_testers (
  id         uuid primary key default gen_random_uuid(),
  email      text not null unique,
  status     text not null default 'pending' check (status in ('pending', 'invited', 'active')),
  created_at timestamptz not null default now(),
  invited_at timestamptz
);

alter table public.beta_testers enable row level security;

drop policy if exists "beta: cadastrar" on public.beta_testers;
create policy "beta: cadastrar"
  on public.beta_testers for insert
  to anon
  with check (true);

-- quando a conta do jogo nasce (ou troca de e-mail) com um e-mail que já está na
-- fila de beta, o badge de beta tester entra sozinho — sem precisar cruzar nada
-- manualmente depois de convidar alguém no Play Console
create or replace function public.grant_beta_badge()
returns trigger language plpgsql security definer set search_path = public as $$
begin
  if new.email is not null and exists (
    select 1 from public.beta_testers where email = new.email
  ) then
    insert into public.user_badges (user_id, badge_code)
    values (new.id, 'beta_tester')
    on conflict do nothing;
  end if;
  return new;
end $$;

drop trigger if exists profiles_grant_beta_badge on public.profiles;
create trigger profiles_grant_beta_badge
  after insert or update of email on public.profiles
  for each row execute function public.grant_beta_badge();
