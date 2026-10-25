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
- **10/10 · Um lote por release** (substitui "uma versão por assunto", da mesma manhã) — palavras do
  John: "se tiverem mais pontos para serem resolvidos na fila, sugiro fazer todos sempre no mesmo
  release, senão vai demorar mto". Cada versão passa pela revisão do Google; um commit por item
  mantém a causa fácil de achar.
- **10/10 · QA anexa print de evidência** em todo fechamento, passou ou falhou (pedido do John):
  mais fácil corrigir e mais claro quando deu certo. Prints no branch `qa-evidencias` (público, fora
  do CI), enviados por `tools/qa-evidencia.sh`.
- **10/10 · Esteira roda sozinha**: tarefas agendadas em modo automático (configurado pelo John no app),
  `defaultMode: auto` no projeto, e regras que ele adicionou para o push na `main` e o SQL do Supabase
  (o Release registra cada versão em `app_releases`). Antes, as rodadas paravam esperando permissão.
- **13/10 · Contas QA ativas só durante o teste** (decisão do John): o QA põe `is_test = false` no
  início e `true` no fim, sempre. Assim dá para testar o que mexe no ranking sem poluí-lo; os pontos
  do teste ficam invisíveis fora da sessão, nada é zerado e as contas nunca são removidas.
  Substitui "contas QA saem do placar" (09/10). A #24 (deixá-las sempre valendo) foi cancelada.
- **13/10 · Android só pela Play Store oficial** (#25, pedido do John: "vamos manter só a play store
  oficial"): o site deixa de oferecer o APK direto e todo caminho de Android leva ao teste fechado. A
  loja é o caminho mais seguro (assinatura oficial, atualização automática) e o APK avulso já não era
  necessário. O iPhone segue com o `.ipa` do Sideloadly até o TestFlight (08/10). O CI continua gerando o
  APK na release `latest`; parar de publicá-lo mexe em workflow e vira outra issue, com o OK do John.
- **15/10 · Push diário convidando para jogar** (pedido do John: todo jogador jogar todo dia): tarefa
  agendada `push-diario-naval-battle` às 12h (o lembrete local do Diário já é às 19h), uma mensagem
  provocativa por dia da semana, via `push_broadcast` (kind `daily_invite`). Travas: só envia entre
  11h e 15h e no máximo uma vez por dia. Só Android por enquanto (push no iPhone é a #14).
- **17/10 · Pendência do design system: cartão do Prêmio no artefato Claude Design** (#15) — o cartão
  está em `docs/DESIGN_SYSTEM.md`, mas o artefato ainda não recebeu o cartão nem o estado "Indisponível
  agora" da vitrine. Registrado em `docs/design-system/PENDENCIAS.md`; atualizar pela skill
  `design-system`, passo 3.
- **17/10 · Compras reais de dobrões aprovadas** (#15; perfil de pagamentos da Play verificado):
  camada nova **Google Play Billing** aprovada; pacotes de 04/10 (Bolsa R$ 4,90/500, Baú R$ 9,90/1.100,
  Cofre R$ 24,90/3.000, Tesouro R$ 49,90/6.500); sem teto diário. A loja simulada e o limite de
  R$ 50/dia saem: o beta tester ganha o **Pacote Prêmio Beta** (Tesouro, 1×/dia) pelo fluxo real da
  Play como testador de licença, sem cobrança. Venda para jogador comum só depois da produção
  (venda ligada: aguarda OK do John).
- **17/10 · Compra de dobrões: o servidor decide, o aparelho só aplica** (#15, 0.33.0) — porquê: o
  crédito não pode depender do que o aparelho diz. Compra conferida na API da Play pela Edge
  `dobroes-verify` (com a conta de serviço do secret `PLAY_BILLING_SERVICE_ACCOUNT`; hoje a permissão
  financeira está no `play-publisher-ci`, troca por conta só de billing aguarda o John), crédito uma
  vez por token, consumo feito pelo servidor logo depois, entrega ao aparelho por
  `dobroes_pending`/`dobroes_ack` (o aparelho guarda os tokens já somados). Interruptores
  `real_billing_enabled` e `premio_beta_enabled` nascem desligados (aguarda OK do John para ligar
  qualquer um dos dois). **Estado real conferido em 18/10:** `premio_beta_enabled` está `'true'` em
  produção, ligada antes do lote 0.33.3; quem ligou não se sabe pelo repositório, e o John confirma na
  issue #28. Não foi alterada no lote. **Limite conhecido:** o saldo ainda é `profiles.credits` gravado pelo
  aparelho: por isso a venda para jogador comum não liga antes da parte (e) do #15 (saldo
  autoritativo). Entrega sem soma dupla no aparelho: saldo e conjunto de compras entregues vão no
  mesmo lote (`Prefs.putBatch`, uma escrita no SharedPreferences do Android); no iPhone o
  NSUserDefaults não tem transação, mas a compra não roda ali. Loja simulada e `buy_beta_pack` viram `store_retired` (o app 0.32.x não credita).
- **17/10 · Falha da conferência de compra registra etapa e motivo, nunca segredo** (#27, 0.33.1) —
  porquê: a Edge respondia 503 sem registro (secret, JSON, chave e OAuth caíam no mesmo caso) e a
  compra do QA não creditava sem diagnóstico. Uma linha JSON por falha, com etapa e motivo fixo ou
  status HTTP. Não se loga `err.message` (o `JSON.parse` e a URL da rede podem trazer segredo ou token
  da compra) nem corpo da Google. Resposta ao app inalterada; a rede da compra passa de 500 para 502.
  Sobe versão porque o push na `main` publica; o app não muda.
- **17/10 · iPhone sem loja de dobrões** (recomendação aplicada, aguarda OK do John; #15 parte h,
  StoreKit fora do lote) — a aba fica fechada no iPhone, sem a simulada e sem o Prêmio; o `.ipa`
  segue com a versão antiga do jogo até lá.
- **17/10 · Saldo no aparelho com a venda desligada** (recomendação aplicada, aguarda OK do John) —
  porquê: enquanto o saldo for `profiles.credits` gravado pelo aparelho, uma venda aberta seria
  adulterável; por isso `real_billing_enabled` nasce desligado e a venda só liga depois do saldo
  autoritativo no servidor (#15 parte e).
- **17/10 · Conferência de instalação por push de validação** (#26, 0.33.0) — porquê: o número de
  testadores do Play Console não diz quem instalou. A Edge `push-check` pede ao FCM `validate_only`
  (o Google confere o token sem entregar nada a aparelho) e grava só o status; não apaga token (quem
  apaga é a função `push`, com 404). Relatório só da lista da Play; iPhone, `manual` e `qa` ficam fora.
- **17/10 · Conferência diária sem pg_cron, pendente do passo 0 na skill `esteira`** (#26, 0.33.2) —
  porquê: a conferência só rodava quando alguém executava o SQL. Sem pg_cron (extensão nova) e sem
  mexer em `.github/workflows` (exige OK do John), a escolha é o passo 0 da rodada da esteira:
  `push_check_daily()` em duas fases, porque o `pg_net` só envia depois do commit (dispara numa rodada,
  devolve o resumo na seguinte). Estado: a função está no banco e no repositório, mas **a edição da
  skill `esteira` foi negada pelo classificador de permissão** e não foi tentada de novo; até o John
  incluir o passo 0, a conferência é manual.
- **17/10 · Desinstalado é marcado, não apagado** (#26, 0.33.2; revisa o item de 0.33.0 acima) — porquê:
  apagar o token com 404 perdia o sinal "desinstalado" e fazia o relatório dizer "sem aparelho
  registrado", que é falso para quem desinstalou (visto no QA do 0.33.0). Agora a `push` marca
  `unregistered` e os envios pulam o token; reinstalar zera a marca. Relatório e resumo, sem e-mail.
- **18/10 · Prêmio Beta sai da Play e vira resgate no servidor** (#28, 0.33.3; substitui o desenho de
  17/10 "Prêmio pela Play como compra de testador") — porquê: a Play trata testador de licença por
  conta inteira. Toda compra dele vira teste, e o prêmio grátis não podia coexistir com pacote pago
  cobrado de verdade na mesma conta. Agora `claim_premio_beta()` credita 6.500 1× por dia (Brasília),
  com token estável por conta e dia; o prêmio não depende de a Play listar o produto.
- **18/10 · Vitrine paga para testador nasce desligada (`beta_store_enabled`)** (#28, 0.33.3) — porquê:
  sem o secret `PLAY_BILLING_SERVICE_ACCOUNT`, um pacote pago cobraria e não creditaria. O pedido era
  "já agora"; a recomendação do Tech Lead, levada à rodada, é ligar só depois do secret e dos 4
  produtos. Desvio registrado; o John liga quando estiver pronto.
- **18/10 · Compra de teste da vitrine credita o pacote; o caminho legado do Prêmio fica só para o
  app antigo** (#28, 0.33.3) — porquê: a origem `vitrine` chega na Edge. Sem origem (0.33.0 a
  0.33.2) a regra antiga do Tesouro 1× por dia continua, e sai quando esses apps saírem.
- **18/10 · Gate antes de tirar testador da licença** (#28, 0.33.3) — porquê: quem ficar fora da
  lista de teste de licença com app antigo seria cobrado no Prêmio pela Play. `dobroes_gate_licenca()`
  lista quem ainda não tem 0.33.3 em `device_checks`; a lista de licença só muda com essa consulta vazia.
- **18/10 · iPhone mostra só o Prêmio Beta na aba Dobrões; pacotes pagos ficam ocultos** (#30, 0.33.4) —
  porquê: sem a Play não há venda, e o Prêmio não depende da Play (é resgate no servidor). Antes a aba
  inteira fechava no iPhone e o Prêmio sumia junto. A vitrine é gateada pela Play (`billing.available`),
  não pelo servidor: nunca aparece no iPhone, mesmo com a venda ligada. Pedido e aprovado pelo John na #30.
- **18/10 · Anúncios premiados opcionais aprovados** (#29): camada nova **SDK Google Mobile Ads
  (AdMob)** aprovada pelo John; só anúncio que o jogador escolhe ver, para ganhar algo ou multiplicar
  um prêmio (nada de anúncio forçado nem banner). Recompensa decidida pelo servidor (SSV). Conta
  AdMob criada por ele; app e blocos configurados; `app-ads.txt` na raiz `johncoelho.github.io`.
- **18/10 · Anúncios premiados implementados atrás de `ads_enabled = 'false'`** (#29, 0.34.0) — porquê:
  o servidor e o app sobem juntos e testáveis com anúncio de teste, e o John liga quando a Play Console
  ("Contém anúncios", Segurança dos dados) estiver pronta, longe do pedido de produção. Correções do
  Tech Lead aplicadas: Edge `ads-reward` com `verify_jwt` desligado (o Google não manda JWT; a
  segurança é a assinatura); assinatura conferida sobre a query crua; o vale guarda o valor no
  servidor; limite contado na aplicação (fechar o anúncio não gasta); `dobroes_purchases` reaproveitada
  com `kind = 'ad'` (um caminho de crédito só); milha sob o lock e com `miles_cap`; SDK e UMP só depois
  do primeiro toque; unidade de teste em debug, `.preview`, QA e na conta do John.
- **18/10 · `play-services-ads` 25.2.0 em vez da 25.5.0** (#29) — porquê: da 25.3.0 em diante o SDK traz
  metadata Kotlin 2.3 e não compila com o Kotlin 2.1.0 do projeto. Subir Kotlin mexe em Compose
  Multiplatform e no iOS; `-Xskip-metadata-version-check` esconderia incompatibilidade de qualquer
  biblioteca. Decisão do coordenador com o John presente: ficar na maior 25.x que compila (a medição
  inicial apontou 25.3.0; com o código usando o SDK, a 25.3.0 também falhou e ficou a 25.2.0).
- **18/10 · Permissão `AD_ID` fora do app enquanto o anúncio está desligado** (#29, 0.34.0) — porquê: o
  SDK injeta a permissão e a Play recusa o envio quando a declaração de ID de publicidade diz "não
  usa"; a declaração só muda perto da ativação. Consequência: ligar os anúncios exige uma versão nova
  que devolva a `AD_ID` e a declaração de ID de publicidade/Segurança dos dados atualizada na Play;
  virar `ads_enabled` sozinho não basta (sem `AD_ID` o anúncio sai com ID de publicidade zerado).
- **18/10 · Conta de teste de anúncio fora do repositório** (#29) — porquê: o John está na lista da
  Play, não em `manual`, e o repositório é público. QA vem de `allowed_players.source = 'qa'`; o John,
  de `app_config.ads_test_emails`, preenchido direto no banco.
- **18/10 · Vitória por ausência do outro não dobra** (#29) — porquê: a SPEC exclui; `Match.wonByAbsence`
  marca o `abandon` do lado que ficou. Ref da vitória: id gerado no aparelho contra a IA (o servidor
  limita a 2 por dia e 150) e id da sala no online (o servidor confere participação e tiro).
- **18/10 · Loja beta com as duas opções** (#28): prêmio diário grátis resgatado no servidor (sem
  Play) e pacotes pagos com cobrança real para os testadores; lista de licença só com as contas QA.
- **18/10 · Edge `dobroes-verify` redeployada com `p_origin`** (#28, correção sem versão de app) —
  porquê: a versão 5 em produção não tinha o commit 828eda1 (`origem` e `p_origin`), então a compra
  de teste de pacote caía no legado (`premio_only`) e não creditava (QA da 0.33.4, critério 3). A
  conferência de 18/10 afirmou "versão 5 igual ao repositório" sem comparar o código; isso estava
  errado, e o erro fica registrado. Corrigido com `supabase functions deploy` (versão 6) e diff vazio
  contra o repositório. Proposta do desenho, aguarda OK do John: depois de qualquer mudança em Edge, conferir implantado = repositório.
- **19/10 · iPhone não bloqueia a esteira**: o iOS só é compilado para adiantar; passou no QA do
  Android, a issue sai da fila. Teste em iPhone fica no backlog (John).
- **19/10 · Anúncios em modo "teste"**: `ads_enabled` ganha o valor `'test'` (liga só para as contas de
  anúncio de teste: QA e `ads_test_emails`), para o QA testar sem jogador real ver botão. Ligado em
  `'test'` agora; `'true'` (todos) só com o OK do John e a versão com a AD_ID de volta.
- **19/10 · Conta de teste recebe bloco real só em emulador** (#32, 0.34.3) — porquê: o bloco de
  exemplo não dispara o SSV, então o QA nunca via o vale `applied`. No emulador o Google já trata o
  aparelho como teste (sem clique real), e o bloco real é a única forma de o SSV creditar. Aparelho
  físico segue com o exemplo, e a produção ('true') não muda. Desenho aprovado pelo Tech Lead.
  Pré-requisito: a URL do SSV colada nos 4 blocos do AdMob (pendente do John).
- **10/10 · Ambiente de teste do banco (branch do Supabase) adiado** — por ora, testes que desfazem
  tudo em produção (skill `supabase-change`).
- **10/10 · Vitória sem combate não rende carreira** (#6, 0.31.0): combate = o vencedor deu pelo
  menos 1 tiro ou usou 1 habilidade, mesma definição no app (`Match.wonWithoutCombat`) e no
  servidor (`award_win_miles` conta `ACT|`/`ABIL|` em `online_messages`). Fecha a brecha de XP,
  dobrões e milha de graça com um amigo saindo no posicionamento. A ranqueada fica de fora para
  não quebrar a soma zero (HISTORY 09–10/10); anular para os dois lados seria outra issue.
- **10/10 · Push "amigo online"** (#19, 0.31.0), padrão ligado como o "Receber convites", com
  anti-spam no servidor (1 por par a cada 3 h, até 5 por dia, em `app_config`) porque push demais
  faz o jogador desligar todas as notificações e perder os convites. Dispara só na transição fora
  → online dentro do `touch_presence`; erro de push nunca derruba a presença. O terceiro argumento
  nulo (app antigo) não muda a opção, para o app antigo não religar o que o jogador desligou.
- **10/10 · Presença da partida online: cálculo separado da chamada de rede** (#8, 0.31.0): a batida
  presa na rede (até ~30 s) segurava o "sem conexão" e o relógio do turno disparava sozinho.
- **10/10 · "Criar senha" para conta só Google** (#9, 0.31.1), aprovado pelo John no lugar de só
  esconder a opção (esconder deixaria o jogador sem jeito de entrar por e-mail se perder o Google).
  Quem decide é o servidor (`account_has_password()`), não `app_metadata.providers`: o GoTrue não
  acrescenta `email` aos provedores quando a conta OAuth cria senha, então a lista mentiria depois.
  Não pede reautenticação: a sessão Google é a prova de identidade e o "Secure password change" do
  Auth está desligado (conferido pelo John). Sem resposta do servidor vale "Trocar senha".
- **10/10 · Superestrutura da linha substitui a da classe e mastro fica dentro do casco** (#21,
  0.31.2): torre e chaminés da linha eram desenhadas por cima da ilha da classe e o mastro eram três
  riscos finos sobre a água; nos prints do John (Loja → Cascos e Camuflagens) isso lia como falha de
  desenho em todas as linhas. Agora torre e chaminés da linha ocupam o lugar das da classe (nunca as
  duas) e há no máximo um mastro por navio, com verga curta dentro do convés. A Fantasma mantém as
  chaminés da classe (orquestrador da esteira: não mudar a silhueta comprada além do que o bug
  pede); porta-aviões não recebe torre de linha (cairia na pista). A decisão do desenho fica numa
  função pura (`superstructurePlan`) para ser testável sem teste de pixel.
- **10/10 · Desenho animado não cria Brush por quadro e separa o estático do animado** (#22, 0.31.3):
  jogador relatou animação travada no Android. O menu criava centenas de degradês por quadro e a
  varredura do radar redesenhava o tabuleiro inteiro; no Android cada `Brush` novo é um shader novo
  na thread principal (no iPhone, Skia/Metal e ProMotion disfarçavam). Escolhido cachear com
  `drawWithCache` e separar camadas com `graphicsLayer` (girar a camada da varredura em vez de
  redesenhar), sem dependência nova e sem mudar nada visível. Virou padrão no README → Especificação
  técnica.
- **11/10 · Contas dos testes automáticos do Google ficam fora do ranking e da partida rápida** (#23,
  0.32.0): cada envio à Play criava contas da revisão (Firebase Test Lab) que entravam no placar.
  O app relata `firebase.test.lab` e só o servidor marca `profiles.is_test`, num sentido só. Na
  partida rápida, **separar em vez de bloquear** (padrão do desenho, sem resposta do John): o robô
  continua achando partida entre robôs, sem erro novo para a revisão. **Log de diagnóstico sem modelo
  do aparelho** (`device_checks` guarda só conta, versão, plataforma e a marca), para não mexer na
  seção de Segurança dos dados da Play. Limpeza das contas antigas só com a lista conferida pelo John.
- **12/10 · Só testador registrado vale como jogador** (#23, 0.32.1): a revisão da 0.32.0 criou conta
  sem `firebase.test.lab` e com Gmail comum, então nem a marca do aparelho nem o domínio separam robô
  de gente. Decisão do John: vale quem está na lista "Testadores fechados" da Play, quem abre o app
  no iPhone (o `.ipa` não passa pela revisão do Google) ou quem é exceção manual dele; o resto é
  `is_test`. **Lista no banco (`allowed_players`), nunca no repositório**, que é público: a migração
  cria só a estrutura e os e-mails são carregados por SQL não versionado. **Interruptor
  `allowlist_enforced`** para a estrutura subir inerte e a carga vir depois, sem janela marcando todo
  mundo. **QA01/QA02 como conta de teste** (ranqueada de QA deixa de valer pontos; a fórmula do Elo
  fica coberta pelo bloco que desfaz). **`test_lab` vira só diagnóstico** com a regra ligada. iPhone
  vale pela plataforma informada pelo app: risco aceito durante o beta.
- **18/10 · Novidades com uma linha por minor, sem patch** (#31, 0.34.1): a tela parou na 0.25.0 porque
  a lista não ganhou linha ao subir as minors. Correção: linhas 0.26.0 a 0.34.0 com os textos de
  `app_releases`, e a regra da minor no `dev-cycle` e no `play-store-release`, com `ReleaseNotesTest`
  para quebrar se faltar linha. **Ler `app_releases` pela tela continua pendente** (é melhoria; o John
  decide se a fonte passa a ser o banco e se a linha vira por patch).

## Fase 5 · Produção na Play (25/10)

- **25/10 · Produção enviada para revisão** — versão 91 (0.34.4), liberação total, todos os países
  (177 + resto do mundo, o jogo tem pt/en/es). Acesso à produção liberado depois dos 14 dias do teste
  fechado (respostas do questionário no histórico da conversa).
- **25/10 · Jogador válido na produção** (#35, aprovado pelo John) — porquê: com a allowlist ligada,
  todo jogador da loja viraria `is_test`. Regra nova: todo mundo vale; só robôs da Google e contas QA
  ficam de fora. Testadores registrados ficam com o que já ganharam; o brinde novo passou a ser só de
  QA e do John (item abaixo).
- **25/10 · Fim dos brindes, exceto QA01, QA02 e o John** (#35, decisão do John, substitui a de manter o
  Prêmio para testadores, tomada no mesmo dia) — testadores ficam com tudo o que ganharam; daqui para
  frente todo mundo paga por tudo. Prêmio Beta e grátis só para as 2 contas QA e a conta do John
  (e-mail só no banco). **Implementado na 0.34.5:** `beta_eligible()` = conta QA
  (`allowed_players.source = 'qa'`) ou e-mail de `app_config.beta_gift_emails` (valor só no banco,
  como `ads_test_emails`). Vale para o Prêmio Beta, a vitrine beta e o Prêmio legado pela Play.
  Saldo, itens e medalha Beta Tester já ganhos não mudam. Ensaio em `supabase/tests/brindes_ensaio.sql`.
- **25/10 · Aviso de lançamento na entrada do jogo, só no Android (#36, 0.34.6)** — o John quer agradecer
  aos testadores do beta e avisar que, daqui para frente, tudo é pago (exceto QA01, QA02 e a conta dele).
  Popup uma vez por conta, ligado por `app_config.launch_notice_enabled` (nasce `'false'`; liga quando a
  Play aprovar a produção, sem versão nova). Duas variações: testador (lista play/manual/qa, aparelho
  iPhone, ou medalha Beta Tester com corte) e jogador novo. Não usa `beta_eligible()`, que desde a 0.34.5
  vale só para QA e John. Com `beta_ends_at` vazio a medalha não conta, porque a medalha é dada a toda
  conta nova enquanto não há corte.
- **25/10 · O aviso de lançamento não aparece no iPhone, com nenhum texto (#36)** — decisão do John, que
  substituiu a ideia de um título neutro no iOS: o texto é sobre a Google Play e o iPhone segue por
  .ipa. Feito nos dois lados: o app no iOS nem consulta o servidor, e `launch_notice_status` devolve
  `enabled = false` para a plataforma `ios` (vale também para qualquer versão que pergunte).
- **25/10 · `is_test` só por robô da Google; todo jogador da loja vale** (#35, 0.34.5) — porquê: a lista
  deixou de decidir quem é jogador. Robô = e-mail de login `nome.00000@gmail.com` (ou `firebase.test.lab`);
  conta QA fica fora da rotina. Ensaio em `supabase/tests/allowlist_ensaio.sql`. Passada
  `refresh_all_is_test(true)` rodada em 25/10: 0 contas mudaram (15 jogadores, 122 teste).
- ~~**25/10 · Prêmio Beta continua só para testadores** (decisão do John, de 3fe3750) — 6.500/dia, 1×/dia,
  para quem está em `allowed_players` (play/manual/qa); conta nova da loja não recebe.~~ Substituída pela
  decisão de 7afa37e (fim dos brindes, exceto QA e John).
- **25/10 · Depois do lançamento, ninguém novo entra como `play` na lista** (#35, aceite da decisão do
  John) — porquê: a lista deixa de ser a porta de entrada da conta de jogador (todo mundo vale) e a
  lista da Play não dá mais brinde (só QA e o John). Fica para o gate da licença e a conferência de
  instalação de quem já estava no teste fechado.
- **25/10 · Beta fecha na aprovação da produção** (#35) — porquê: o John decidiu; `beta_ends_at` fica nulo até lá.
- **25/10 · Venda aberta só depois do saldo no servidor** (#34) — `real_billing_enabled` segue
  desligado na produção até o saldo autoritativo; nada cobra sem creditar enquanto isso.

---


- **25/10 · Play Age Signals 0.0.4 entra na stack: ECA Digital exige ler a faixa etária vinda da loja; a
  idade não sai do aparelho** (#49, aprovado pelo John; 0.34.7) — porquê: os termos da API proíbem usar o
  dado em publicidade, perfilamento ou analytics; desligar a personalização para menor é cumprimento da lei.
  No Prefs fica só a faixa convertida e a hora. API beta que já mudou na 0.0.4 (`requestAgeSignalsAccess` +
  `checkAgeSignals`): versão fixa; trocar só com novo teste. Decisões aplicadas (recomendação do Tech Lead,
  o John pode mudar): idade desconhecida conta como menor (anúncio não personalizado; custa eCPM); o aviso da
  Play só aparece no primeiro toque em anúncio; `ads_min_app_version` no servidor, pronta e **não ligada**.
  iPhone fora até a App Store (Declared Age Range da Apple, issue própria).
- **25/10 · Sem item aleatório pago, nunca (ECA Digital)** (#49) — pacotes, Prêmio, anúncio, diário e oferta
  do dia são determinísticos; recompensa ou compra de conteúdo aleatório fica proibida.
- **25/10 · Produção com liberação gradual e promoção pelo Release** (#39, aprovado pelo John) — porquê: com
  a produção no ar, o push na `main` continua indo só para a Alpha; a versão que o QA aprovou vai para a
  produção em 20% → 50% → 100% (≥ 24 h cada, contadas do *Published*) sem passo manual dele. Workflow manual
  `promote-production.yml` + `tools/play-rollout.sh` pela API de edições, com o versionCode explícito (o
  `promoteReleaseArtifact` do Gradle Play Publisher promove o último da faixa e pegaria a versão errada se outro
  lote subisse durante o QA). Sem cron: toda decisão passa pela esteira, que comenta na issue. Halt automático
  (falha > 1,09% ou ANR > 0,47% com ≥ 100 usuários, ou P0) sem esperar o John. Variável `PLAY_PRODUCTION_LIVE`
  fica `false` até o *Published* da produção 91, para não substituir um envio em revisão. Etapa "Em produção"
  no quadro: o QA deixa de fechar a issue. iOS fora (segue o `.ipa`).

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
