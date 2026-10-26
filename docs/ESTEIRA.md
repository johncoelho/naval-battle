# Esteira agêntica

Como o trabalho do Naval Battle anda sozinho, do relato à loja, com o John decidindo só o que é dele.
Os agentes ficam em `.claude/agents/`; a rodada recorrente é a skill `esteira`.

## Papéis

| Papel | Quem | Faz | Decide |
|---|---|---|---|
| Dono do produto | **John** | prioriza, aprova melhorias e SPECs | melhoria, prioridade, stack, dinheiro, feedback, workflows |
| Suporte | `suporte` | lê issues e feedback, classifica bug × melhoria, deixa reproduzível | — |
| PO | `po` | SPEC com critérios de aceite, ordem da Fila, quebra pedidos grandes | — |
| Tech Lead | `tech-lead` | desenho técnico antes, revisão antes de todo push | — |
| Dev | `dev` | implementa o lote da Fila num worktree, com testes e documentação | — |
| Release | `release` | `app_releases`, CI, `ios_live`, versão na issue; promove e libera a produção | — |
| QA | `qa` | valida na Play (QA01/QA02) e manda o cartão para Em produção | — |
| Game Designer | `game-designer` | analisa dados, propõe balanceamento e mecânicas | — |

**Modelos (25/10, para economizar sem travar):** a tarefa agendada (orquestrador) pode rodar no Haiku, mas
cada agente fixa o próprio modelo no cabeçalho: Tech Lead no Opus (desenho e revisão, poucos tokens e
decisões caras), Dev, PO, Game Designer, Suporte, Release e QA no Sonnet. Sem isso os agentes herdavam o
Haiku do orquestrador e a revisão entrava em loop (#35 voltou três vezes em 25/10).

## Quadro

[GitHub Projects — Naval Battle · Esteira](https://github.com/users/johncoelho/projects/1) (campo
**Status**, visão em quadro). Cada issue aberta é um cartão.

| Etapa | Significa | Quem move para a próxima |
|---|---|---|
| **Novo** | chegou, sem triagem | Suporte |
| **Aguardando aprovação** | melhoria com proposta do PO | **John** (arrasta para Fila ou comenta "aprovado") |
| **Fila** | pronto para fazer, na ordem | rodada da esteira pega o primeiro |
| **Em andamento** | Dev + Tech Lead trabalhando | Dev (push) |
| **Publicado** | CI verde e enviado; em revisão do Google | tarefa do QA, quando o Play Console mostra Published |
| **Liberado para testes** | Play liberou para os testadores; QA testando | QA |
| **Em produção** | QA aprovou todos os cartões da versão; promovida na produção com liberação gradual (20%, 50%); issue aberta | Release, quando a versão chega a 100% |
| **Concluído** | versão em 100% na produção, issue fechada | — |

## Regras

- **Bug vai direto para a Fila e é resolvido logo**, sem esperar aprovação (P0 antes de P1). Bug é
  o código não fazer o que a regra documentada (README, SPEC) diz.
- **Mudar uma regra que já está valendo é melhoria, nunca bug**, mesmo que a regra pareça errada ou
  explorável: precisa da aprovação do John.
- **Melhoria só entra na Fila com aprovação do John.** O Game Designer e o PO propõem; ele decide.
- **Um lote em andamento por vez** (push direto na `main` como hoje). Tech Lead aprova o diff antes
  de todo push. Paralelismo com PRs fica para quando a fila pedir.
- **Publicação sem esperar o John** (decisão dele, 10/10): item liberado para desenvolvimento (bug
  pela regra acima, melhoria aprovada por ele) + **APROVADO** do Tech Lead + testes de unidade e
  `release-check` verdes → o agente publica. O CI repete a trava e os testes antes de compilar e
  publicar; falhou, nada vai para a Play.
- **Um lote por release** (decisão do John, 10/10): tudo o que está na Fila quando a rodada começa
  vai junto numa versão só (substitui "uma versão por assunto"): um commit por
  item, uma subida de versão no fim, notas da Play e CHANGELOG citando todos. Item que travar sai do
  lote e o resto segue.
- Feedback de jogador vira issue, mas aprovar, recusar ou recompensar continua sendo do John.
- Nada em `.github/workflows`, stack nova ou dinheiro sem o OK dele.
- **iPhone não bloqueia** (decisão do John, 19/10): o iOS só é compilado para adiantar; o teste no
  iPhone fica para depois. Item que passou no QA do **Android** sai da fila (Concluído), mesmo sem
  teste em iPhone (vai para **Em produção**).
- **Só se promove para a produção com todos os cartões da versão aprovados no QA** (#39). O Release usa o
  versionCode que o QA testou no aparelho, nunca "a última da Alpha".
- Item em **Em andamento** há mais de 3 horas sem commit: a rodada seguinte retoma ou devolve à Fila.

## Rodada (skill `esteira`, agendada)

0. **PENDENTE** (#26): `select public.push_check_daily();` no SQL, quando o John incluir este passo na
   skill `esteira`. Só então a conferência diária roda sozinha. Texto devolvido entra no relatório
   (resumo para o John); null = nada a dizer. Enquanto não entrar, não há conferência automática.
1. Suporte faz a triagem (issues novas e feedback pendente).
2. PO escreve proposta para melhorias novas e ordena a Fila.
3. Se nada estiver **Em andamento**: pega **toda a Fila** como um lote → Tech Lead desenha (se preciso) → Dev
   implementa → Tech Lead revisa → Dev publica → Release acompanha o CI.
4. QA **não** roda na rodada: tem tarefa própria.
5. Relatório curto para o John (o que entrou, o que saiu, o que espera dele).

## Rodada do QA (tarefa `esteira-qa-naval-battle`, às 7h, 12h e 18h)

O sinal de "liberado para testes" vem do **Play Console**, não do emulador (decisão do John):
[Submission activity](https://play.google.com/console/u/0/developers/6665366169429730164/app/4973862797492773091/publishing/submission-activity)
lista cada envio com o status (*In review* / *Published*); o detalhe do envio mostra a versão
(ex.: envio 65 = 0.30.3, *Published*). Lido no Chrome do John (já logado), só leitura.

1. Sem cartão em **Publicado**, **Liberado para testes** nem **Em produção**: termina.
2. Cartão em **Publicado**: abre o Submission activity no Chrome e procura o envio da versão do
   cartão. *Published* → move os cartões dessa versão para **Liberado para testes**. Ainda em
   revisão → termina sem ligar emulador.
3. Cartão em **Liberado para testes**: ativa as contas QA (`is_test = false`), liga os emuladores (QA01 = `emulator-5554`,
   QA02 = `emulator-5556`; pacote do app `aigamesfactory.navalbattleclassic`), atualiza pela Play, testa os
   critérios de aceite e os casos do `QA_TEST_PLAN.md`, anota o versionCode testado
   (`adb shell dumpsys package aigamesfactory.navalbattleclassic | grep versionCode`), comenta o que passou e
   move para **Em produção** (a issue **não** fecha mais aqui) e devolve à **Fila** o que falhou. **Todo fechamento leva print de evidência** no comentário, passou ou
   falhou (`tools/qa-evidencia.sh` → branch `qa-evidencias`). Desliga os emuladores e **desativa as
   contas QA** (`is_test = true`), sempre, mesmo se o teste falhar.
4. **Release, no fim da tarefa do QA** (produção, #39): com cartões em **Em produção**, a tarefa abre o
   Submission activity no Chrome (só ela tem o Chrome; o agente `release` não tem), lê o status e a data do
   *Published* do envio de produção e passa os dois ao `release`, que roda `status`, `promote` ou `advance`.
   Ver a seção abaixo.

## Produção e liberação gradual (#39)

Workflow manual `promote-production.yml` (`workflow_dispatch`, sem cron) chama `tools/play-rollout.sh`
(openssl, curl e jq; Play Developer API v3, edits) com a conta `play-publisher-ci`. O `android.yml` segue
só com a Alpha. O Release dispara com o versionCode **sempre explícito**:
`gh workflow run promote-production.yml -f action=<promote|advance|halt|resume|status|vitals> -f version_code=N -f fraction=<0.2|0.5|1> [-f dry_run=true]`.
O histórico de runs (`gh run list -w promote-production.yml`) é o registro das etapas.

| Etapa | Fração | Quando | Quem |
|---|---|---|---|
| `promote` | 20% | QA aprovou **todos** os cartões da versão | Release |
| `advance` | 50% | ≥ 24 h em 20% (contadas do *Published* da produção), sem `ALARME` no vitals, nenhuma P0 aberta contra a versão | Release |
| `advance` | 100% (concluída) | ≥ 24 h em 50%, mesmas condições | Release |

- **Travas do `promote`** (erro em pt-BR, sem commit): (1) variável de repositório `PLAY_PRODUCTION_LIVE`
  = `true` (nasce `false`; vira `true` depois do *Published* da produção 91); (2) o versionCode está num
  release concluído da faixa alpha; (3) é maior que todo versionCode da produção; (4) notas e nome copiados
  do release da Alpha. O Release também não promove se o Submission activity mostrar envio de produção *In review*.
- **`vitals` (#51, só leitura):** `tools/play-rollout.sh vitals <versionCode>` lê a Play Developer Reporting API
  (`crashRateMetricSet` e `anrRateMetricSet` por versionCode, dados com 1 a 2 dias de atraso; escopo
  `playdeveloperreporting`, sem abrir edição) numa janela de 3 dias e imprime uma linha por dia
  (`dia | usuários | falha % | ANR %`), `vitals: <VEREDITO> (versionCode N, até AAAA-MM-DD)` e `vitals_json: {...}`.
  Veredito: `ALARME` (algum dia com ≥ 100 usuários e falha percebida > 1,09% ou ANR percebido > 0,47%), `OK`,
  `POUCOS_USUARIOS` (há dados, mas todos os dias abaixo de 100) ou `SEM_DADOS` (nenhuma linha). **Só `ALARME` faz
  halt**; `SEM_DADOS` e `POUCOS_USUARIOS` não travam o `advance` (com a base de hoje quase nenhuma versão chega a
  100 usuários por dia). O run só fica vermelho (exit 1) se a **leitura** falhar (ex.: 403 = conta sem permissão
  de relatórios ou API desligada); nesse caso o Release não avança naquela rodada e não faz halt. Limites por
  ambiente (`VITALS_CRASH_MAX`, `VITALS_ANR_MAX`, `VITALS_MIN_USERS`) com padrão fixo. Com `dry_run=true` roda o
  autoteste do veredito com amostras fixas, sem API. O Release comenta na issue
  `Vitals (versionCode N, até D): falha X%, ANR Y%, U usuários → VEREDITO`.
- `dry_run=true` valida na Play (`edits.validate`) e descarta a edição; ignora a trava 1 com aviso.
  `status` é só leitura e sempre funciona.
- **Release novo durante uma liberação** (a Play não aceita dois `inProgress`): antigo em 50% ou mais e sem
  alarme: conclui o antigo (`advance` 1) e depois promove o novo; em 20%: o novo substitui o antigo e
  recomeça em 20%. O script recusa substituir um release em 50% ou mais.
- **Halt:** `halt` grava `halted` (ninguém mais recebe; quem já recebeu fica), `resume` volta na mesma fração.
  O Release interrompe **sem esperar o John** e avisa depois, na issue e no relatório, quando: veredito `ALARME` do vitals
  (ação `vitals`, #51: falha percebida > 1,09% ou ANR percebido > 0,47% em algum dia da janela, com ≥ 100 usuários
  naquele dia), ou P0 aberta pelo Suporte contra a versão, ou falha em lote no log de diagnóstico. A correção da P0 sai em versão nova
  pelo caminho normal e substitui o release `halted`. Problema numa função com chave em `app_config`: desliga a chave antes.
- **Comentário:** a cada etapa, o Release comenta em cada issue da versão `Produção: 20% (versionCode N)`
  (e 50%, 100%). Ao chegar a 100%: cartão para **Concluído** e fecha a issue.
- **O John pausa pelo celular:** GitHub → Actions → *Promote production* → Run workflow → `halt` (sem
  versionCode: pausa o único release em andamento).
- **Permissões prontas (John, 25/10):** `play-publisher-ci` com produção e relatórios no Play Console e a
  Reporting API ativada. **Única pendência:** `gh variable set PLAY_PRODUCTION_LIVE --body true` depois do
  *Published* da produção 91 (até lá `promote` real é barrado; `status`, `dry_run`, `advance`, `halt` funcionam).
- iOS fica fora: o `.ipa` do Sideloadly segue na release `latest`.

## Comandos do quadro

Projeto nº **1** (`PVT_kwHOACzJk84Bme7c`), campo **Status** (`PVTSSF_lAHOACzJk84Bme7czhlHyVE`).

| Etapa | id da opção |
|---|---|
| Novo | `53b1f7e7` |
| Aguardando aprovação | `6d7c57b5` |
| Fila | `e7ed73af` |
| Em andamento | `aa0e138e` |
| Publicado | `3d5c2e0f` |
| Liberado para testes | `a274885f` |
| Em produção | `b8c48711` |
| Concluído | `5785fa7f` |

```bash
# cartões, etapas e número da issue
gh project item-list 1 --owner johncoelho --format json --jq '.items[]|{id, n: .content.number, title, status}'
# pôr uma issue no quadro (devolve o id do cartão)
gh project item-add 1 --owner johncoelho --url https://github.com/johncoelho/naval-battle/issues/<N> --format json --jq .id
# mover um cartão
gh project item-edit --project-id PVT_kwHOACzJk84Bme7c --id <ID-DO-CARTAO> --field-id PVTSSF_lAHOACzJk84Bme7czhlHyVE --single-select-option-id <ID-DA-ETAPA>
```
