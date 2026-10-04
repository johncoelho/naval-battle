-- Fila de beta testers: o formulário "Quero testar" do site (site/index.html) grava
-- aqui pela chave anônima. A tabela não tem nenhuma policy — ninguém lê nem grava
-- direto; o site só chama join_beta_waitlist, que valida o e-mail e não duplica.
-- O Google não tem API para adicionar testadores ao teste fechado: os e-mails
-- pendentes são levados à mão para a lista do Play Console (ver docs/BUILD.md) e
-- marcados como 'invited'.

create table if not exists public.beta_testers (
  id         uuid primary key default gen_random_uuid(),
  email      text not null unique,
  platform   text not null default 'android' check (platform in ('android', 'ios')),
  status     text not null default 'pending' check (status in ('pending', 'invited', 'active')),
  created_at timestamptz not null default now(),
  invited_at timestamptz
);

alter table public.beta_testers enable row level security;

-- devolve 'ok' (entrou na fila), 'exists' (já estava) ou 'invalid' (e-mail ruim)
create or replace function public.join_beta_waitlist(p_email text, p_platform text default 'android')
returns text
language plpgsql security definer set search_path = public as $$
declare
  e text := lower(trim(coalesce(p_email, '')));
  plat text := case when p_platform = 'ios' then 'ios' else 'android' end;
begin
  if length(e) > 254 or e !~ '^[^@\s]+@[^@\s]+\.[^@\s]+$' then
    return 'invalid';
  end if;
  insert into public.beta_testers (email, platform) values (e, plat)
  on conflict (email) do nothing;
  if found then
    return 'ok';
  end if;
  return 'exists';
end $$;

revoke all on function public.join_beta_waitlist(text, text) from public;
grant execute on function public.join_beta_waitlist(text, text) to anon, authenticated;
