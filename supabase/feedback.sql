-- Naval Battle · feedback bonificado e badges
-- Rode este script no SQL Editor do projeto, depois de schema.sql e online.sql.
-- Idempotente: rodar de novo não quebra nada nem duplica badges.

-- ---------------------------------------------------------------- configuração

-- Chave/valor lidos só pelas funções do servidor. `beta_ends_at` vazio = o jogo
-- ainda está em teste fechado; preenchido no dia da publicação em produção, e
-- contas novas deixam de ganhar o badge de Beta Tester.
create table if not exists public.app_config (
  key   text primary key,
  value text
);
alter table public.app_config enable row level security;
insert into public.app_config (key, value) values ('beta_ends_at', null)
on conflict (key) do nothing;

-- ---------------------------------------------------------------- feedback

-- Bug ou sugestão enviado pelo comandante. Entra só por `submit_feedback`, é
-- avaliado só por `review_feedback` (SQL Editor) e reivindicado só por
-- `claim_feedback` — a tabela em si não tem policy de insert nem de update.
create table if not exists public.feedback (
  id             uuid primary key default gen_random_uuid(),
  user_id        uuid not null references auth.users(id) on delete cascade,
  username       text not null,
  kind           text not null check (kind in ('bug', 'improvement')),
  message        text not null,
  status         text not null default 'pending' check (status in ('pending', 'approved', 'rejected')),
  reward_credits integer not null default 0,
  reward_ability text,
  claimed        boolean not null default false,
  created_at     timestamptz not null default now(),
  reviewed_at    timestamptz
);

alter table public.feedback add column if not exists reward_charges integer not null default 0;
alter table public.feedback add column if not exists severity text;
alter table public.feedback add column if not exists review_note text;
alter table public.feedback add column if not exists app_version text;
alter table public.feedback add column if not exists platform text;
alter table public.feedback add column if not exists lang text;

-- 20 a 800 caracteres; `not valid` não reprova linhas antigas, só as novas
do $$ begin
  alter table public.feedback add constraint feedback_message_len
    check (char_length(message) between 20 and 800) not valid;
exception when duplicate_object then null;
end $$;

alter table public.feedback enable row level security;

-- insert direto saiu: passa por submit_feedback, que impõe o limite de pendentes
drop policy if exists "feedback: enviar" on public.feedback;

drop policy if exists "feedback: ler o proprio" on public.feedback;
create policy "feedback: ler o proprio"
  on public.feedback for select
  using (auth.uid() = user_id);

create or replace function public.submit_feedback(
  p_kind text, p_message text, p_app_version text, p_platform text, p_lang text
)
returns void language plpgsql security definer set search_path = public as $$
declare
  pending integer;
  name text;
begin
  if auth.uid() is null then
    raise exception 'feedback_auth';
  end if;
  if char_length(coalesce(p_message, '')) < 20 or char_length(p_message) > 800 then
    raise exception 'feedback_length';
  end if;
  select count(*) into pending from public.feedback
    where user_id = auth.uid() and status = 'pending';
  if pending >= 5 then
    raise exception 'feedback_limit';
  end if;
  select coalesce(username, 'Comandante') into name from public.profiles where id = auth.uid();
  insert into public.feedback (user_id, username, kind, message, app_version, platform, lang)
  values (auth.uid(), coalesce(name, 'Comandante'), p_kind, p_message, p_app_version, p_platform, p_lang);
end $$;

revoke all on function public.submit_feedback(text, text, text, text, text) from public;
grant execute on function public.submit_feedback(text, text, text, text, text) to authenticated;

-- Reivindica UMA vez e devolve a recompensa: o app só aplica o que voltou daqui,
-- então outro aparelho (ou o mesmo, depois de travar) nunca recebe de novo.
drop function if exists public.claim_feedback(uuid);
create function public.claim_feedback(p_feedback_id uuid)
returns table (
  id uuid, kind text, status text, reward_credits integer,
  reward_ability text, reward_charges integer, review_note text
)
language plpgsql security definer set search_path = public as $$
begin
  return query
  update public.feedback f
     set claimed = true
   where f.id = p_feedback_id and f.user_id = auth.uid()
     and f.status in ('approved', 'rejected') and f.claimed = false
  returning f.id, f.kind, f.status, f.reward_credits, f.reward_ability, f.reward_charges, f.review_note;
end $$;

revoke all on function public.claim_feedback(uuid) from public;
grant execute on function public.claim_feedback(uuid) to authenticated;

-- Avaliação pelo administrador (só pelo SQL Editor — sem grant para o app).
-- Bug: p_severity 'minor' 150 · 'medium' 300 · 'major' 600 créditos.
-- Melhoria: p_severity 'small' 1 · 'large' 3 cargas de p_ability — código do
-- Ability em game/Model.kt: 'REC' (Reconhecimento), '2X' (Barragem dupla),
-- 'SNR' (Sonar), 'FUM' (Cortina de fumaça).
-- Exemplos:
--   select review_feedback('<id>', true, 'medium', null, null);
--   select review_feedback('<id>', true, 'small', 'SNR', 'Boa ideia!');
--   select review_feedback('<id>', false, null, null, 'Duplicado — já estava na lista');
create or replace function public.review_feedback(
  p_id uuid, p_approve boolean, p_severity text, p_ability text, p_note text
)
returns void language plpgsql security definer set search_path = public as $$
declare
  k text;
  credits integer := 0;
  charges integer := 0;
begin
  select kind into k from public.feedback where id = p_id;
  if k is null then
    raise exception 'feedback % não existe', p_id;
  end if;

  if p_approve then
    if k = 'bug' then
      credits := case p_severity when 'minor' then 150 when 'medium' then 300 when 'major' then 600 end;
      if credits is null then
        raise exception 'bug pede severidade minor, medium ou major';
      end if;
    else
      charges := case p_severity when 'small' then 1 when 'large' then 3 end;
      if charges is null or p_ability is null or p_ability not in ('REC', '2X', 'SNR', 'FUM') then
        raise exception 'melhoria pede severidade small ou large e o código da habilidade';
      end if;
    end if;
  end if;

  update public.feedback
     set status = case when p_approve then 'approved' else 'rejected' end,
         severity = p_severity,
         reward_credits = credits,
         reward_ability = case when p_approve and k = 'improvement' then p_ability end,
         reward_charges = charges,
         review_note = p_note,
         reviewed_at = now()
   where id = p_id;
end $$;

revoke all on function public.review_feedback(uuid, boolean, text, text, text) from public, anon, authenticated;

-- ---------------------------------------------------------------- badges

-- Conquistas permanentes — só o servidor grava aqui (sem policy de insert).
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

-- feedback aprovado rende o badge de colaborador, além da recompensa em si
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

-- Beta Tester = conta criada enquanto o jogo está em teste fechado (o Google não
-- expõe a lista de testadores por API, então o critério é a data, não o e-mail)
create or replace function public.in_beta()
returns boolean language sql stable security definer set search_path = public as $$
  select coalesce(
    (select value::timestamptz > now() from public.app_config where key = 'beta_ends_at' and value is not null),
    true
  );
$$;

create or replace function public.grant_beta_badge()
returns trigger language plpgsql security definer set search_path = public as $$
begin
  if public.in_beta() then
    insert into public.user_badges (user_id, badge_code)
    values (new.id, 'beta_tester')
    on conflict do nothing;
  end if;
  return new;
end $$;

drop trigger if exists profiles_grant_beta_badge on public.profiles;
create trigger profiles_grant_beta_badge
  after insert on public.profiles
  for each row execute function public.grant_beta_badge();

-- carga inicial: quem já tem conta durante o beta já é Beta Tester
insert into public.user_badges (user_id, badge_code)
select p.id, 'beta_tester' from public.profiles p
where public.in_beta()
on conflict do nothing;

-- ---------------------------------------------------------------- fila antiga

-- O cadastro de testadores agora é só o link oficial do Play Console; a fila
-- própria que o site alimentava saiu de vez.
drop table if exists public.beta_testers cascade;
