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
| Release | `release` | `app_releases`, CI, `ios_live`, versão na issue | — |
| QA | `qa` | valida na Play (QA01/QA02) e fecha a issue | — |
| Game Designer | `game-designer` | analisa dados, propõe balanceamento e mecânicas | — |

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
| **Concluído** | validado na Play, issue fechada | — |

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
- Item em **Em andamento** há mais de 3 horas sem commit: a rodada seguinte retoma ou devolve à Fila.

## Rodada (skill `esteira`, agendada)

1. Suporte faz a triagem (issues novas e feedback pendente).
2. PO escreve proposta para melhorias novas e ordena a Fila.
3. Se nada estiver **Em andamento**: pega **toda a Fila** como um lote → Tech Lead desenha (se preciso) → Dev
   implementa → Tech Lead revisa → Dev publica → Release acompanha o CI.
4. QA **não** roda na rodada: tem tarefa própria.
5. Relatório curto para o John (o que entrou, o que saiu, o que espera dele).

## Rodada do QA (tarefa `esteira-qa-naval-battle`, de hora em hora)

O sinal de "liberado para testes" vem do **Play Console**, não do emulador (decisão do John):
[Submission activity](https://play.google.com/console/u/0/developers/6665366169429730164/app/4973862797492773091/publishing/submission-activity)
lista cada envio com o status (*In review* / *Published*); o detalhe do envio mostra a versão
(ex.: envio 65 = 0.30.3, *Published*). Lido no Chrome do John (já logado), só leitura.

1. Sem cartão em **Publicado** nem em **Liberado para testes**: termina.
2. Cartão em **Publicado**: abre o Submission activity no Chrome e procura o envio da versão do
   cartão. *Published* → move os cartões dessa versão para **Liberado para testes**. Ainda em
   revisão → termina sem ligar emulador.
3. Cartão em **Liberado para testes**: só então liga os emuladores (QA01 = `emulator-5554`,
   QA02 = `emulator-5556`; pacote do app `aigamesfactory.navalbattleclassic`), atualiza pela Play, testa os
   critérios de aceite e os casos do `QA_TEST_PLAN.md`, fecha o que passou (**Concluído**) e devolve
   à **Fila** o que falhou. **Todo fechamento leva print de evidência** no comentário, passou ou
   falhou (`tools/qa-evidencia.sh` → branch `qa-evidencias`). Desliga os emuladores.

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
| Concluído | `5785fa7f` |

```bash
# cartões, etapas e número da issue
gh project item-list 1 --owner johncoelho --format json --jq '.items[]|{id, n: .content.number, title, status}'
# pôr uma issue no quadro (devolve o id do cartão)
gh project item-add 1 --owner johncoelho --url https://github.com/johncoelho/naval-battle/issues/<N> --format json --jq .id
# mover um cartão
gh project item-edit --project-id PVT_kwHOACzJk84Bme7c --id <ID-DO-CARTAO> --field-id PVTSSF_lAHOACzJk84Bme7czhlHyVE --single-select-option-id <ID-DA-ETAPA>
```
