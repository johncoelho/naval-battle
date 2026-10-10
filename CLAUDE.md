# Naval Battle — guia para a IA

Jogo de batalha naval em Kotlin Multiplatform + Compose (Android na Play, iOS por `.ipa`), back-end
Supabase. Dono: **John Coelho** (GitHub `johncoelho`). **Fale com ele em português do Brasil.**

## Ler antes de trabalhar

1. [MEMORY.md](MEMORY.md) — regras de ouro, como ele trabalha, estado atual, pendências, backlog.
2. [README.md → Especificação técnica](README.md#especificação-técnica) — stack, estrutura, padrões
   de código, back-end, release. **É o padrão: código novo segue, mudança de padrão atualiza.**
3. [HISTORY.md](HISTORY.md) — por que o projeto é como é. Consulte antes de mudar algo já decidido.
4. Design system: [docs/DESIGN_SYSTEM.md](docs/DESIGN_SYSTEM.md) e
   [Naval Battle Command HUD](https://claude.ai/artifact/LCZELzjB8RFDv2pdGo7HNg) no Claude Design
   (fonte versionada em `docs/design-system/`).

## Skills (processos fixos em `.claude/skills/`)

| Quando | Skill |
|---|---|
| Qualquer mudança que vá para a `main` (código, banco, site) | `dev-cycle` |
| Tela, popup, componente, cor, texto de interface, ícone | `design-system` |
| SQL, RPC, RLS, regra de pontos/moeda/milhas, `app_config` | `supabase-change` |
| Detalhes do pipeline Android → Play Console | `play-store-release` |
| "Bateria de testes completa" / teste de ponta a ponta | `qa-full-test` |
| "Temos bugs?" / feedback de jogador | `feedback-triage` |
| Testadores novos, liberar na Play, contagem para produção | `beta-testers` |

Padrão que se repete e ainda não tem skill: proponha criar uma.

**Backlog:** [GitHub Issues](https://github.com/johncoelho/naval-battle/issues) — pendência nova vira
issue (via `gh`/MCP do GitHub, sem navegador). O John prioriza; só bug que quebra o jogo pode ser
pego sem pedir. `MEMORY.md` guarda só assuntos em andamento e decisões.

## Inegociável

- Todo push na `main` publica: subir versão (Android e iOS) e escrever as notas da Play no mesmo commit.
- Documentação (CHANGELOG sempre; HISTORY, MEMORY, README, docs, design system quando couber)
  no mesmo commit do código.
- **Camada nova na stack só com aprovação do John**; aprovada, vai para o README e o HISTORY.
- Nunca digitar senha, logar por ele ou criar conta. Segredo nunca no repositório.
- Feedback de jogador só é aprovado/recusado com o OK dele.
- Banco: arquivo em `supabase/` é a fonte da verdade; migração com o mesmo conteúdo; testar com
  bloco que desfaz.

## Manter esta base de conhecimento viva

- Decisão tomada → `HISTORY.md` (data · decisão — porquê).
- Regra, preferência, estado ou pendência nova → `MEMORY.md` (curto, por relevância; apagar o velho).
- Padrão de código/stack mudou → README → Especificação técnica.
- Fim de uma entrega: conferir se os três acima refletem o que aconteceu.
