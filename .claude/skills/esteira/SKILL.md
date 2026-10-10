---
name: esteira
description: Uma rodada da esteira agêntica do Naval Battle — Suporte faz a triagem de issues e feedback, PO propõe melhorias e ordena a Fila, e se nada estiver em andamento o primeiro item da Fila é implementado (Tech Lead, Dev, Release), e o QA valida o que a Play liberou. Use na tarefa agendada da esteira ou quando o John pedir "roda a esteira" ou "olha a fila".
---

# Rodada da esteira

Processo, quadro e regras: `docs/ESTEIRA.md` (ler inteiro antes). Agentes em `.claude/agents/`.
Fale em português do Brasil. Você é o orquestrador: chama cada agente com o Agent tool
(`subagent_type` = nome do agente) e passa a eles o que precisam; não faça o trabalho deles.

## Passos

1. **Estado:** `git -C D:/sourcecode/batalha-naval fetch origin`, issues abertas e cartões do quadro
   (comandos no `docs/ESTEIRA.md`).
2. **Suporte** (`suporte`): triagem de issues novas e feedback pendente. Bug → Fila; melhoria →
   Aguardando aprovação.
3. **PO** (`po`): proposta nas melhorias novas; move para a Fila as que o John aprovou por comentário;
   ordena a Fila (P0, P1, melhorias aprovadas).
4. **Implementação**, só se nada estiver **Em andamento**:
   - mova o primeiro da Fila para **Em andamento**;
   - melhoria ou bug não trivial: **Tech Lead** desenha (comentário na issue);
   - **Dev** implementa no worktree e faz o commit;
   - **Tech Lead** revisa o diff; CORRIGIR volta ao Dev (até 3 voltas; depois disso, pare e reporte);
   - APROVADO: Dev publica; **Release** acompanha o CI e move para **Publicado**.
   - Item **Em andamento** há mais de 3 h sem commit: retome do worktree se existir, senão devolva
     à Fila com comentário.
5. **QA** (`qa`): só se houver cartão em **Publicado** e a versão já estiver na Play dos emuladores.
6. **Relatório** (resposta final, curta): triagem feita, item publicado (versão), o que está
   aguardando o John (melhorias para aprovar, decisões). Rodada sem nada novo: uma linha.

## Limites

- Uma implementação por rodada. Nunca duas ao mesmo tempo.
- Nunca: aprovar feedback, mover melhoria para a Fila sem aprovação do John, mexer em
  `.github/workflows`, adicionar dependência, digitar senha.
- Algo que precisa do John (decisão, permissão negada, CI quebrado 3 vezes): pare o item, comente na
  issue e diga no relatório.
