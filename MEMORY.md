# Memória do projeto

O que precisa ser lembrado em **toda** sessão, do mais importante para o menos. Curto de propósito:
o detalhe mora no [README](README.md#especificação-técnica), no [HISTORY](HISTORY.md) e nas skills.
Atualizar quando surgir regra, preferência ou fato novo; apagar o que deixar de ser verdade.

## 1. Regras de ouro (nunca quebrar)

1. **Falar com o John em português do Brasil**, inclusive status e resumos.
2. **Todo push na `main` publica** (Play faixa Alpha + `.ipa` + site): subir `versionCode`/
   `versionName` e o `CFBundleShortVersionString` do iOS, e escrever as notas da Play
   (`composeApp/src/main/play/release-notes/pt-BR/default.txt`, ≤ 500, sem acento) **no mesmo commit**.
   Exceção: commit só de documentação/skills leva `[skip ci]` na mensagem e não sobe versão.
3. **Documentação anda com o código**: CHANGELOG sempre; README, HISTORY, MEMORY, docs/ e design
   system quando o assunto mudar. Nunca "depois".
4. **Camada nova na stack só com aprovação do John** (biblioteca, serviço, plugin, provedor, versão
   maior). Aprovada, entra no README → Especificação técnica e no HISTORY.
5. **UI nova segue o design system** ([Naval Battle Command HUD](https://claude.ai/artifact/LCZELzjB8RFDv2pdGo7HNg),
   skill `design-system`): tokens `Naval`/`NavalType`, componentes de `ui/Components.kt`, textos
   nas três línguas em `Strings.kt`. Componente ou token novo entra no código, no
   `docs/DESIGN_SYSTEM.md` e no artefato juntos.
6. **Nunca digitar senha nem logar por ele**; não criar contas; segredo nunca no repositório
   (`FCM_SERVICE_ACCOUNT` e conta de serviço da Play só em secrets). A chave anônima do Supabase é pública.
7. **Feedback de jogador só é aprovado/recusado com o OK do John** ("temos bugs?" = listar e propor).
8. **Nenhuma mecânica pode disfarçar o estado real de uma célula** do tabuleiro (lição da Imersão).
9. **Banco: o arquivo em `supabase/` é a fonte da verdade**; mudança vai no arquivo e é aplicada como
   migração com o mesmo conteúdo, testada com bloco `do $$ … raise exception` que desfaz tudo.

## 2. Como o John quer trabalhar

- **Autonomia total no release:** do pedido à Play sem pedir licença (skill `dev-cycle`); ele só
  instala e testa. Verificar o CI de verdade antes de dizer que está no ar.
- Processo que se repete vira **skill** em `.claude/skills/`.
- "Bateria de testes completa" = `docs/QA_TEST_PLAN.md` (skill `qa-full-test`).
- Prévia para aprovação é **build local** (APK `.preview`), nunca branch (branch também publica).
- Navegador: painel embutido só para tarefa autônoma; o que ele acompanha vai no Chrome dele.
- Botões: título em cima à esquerda, legenda embaixo à direita, nunca lado a lado.
- Esteira agêntica (`docs/ESTEIRA.md`): **bug vai direto para a Fila e é resolvido logo**; melhoria
  só entra na Fila com aprovação dele (arrasta o cartão ou comenta "aprovado"). **Mudar regra que já
  está valendo é melhoria, nunca bug** — sempre com a aprovação dele.
- Antes de fechar uma SPEC de melhoria, avisar se há bugs de jogador pendentes para incluir.

## 3. Assuntos em andamento

- **Esteira agêntica (10/10):** agentes em `.claude/agents/`, skill `esteira`, `docs/ESTEIRA.md`.
  Quadro: https://github.com/users/johncoelho/projects/1. Falta: primeira rodada acompanhada e a
  tarefa agendada (a cada 30 min).
- **Base de conhecimento e qualidade (10/10):** CLAUDE.md, HISTORY.md, MEMORY.md, especificação técnica
  no README, design system no Claude Design, skills; testes de unidade (`commonTest`) e trava de
  release no CI (`tools/release-check.sh`).
- **iPhone na App Store:** aguardando a ativação da conta Apple Developer (paga em 08/10). Depois:
  chave da API do App Store Connect como secret, app no App Store Connect, TestFlight **ao lado** do
  `.ipa` do Sideloadly (que segue ativo até a versão da Apple ter tudo).
- **Teste fechado → produção:** 12 testadores inscritos; 14 dias seguidos (≈ 23/10), depois pedir
  produção no Play Console. Contas QA não contam.

## 4. Decisões recentes (detalhe e porquê no HISTORY)

- 10/10 · Backlog inteiro no GitHub Issues; aqui só assuntos em andamento e decisões.
- 10/10 · Testes de unidade obrigatórios para regra de jogo; bug de lógica corrigido ganha teste.
- 10/10 · CI barra release incompleto (versão, iOS, notas, CHANGELOG).
- 10/10 · SPEC com critérios de aceite para feature média/grande; critérios viram casos de QA.
- 10/10 · Uma versão por assunto.
- 10/10 · Ambiente de teste do banco (branch Supabase) adiado.
- 10/10 · Camada nova na stack só com aprovação do John.
- 09–10/10 · Ranqueada soma zero, força do adversário antes da partida, 3/dia contra o mesmo
  adversário; revanche online em sala nova.
- 08/10 · iPhone: Google login mantido (Apple depois), loja simulada mantida, `.ipa` segue ativo.

## 5. Pendências com o John

- Avisar quando a conta Apple ativar.

## 6. Ambiente e armadilhas conhecidas

- Backlog: [GitHub Issues](https://github.com/johncoelho/naval-battle/issues) — pendência nova vira issue, não linha aqui.
- `gh` logado como `johncoelho` (login feito pelo John): issues e acompanhamento do CI
  (`gh run watch`). Mudança em `.github/workflows` só com o OK do John, caso a caso.

- GitHub: sempre `johncoelho`. `git push` direto funciona (se travar, ver
  `git-credential-manager github list` por identidade duplicada).
- Gradle local no Windows: TMP/TEMP em `D:\tmp` + `JAVA_TOOL_OPTIONS` (ver docs/BUILD.md). Nunca
  `git checkout --` no `build.gradle.kts` durante a troca para `.preview`.
- AGP 9.x quebra KMP: não subir.
- Emuladores: QA01/QA02 (Play Store, contas de teste), Pixel_6 (sem loja). Ligar com
  `-feature -Vulkan`; desligar com `adb emu kill`. App `.preview` não loga com Google.
- Ficha da Play: asset novo sempre com nome novo, conferir pela URL.
- Supabase: projeto `cwtslesnthbenxswdcbv`; `apply_migration` pode pedir permissão explícita.
- Play: versão nova passa por revisão do Google (horas); o John avisa quando sai.
