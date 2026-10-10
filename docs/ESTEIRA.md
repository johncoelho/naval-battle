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
| Dev | `dev` | implementa um item da Fila num worktree, com testes e documentação | — |
| Release | `release` | `app_releases`, CI, `ios_live`, versão na issue | — |
| QA | `qa` | valida na Play (QA01/QA02) e fecha a issue | — |
| Game Designer | `game-designer` | analisa dados, propõe balanceamento e mecânicas | — |

## Quadro

[GitHub Projects — Naval Battle · Esteira](https://github.com/users/johncoelho/projects) (campo
**Etapa**, visão em quadro). Cada issue aberta é um cartão.

| Etapa | Significa | Quem move para a próxima |
|---|---|---|
| **Novo** | chegou, sem triagem | Suporte |
| **Aguardando aprovação** | melhoria com proposta do PO | **John** (arrasta para Fila ou comenta "aprovado") |
| **Fila** | pronto para fazer, na ordem | rodada da esteira pega o primeiro |
| **Em andamento** | Dev + Tech Lead trabalhando | Dev (push) |
| **Publicado** | CI verde, aguardando a revisão da Play | QA, quando a Play libera |
| **Concluído** | validado na Play, issue fechada | — |

## Regras

- **Bug vai direto para a Fila e é resolvido logo**, sem esperar aprovação (P0 antes de P1).
- **Melhoria só entra na Fila com aprovação do John.** O Game Designer e o PO propõem; ele decide.
- **Um item em andamento por vez** (fila serial, push direto na `main` como hoje). Tech Lead aprova o
  diff antes de todo push. Paralelismo com PRs fica para quando a fila pedir.
- **Uma versão por assunto.**
- Feedback de jogador vira issue, mas aprovar, recusar ou recompensar continua sendo do John.
- Nada em `.github/workflows`, stack nova ou dinheiro sem o OK dele.
- Item em **Em andamento** há mais de 3 horas sem commit: a rodada seguinte retoma ou devolve à Fila.

## Rodada (skill `esteira`, agendada)

1. Suporte faz a triagem (issues novas e feedback pendente).
2. PO escreve proposta para melhorias novas e ordena a Fila.
3. Se nada estiver **Em andamento**: pega o primeiro da Fila → Tech Lead desenha (se preciso) → Dev
   implementa → Tech Lead revisa → Dev publica → Release acompanha o CI.
4. QA valida o que está em **Publicado** se a Play já liberou.
5. Relatório curto para o John (o que entrou, o que saiu, o que espera dele).

## Comandos do quadro

Preenchidos quando o quadro foi criado (número do projeto e ids dos campos):

```bash
gh project item-list <NUM> --owner johncoelho --format json        # cartões e etapas
gh project item-add <NUM> --owner johncoelho --url <url-da-issue>  # pôr issue no quadro
gh project item-edit --project-id <PID> --id <ITEM> --field-id <FID> --single-select-option-id <OPT>
```
