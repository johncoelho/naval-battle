---
name: beta-testers
description: Gestão dos testadores do teste fechado do Naval Battle — lista "Testadores fechados" da Play, lista de jogadores registrados no banco (allowed_players), contagem para produção. Use quando o John perguntar por testadores novos, pedir para liberar ou remover alguém, ou quiser saber quantos estão inscritos.
---

# Testadores do teste fechado

Fluxo: quem quer testar no Android entra pelo link oficial do teste fechado
(`play.google.com/apps/testing/aigamesfactory.navalbattleclassic`), que só funciona para e-mails
já na lista **"Testadores fechados"** da Play. Não há API do Google para essa lista: **eu** coloco o
e-mail lá à mão quando o John pedir, e espelho no banco em `allowed_players` (só testador
registrado vale como jogador, #23). A antiga fila do site (`beta_testers`) não existe mais.

**O repositório é público: nenhum e-mail de testador em arquivo, commit, issue ou log.** Os
comandos abaixo usam o marcador `<email>`; o e-mail de verdade vai só no SQL rodado no banco.

## Liberar na Play (Chrome do John, já logado)

1. Play Console → Naval Battle Classic → Testing → Closed testing → **Alpha** → aba **Testers**
   (`…/tracks/4699826548171185940?tab=testers`).
2. Seta da lista **"Testadores fechados"** → campo "Add email addresses": clicar pelo `ref` do campo
   (`find`), digitar o e-mail, Enter, conferir que entrou na lista, **Save changes** → confirmar Save.
3. Conferir na página que salvou ("Your changes have been saved") e fechar a aba.
4. Espelhar no banco (passo abaixo).

Remover alguém: mesma lista, lixeira da linha, salvar; depois o passo abaixo.

## Manter `allowed_players` (ao adicionar ou remover alguém na lista da Play)

Toda mudança na lista da Play acompanha no banco, no mesmo dia. Só SQL rodado direto no banco
(`execute_sql`), nunca arquivo do repositório.

```sql
-- adicionar testador da Play
insert into public.allowed_players (email, source) values (lower(trim('<email>')), 'play');
-- remover
delete from public.allowed_players where email = lower(trim('<email>'));
-- depois de qualquer mudança: reaplicar a regra a todas as contas
select * from public.refresh_all_is_test(true);
```

- Conta QA (`aigamesfactory.qa01/qa02`) entra com `source = 'qa'`: espelha a lista da Play, mas a
  conta segue como conta de teste.
- **Jogador de iPhone** fora da lista não precisa de nada: vira jogador sozinho ao abrir o app
  (`report_device_check` com `ios`).
- **Exceção por perfil** (decisão do John; ex.: e-mail da Play diferente do de login, iPhone com
  versão antiga): `insert into public.allowed_players (user_id, source, note) select id, 'manual',
  '<motivo>' from public.profiles where username = '<nome>';` (conferir que achou exatamente 1
  perfil) e o `refresh_all_is_test(true)`.
- Conferir: `select source, count(*) from public.allowed_players group by 1;` e o ensaio
  `select * from public.refresh_all_is_test(false);` (só conta, não muda nada).
- A regra só vale com `app_config.allowlist_enforced = 'true'` e ao menos uma linha `play`;
  desligado, `refresh_all_is_test` recusa rodar.

## Contagem para produção

- Dashboard do app no Play Console → card "Apply for access to production": os três requisitos
  (versão no teste fechado, **12 inscritos**, 14 dias seguidos). Ler o texto da página.
- Para saber **quem** ainda não entrou: cruzar `allowed_players` (`source = 'play'`) com
  `auth.users` (quem entrou e abriu o jogo tem conta). Responder só com nomes de perfil e contagens.
- As contas QA (`aigamesfactory.qa01/qa02`) estão na lista para testes, mas **não contam** como
  testadores reais.

## Responder ao John

Quem entrou desde a última vez, quem está pendente, o total inscrito e o que falta para a produção.
