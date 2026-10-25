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

## Teste de licença (compras sem cobrança)

Play Console → Configurações → **Teste de licença** usa só a lista **"Licenca QA"** (QA01 e QA02),
decisão do John em 18/10: todo beta tester paga de verdade nos pacotes pagos; o prêmio diário grátis
é resgatado no servidor (não passa pela Play). Testador novo **não** entra nessa lista.

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

- **Depois do lançamento da produção (0.34.5, #35), ninguém novo entra como `'play'`.** Decisão do
  John (HISTORY, 25/10): a lista não dá mais brinde. Prêmio Beta, vitrine beta e qualquer grátis valem
  só para QA01, QA02 (`'qa'`) e a conta do John (`app_config.beta_gift_emails`, e-mail só no banco).
  Testador antigo fica com o que já ganhou (saldo, itens, medalha Beta Tester) e paga como todo mundo.
  Quem vale como jogador é todo mundo que não é robô; a lista não muda isso.
- A passada `refresh_all_is_test(true)` só muda robô e conta QA fica de fora; testador novo não exige
  passada. Rodada em 25/10 (0 mudanças); rodar de novo só com ensaio antes.
- Conta QA (`aigamesfactory.qa01/qa02`) entra com `source = 'qa'`: espelha a lista da Play, mas a
  conta segue como conta de teste.
- **Jogador de iPhone** fora da lista não precisa de nada: vira jogador sozinho ao abrir o app
  (`report_device_check` com `ios`).
- **Exceção por perfil (`'manual'`)**: era da regra antiga (0.32.1). Desde 0.34.5 uma linha
  `'manual'` não muda `is_test` nem dá brinde; não criar nova. Brinde para mais alguém só com o OK do
  John, pondo o e-mail em `app_config.beta_gift_emails` direto no banco (nunca no repositório).
- Conferir: `select source, count(*) from public.allowed_players group by 1;` e o ensaio
  `select * from public.refresh_all_is_test(false);` (só conta, não muda nada).
- A regra de `is_test` só vale com `app_config.allowlist_enforced = 'true'` (interruptor, sem exigir
  linha `play`); desligado, `refresh_all_is_test` recusa rodar.

## Prêmio Beta pela Play (legado, apps 0.33.0 a 0.33.2)

Só o app antigo passava o Prêmio pela Play; quem não estava no Teste de licença era cobrado de
verdade. Desde 0.33.3 o Prêmio é resgatado no servidor, e desde 0.34.5 (#35) só QA e a conta do John
o recebem. Antes de mexer na lista de licença, conferir `select * from public.dobroes_gate_licenca();`
(testadores ainda sem o 0.33.3).

## Conferir instalação (#26)

Hoje a conferência é manual. A versão diária (`push_check_daily()`) ainda não roda sozinha: falta o
John incluir o passo 0 na skill `esteira`. Para conferir agora, à mão, só para o John, nunca em issue
nem em commit (o relatório tem e-mails):

```sql
-- conferir agora: a Edge pede ao FCM uma validação para cada aparelho Android (nada é entregue)
select public.push_check_run();
-- depois de alguns segundos:
select * from public.testers_install_report();
```

Resultado por testador: `instalado`, `desinstalado` (o FCM disse que o app não está lá),
`sem aparelho registrado` (nunca abriu, ou não gerou token: não quer dizer que não instalou),
`erro na conferencia` ou `ainda nao conferido`. O número oficial de testadores continua sendo o do
Play Console; este relatório só ajuda a acompanhar quem instalou.

## Contagem para produção

- Dashboard do app no Play Console → card "Apply for access to production": os três requisitos
  (versão no teste fechado, **12 inscritos**, 14 dias seguidos). Ler o texto da página.
- Para saber **quem** ainda não entrou: cruzar `allowed_players` (`source = 'play'`) com
  `auth.users` (quem entrou e abriu o jogo tem conta). Responder só com nomes de perfil e contagens.
- As contas QA (`aigamesfactory.qa01/qa02`) estão na lista para testes, mas **não contam** como
  testadores reais.

## Responder ao John

Quem entrou desde a última vez, quem está pendente, o total inscrito e o que falta para a produção.
