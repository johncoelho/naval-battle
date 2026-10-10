# Histórico e decisões

Linha do tempo das **decisões** do Naval Battle: o que foi decidido, quando e por quê. O
[CHANGELOG](CHANGELOG.md) diz *o que mudou* em cada versão; este arquivo diz *por que o projeto
é como é*. Se uma sessão de trabalho se perder, isto + [MEMORY.md](MEMORY.md) +
[CLAUDE.md](CLAUDE.md) bastam para retomar.

**Como manter:** toda decisão nova (de produto, técnica ou de processo) entra aqui no mesmo commit
em que é aplicada, na fase atual, no formato `data · decisão — porquê`. Decisão revertida não é
apagada: ganha uma linha nova dizendo o que mudou e por quê.

---

## Fase 0 · Origem (12–13/09/2026)

- **12/09 · Jogo nativo em Kotlin Multiplatform + Compose**, Android primeiro e iOS "no horizonte" —
  uma base de código para as duas lojas, sem reescrever telas.
- **12/09 · Nenhuma imagem no APK: toda arte em Canvas.** APK leve, arte escalável de 16 dp a tela
  cheia, e item de loja barato de criar (pintura e casco são parâmetros, não desenhos novos).
- **12/09 · Identidade "Command HUD"**: centro de comando naval noturno, verde-radar e âmbar para
  ação. Um único tema escuro, de propósito.
- **12/09 · Regra das dependências: não ter.** Rede com `HttpURLConnection` + `org.json`, fonte do
  sistema, persistência em `SharedPreferences`. Toda biblioteca precisa se pagar.
- **13/09 · Supabase como back-end** (Auth + Postgres com RLS): conta, carreira na nuvem e depois o
  modo online, sem servidor próprio para manter.
- **13/09 · Três idiomas desde cedo** (pt-BR, en, es) com cada frase nas três línguas na mesma linha
  de `Strings.kt` — tradução faz parte de criar a tela.
- **13/09 · "Acertou, joga de novo"** vira a regra de turno nos dois modos (batalha naval clássica).
- **13/09 · Rede local por NSD/mDNS e sockets**, não Nearby Connections: sem Play Services, sem
  permissão de localização, e o mesmo protocolo do Bonjour serve ao iOS.
- **12/09 · GitHub `johncoelho/naval-battle`**; CI publica APK na release rolante `latest`, e o site
  aponta sempre para ela (link nunca muda).

## Fase 1 · Conta, online e iOS (13–19/09)

- **13/09 · Login com Google** (Credential Manager no Android) caindo na mesma carreira do e-mail
  (gatilho no Supabase).
- **13–14/09 · Primeiro build iOS e `.ipa` sem assinatura** instalável por Sideloadly — testadores de
  iPhone sem pagar Apple Developer.
- **14/09 · Modo online pela REST do Supabase** (sala de amigo com código, partida rápida, amigos),
  mesmo protocolo de linha da rede local, com polling — sem WebSocket nem servidor de jogo.
- **15–17/09 · Publicação automática na Play** (Gradle Play Publisher + conta de serviço): todo push
  na `main` vira versão na faixa de teste. **17/09 · Autonomia total no release**: o fluxo segue
  sozinho até a Play; o John só testa no aparelho.
- **16–17/09 · Regras de release** depois de erros reais: todo commit sobe `versionCode` (a Play
  recusa repetido) e escreve as notas "Novidades" (testadores acompanham versão a versão).
- **17/09 · Navegador:** o painel embutido só para tarefa 100% autônoma; o que o John acompanha vai
  no Chrome dele.
- **19/09 · Ranqueada, temporadas por estação e placar**; servidor decide o vencedor.
- **19/09 · Imersão do Submarino removida** — escondia acerto como água e parecia bug. Regra:
  nenhuma mecânica passiva pode disfarçar o estado real de uma célula.
- **19/09 · Botões com título e legenda empilhados**, nunca lado a lado (espremia em botão estreito).
- **19/09 · Menu e Configurações redesenhados**; opções de tabuleiro (cor do oceano, grade) adiadas
  para o backlog.

## Fase 2 · Teste fechado e economia (20/09–06/10)

- **22/09–01/10 · Feedback recompensado e badges** (Beta Tester, Colaborador). Aprovação de
  feedback é **sempre manual, com o OK do John** (04/10, depois de eu aprovar um sozinho).
- **01/10 · Beta tester entra pela lista do Play Console**, levada à mão; a fila do site só registra.
- **02/10 · Melhorias de CX** a partir de avaliação heurística de um especialista (planilha).
- **04/10 · Economia aprovada:** "Créditos" viram **Dobrões**; loja de dobrões **simulada** só para
  beta testers (até R$ 50/dia, nenhum cartão real); **milhas náuticas** (10/dia, 1 por partida
  online, vitória ranqueada +5, casual +1, teto 50); billing real depois, pela Google Play Billing.
- **04/10 · Passe de temporada** (gratuito ou pago em dobrões) como requisito da ranqueada.
- **05/10 · Diário de bordo** (check-in de 7 dias + desafio do dia), valores no servidor.
- **05/10 · Janela de versão nova** lendo `app_releases`; no Android só quando a Play confirma.

## Fase 3 · Lançamento do teste fechado (07–08/10)

- **07/10 · Deque de comando novo**, modo como etapa do fluxo, 2 jogadores na horizontal.
- **07/10 · Ícone "Acerto no casco"** e abertura nova. Lição: na ficha da Play, subir asset com nome
  novo e conferir pela URL — nome igual escondeu que o ícone não tinha sido trocado.
- **07/10 · Push de verdade no Android** (Firebase Cloud Messaging; envio pelo Supabase com `pg_net`
  e Edge Function). Push é **genérico**: todo aparelho recebe avisos gerais, com ou sem conta.
- **07/10 · Amigos com presença**: convite só para quem está online, fora de partida e com
  "Receber convites" ligado (padrão ligado). Ranqueada entre amigos liberada **só no beta**
  (`friend_ranked_enabled`). Partida rápida oferecida a um disponível por vez.
- **07/10 · Página de beta tester** no site com situação da liberação; liberação na Play segue
  manual e o aviso ao testador é só pela página.
- **08/10 · Excluir conta pelo app** (exigência da App Store 5.1.1).
- **08/10 · iPhone na App Store:** conta Apple Developer paga (pessoa física). Login com Google
  mantido por enquanto (Apple depois), loja de dobrões simulada mantida, e **o `.ipa` do Sideloadly
  continua ativo** até a versão da Apple ter tudo.

## Fase 4 · Qualidade e processo (09–10/10)

- **09/10 · Emuladores QA01/QA02** com Play Store e contas Google de teste (logadas pelo John;
  senhas nunca são digitadas por IA). Pixel_6 sem loja fica para prévia de desenvolvimento.
- **09/10 · Bateria de testes completa vira SPEC fixa** (`docs/QA_TEST_PLAN.md`, skill
  `qa-full-test`): dois aparelhos primeiro, depois um; limpeza do servidor e relatório no fim.
  Contas QA não jogam contra jogadores reais e saem do placar ao final.
- **09/10 · Teste fechado:** 12 testadores inscritos; contagem de 14 dias seguidos até poder pedir
  produção (previsão 23/10). Contas QA não contam para esse mínimo.
- **09–10/10 · Ranqueada soma zero** (0.30.0): o vencedor ganha exatamente o que o perdedor perde;
  saíram bônus de desempenho, piso de +5 e alívio — inflavam ~6 pontos por partida e o bônus batia
  no teto sempre. A força do adversário é a de **antes** da partida (antes dependia de quem relatava
  primeiro). Só **3 ranqueadas por dia contra o mesmo adversário** valem pontos (anti-farm).
- **10/10 · Revanche online em sala nova**: cobra milha e pontua de novo (antes reaproveitava a sala
  e saía de graça e sem pontos).
- **10/10 · Base de conhecimento para IA**: `CLAUDE.md` (porta de entrada), `HISTORY.md` (este),
  `MEMORY.md` (memória sintetizada), especificação técnica dentro do README, design system no
  Claude Design ([Naval Battle Command HUD](https://claude.ai/artifact/LCZELzjB8RFDv2pdGo7HNg)) e
  processos repetíveis como skills em `.claude/skills/`.
- **10/10 · Camada nova na stack só com aprovação do John**, registrada no README e aqui.
- **10/10 · Testes de unidade** (`composeApp/src/commonTest`, `kotlin.test` — aprovado pelo John):
  regra de jogo nova ganha teste e bug de lógica corrigido ganha teste de regressão. Três falhas da
  semana (barragem, pontuação, revanche) eram de lógica pura e teriam sido pegas antes.
- **10/10 · Trava de release no CI** (`tools/release-check.sh`): push que mexe no app sem versão nova,
  iOS alinhado, notas da Play ou CHANGELOG falha antes do build. Disciplina vira garantia.
- **10/10 · SPEC com critérios de aceite** para feature média/grande; cada critério vira caso no
  `QA_TEST_PLAN.md`. **Uma versão por assunto** (revisão mais rápida, causa óbvia se quebrar).
- **10/10 · Backlog inteiro no GitHub Issues**; o MEMORY guarda só assuntos em andamento e decisões.
  Issues são criadas e operadas sem navegador (`gh` ou MCP do GitHub), por preferência do John. A
  regra antiga "5 issues abertas = trabalhar sozinho" deixou de valer: o John prioriza; só bug que
  quebra o jogo pode ser pego sem pedir.
- **10/10 · Backlog migrado para o GitHub Issues** (#6–#18, criadas pelo `gh` logado pelo John) e
  trava de release + testes de unidade ligados no CI (aplicado com o OK dele).
- **10/10 · Esteira agêntica**: papéis de time (Suporte, PO, Tech Lead, Dev, Release, QA, Game
  Designer) como agentes em `.claude/agents/`, rodada recorrente (skill `esteira`) e quadro kanban no
  GitHub Projects. **Bug entra direto na Fila e é resolvido logo; melhoria só com aprovação do John**
  (substitui "só bug que quebra o jogo sem pedir"). Fila serial com push direto na `main` e revisão
  do Tech Lead antes de todo push; PRs e paralelismo só quando a fila pedir.
- **10/10 · Mudar regra aplicada é melhoria, não bug** — precisa da aprovação do John mesmo quando a
  regra parece errada ou explorável (caso da #6, vitória por desistência rendendo XP e dobrões). Bug
  é só o código não fazer o que a regra documentada diz.
- **10/10 · Agente publica sozinho o que já foi liberado** — palavras do John: "se o desenvolvimento
  já foi aprovado por mim não vejo pq o agente não publicar sozinho, desde que todos testes
  automatizados tenham dado certo". Condições: item liberado (bug pela regra, melhoria aprovada),
  APROVADO do Tech Lead, testes e `release-check` verdes. Primeira entrega da esteira: #7 (0.30.3).
- **10/10 · Ambiente de teste do banco (branch do Supabase) adiado** — por ora, testes que desfazem
  tudo em produção (skill `supabase-change`).

---

## Artefatos de referência (SPECs e designs aprovados)

| Data | Artefato |
|---|---|
| 12/09 | [Naval Battle Command HUD (identidade original)](https://claude.ai/artifact/WdSLhmvbjnQYzaReknNSxv) · [Dossier](https://claude.ai/artifact/Pcdsg1xpn2RoHxp7WwotuK) |
| 13/09 | [Abertura](https://claude.ai/artifact/2kXiRcpFYMX3MYkawGdEKC) |
| 19/09 | [Menu novo](https://claude.ai/artifact/VRcHhTMUSPBK9AiJXhutC8) · [Novos navios e avatares](https://claude.ai/artifact/NRpMj2RPSMinLaPxnXVLC9) · [Habilidades táticas](https://claude.ai/artifact/5kA2HEWerfeqvWhjvvpkPT) |
| 25/09 | [SPEC — Feedback bonificado, badges e beta testers](https://claude.ai/artifact/YWMhFe4bN1tATRcf7qY6wP) |
| 01/10 | [SPEC — Melhorias de CX](https://claude.ai/artifact/XDau1ZjhVFr9Z3XviZ6Kfx) |
| 04/10 | [SPEC — Estaleiro, partida rápida, convites e relatório online](https://claude.ai/artifact/2kDikuwrR2iksYRzwUoy3e) · [Release notes de CX](https://claude.ai/artifact/Vx4GHqWLfri4KXN9JDMPmr) |
| 07/10 | [Ícone e banner da loja](https://claude.ai/artifact/TLxxU2YrJJiFiigziHuaE4) |
| 10/10 | [Design system — Naval Battle Command HUD](https://claude.ai/artifact/LCZELzjB8RFDv2pdGo7HNg) |
