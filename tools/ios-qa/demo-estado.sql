-- Prepara (ou so confere) o estado da conta de demonstracao da Apple antes do ensaio de revisao (#118, skill apple-review).
-- Quem roda e a QA/Release, a mao, com acesso de escrita ao banco; o workflow NAO roda isto (nao ha service_role no CI).
-- Segue a skill supabase-change: bloco que DESFAZ por padrao (dry run) e mostra o que faria; so grava com aplicar := true.
--
-- Estados (e_estado):
--   'verificar' : so le e imprime o estado atual (use na conferencia final, antes do envio).
--   'sem-saldo' : saldo ABAIXO do preco de entrada do Passe e sem passe na temporada  -> ensaio estado=sem-saldo
--   'com-saldo' : saldo >= preco de entrada e sem passe                                  -> ensaio estado=com-saldo
--   'entrega'   : estado enviado a Apple: saldo suficiente, sem passe, beta_eligible, milhas baixas (so confere o
--                 beta_eligible; nao muda nada alem do saldo, do passe e das milhas)
-- Nunca rodar o ensaio depois do envio: o ensaio 'com-saldo' gasta o saldo e cria o passe. Depois dele, voltar ao 'entrega'.
--
-- Uso: troque e_email (o e-mail da conta de demonstracao, o mesmo do secret IOS_REVIEW_EMAIL; nunca commite o valor real),
-- escolha e_estado e rode. Primeiro com aplicar := false (resultado vem no erro de rollback), depois com true.
-- Tabelas e funcoes (conferidas em supabase/): profiles.credits / nautical_miles / miles_day (schema.sql, economy.sql),
-- season_passes + current_season() + config_int('season_pass_price') (season.sql), dobroes_has_wallet / dobroes_apply
-- (dobroes_saldo.sql: o saldo so muda por RPC, com razao), beta_eligible (dobroes.sql).
do $$
declare
  e_email   text    := 'DEMO_EMAIL';      -- trocar
  e_estado  text    := 'verificar';       -- verificar | sem-saldo | com-saldo | entrega
  aplicar   boolean := false;             -- false = desfaz tudo no fim (dry run)
  uid uuid;
  szn text;
  entry integer;
  cur integer;
  alvo integer;
  passe text;
  miles integer;
  elegivel boolean;
  resumo text;
begin
  if e_estado not in ('verificar', 'sem-saldo', 'com-saldo', 'entrega') then
    raise exception 'e_estado invalido: %', e_estado;
  end if;
  select id into uid from auth.users where lower(email) = lower(trim(e_email));
  if uid is null then
    raise exception 'conta de demonstracao nao encontrada (troque e_email)';
  end if;
  select c.season_key into szn from public.current_season() c;
  entry := public.config_int('season_pass_price', 1200);

  if e_estado <> 'verificar' then
    if not public.dobroes_has_wallet(uid) then
      raise exception 'a conta nao tem carteira no servidor (dobroes_wallets): o saldo nao seria o do servidor';
    end if;
    -- sempre sem passe nesta temporada
    delete from public.season_passes where user_id = uid and season_key = szn;
    select credits into cur from public.profiles where id = uid;
    alvo := case e_estado
              when 'sem-saldo' then greatest(entry - 1, 0)
              when 'com-saldo' then entry + 500
              else entry * 2 end;
    if alvo <> cur then
      perform public.dobroes_apply(uid, alvo - cur, 'demo_ajuste', e_estado || ':' || extract(epoch from clock_timestamp())::bigint);
    end if;
    if e_estado = 'entrega' then
      update public.profiles set nautical_miles = 2, miles_day = public.brt_today() where id = uid;
    end if;
  end if;

  select credits, nautical_miles into cur, miles from public.profiles where id = uid;
  select p.tier into passe from public.season_passes p where p.user_id = uid and p.season_key = szn;
  elegivel := public.beta_eligible(uid);
  resumo := format('temporada=%s preco_de_entrada=%s saldo=%s passe=%s milhas=%s beta_eligible=%s',
                   szn, entry, cur, coalesce(passe, 'nenhum'), miles, elegivel);

  if e_estado = 'entrega' and (not elegivel or passe is not null or cur < entry) then
    raise exception 'estado de entrega NAO conferido (%). Corrija antes do envio', resumo;
  end if;
  if e_estado = 'sem-saldo' and (passe is not null or cur >= entry) then
    raise exception 'estado sem-saldo NAO conferido (%)', resumo;
  end if;
  if e_estado = 'com-saldo' and (passe is not null or cur < entry) then
    raise exception 'estado com-saldo NAO conferido (%)', resumo;
  end if;

  if aplicar and e_estado <> 'verificar' then
    raise notice 'APLICADO: %', resumo;   -- o bloco termina normalmente: commit
  else
    raise exception 'RESULTADO (rollback, nada gravado): %', resumo;
  end if;
end $$;
