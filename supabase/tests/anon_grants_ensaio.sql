-- Ensaio da #52: as 6 RPCs antigas não podem ser executáveis por anon.
-- Desfaz tudo (raise exception no fim). Esperado: "ENSAIO OK | anon tinha antes: <as 6>"
-- (depois de aplicada a migração: "ENSAIO OK | anon tinha antes: nenhuma").
do $$
declare f text; antes text := ''; bad text := '';
  fs text[] := array['public.daily_checkin()','public.claim_daily_challenge(text,integer)',
    'public.join_season(text)','public.claim_season_end()','public.buy_miles(integer)',
    'public.claim_feedback(uuid)'];
begin
  foreach f in array fs loop
    if has_function_privilege('anon', f, 'execute') then antes := antes || ' ' || f; end if;
  end loop;
  -- as mesmas 6 linhas da migração
  revoke all on function public.daily_checkin() from public, anon;
  revoke all on function public.claim_daily_challenge(text, integer) from public, anon;
  revoke all on function public.join_season(text) from public, anon;
  revoke all on function public.claim_season_end() from public, anon;
  revoke all on function public.buy_miles(integer) from public, anon;
  revoke all on function public.claim_feedback(uuid) from public, anon;
  foreach f in array fs loop
    if has_function_privilege('anon', f, 'execute') then bad := bad || ' anon:' || f; end if;
    if not has_function_privilege('authenticated', f, 'execute') then bad := bad || ' auth:' || f; end if;
    if not has_function_privilege('service_role', f, 'execute') then bad := bad || ' service:' || f; end if;
    if exists (select 1 from pg_proc p, aclexplode(p.proacl) a
                where p.oid = f::regprocedure and a.grantee = 0) then bad := bad || ' public:' || f; end if;
  end loop;
  -- prova real como anon (o SET LOCAL volta junto com a subtransação)
  begin
    set local role anon;
    perform public.daily_checkin();
    bad := bad || ' anon_chamou';
  exception when insufficient_privilege then null;
  end;
  reset role;
  raise exception 'ENSAIO % | anon tinha antes:%',
    case when bad = '' then 'OK' else 'FALHOU:' || bad end, coalesce(nullif(antes, ''), ' nenhuma');
end $$;
