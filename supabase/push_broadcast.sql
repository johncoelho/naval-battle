-- Push genérico (complementa supabase/push.sql).
--
-- * Todo aparelho Android com o jogo instalado registra o token, com ou sem conta
--   (user_id nulo = aparelho anônimo). Entrar na conta liga o token a ela; sair
--   devolve o token ao anonimato em vez de apagar, para avisos gerais seguirem.
-- * queue_push com p_user nulo = aviso para TODOS os aparelhos (a Edge Function
--   `push` manda para todos os tokens quando a linha da fila não tem user_id).
-- * push_broadcast(título, texto): aviso geral manual (novidades, eventos, qualquer
--   assunto) — só pelo SQL do painel, nunca pelo app.
-- * Versão nova: marcar app_releases.notify = true numa versão já publicada no
--   Android manda "Versão X disponível" para todos. Não sai sozinho a cada correção
--   pequena — liga só quando a versão merece aviso.

alter table public.push_tokens alter column user_id drop not null;
alter table public.push_outbox alter column user_id drop not null;
alter table public.app_releases add column if not exists notify boolean not null default false;

create or replace function public.register_push_token(p_token text, p_platform text default 'android')
returns table (ok boolean)
language plpgsql security definer set search_path = public as $$
begin
  if coalesce(length(p_token), 0) < 20 then
    return query select false;
    return;
  end if;
  insert into public.push_tokens (token, user_id, platform, updated_at)
  values (p_token, auth.uid(), coalesce(p_platform, 'android'), now())
  on conflict (token) do update
    set user_id = excluded.user_id, platform = excluded.platform, updated_at = now();
  return query select true;
end $$;
grant execute on function public.register_push_token(text, text) to anon, authenticated;

-- sair da conta: o aparelho continua recebendo avisos gerais, não os da conta
create or replace function public.unregister_push_token(p_token text)
returns table (ok boolean)
language plpgsql security definer set search_path = public as $$
begin
  update public.push_tokens set user_id = null, updated_at = now()
  where token = p_token and user_id = auth.uid();
  return query select true;
end $$;
grant execute on function public.unregister_push_token(text) to authenticated;

create or replace function public.queue_push(p_user uuid, p_kind text, p_title text, p_body text)
returns void
language plpgsql security definer set search_path = public, extensions as $$
declare
  v_id bigint;
begin
  if p_user is null then
    if not exists (select 1 from public.push_tokens) then return; end if;
  elsif not exists (select 1 from public.push_tokens where user_id = p_user) then
    return;
  end if;
  insert into public.push_outbox (user_id, kind, title, body)
  values (p_user, p_kind, p_title, p_body)
  returning id into v_id;
  perform net.http_post(
    url     := 'https://cwtslesnthbenxswdcbv.supabase.co/functions/v1/push',
    body    := jsonb_build_object('id', v_id),
    headers := jsonb_build_object('Content-Type', 'application/json'),
    timeout_milliseconds := 30000
  );
exception when others then
  null;
end $$;
revoke all on function public.queue_push(uuid, text, text, text) from public, anon, authenticated;

create or replace function public.push_broadcast(p_title text, p_body text, p_kind text default 'announcement')
returns void
language sql security definer set search_path = public as $$
  select public.queue_push(null, p_kind, p_title, p_body);
$$;
revoke all on function public.push_broadcast(text, text, text) from public, anon, authenticated;

create or replace function public.push_on_release()
returns trigger
language plpgsql security definer set search_path = public as $$
begin
  if new.notify and new.android_live
     and (tg_op = 'INSERT' or not (old.notify and old.android_live)) then
    perform public.queue_push(null, 'new_version', 'Versão ' || new.version_name || ' disponível',
      left(new.notes_pt, 170) || case when length(new.notes_pt) > 170 then '…' else '' end);
  end if;
  return new;
end $$;

drop trigger if exists push_on_release on public.app_releases;
create trigger push_on_release
  after insert or update of notify, android_live on public.app_releases
  for each row execute function public.push_on_release();
