# Memória do projeto

O que precisa ser lembrado em **toda** sessão, do mais importante para o menos. Curto de propósito:
o detalhe mora no [README](README.md#especificação-técnica), no [HISTORY](HISTORY.md) e nas skills.
Atualizar quando surgir regra, preferência ou fato novo; apagar o que deixar de ser verdade.

## 1. Regras de ouro (nunca quebrar)

1. **Falar com o John em português do Brasil**, inclusive status e resumos.
2. **Todo push na `main` publica** (Play faixa Alpha + `.ipa` + site): subir `versionCode`/
   `versionName` e o `CFBundleShortVersionString` do iOS, e escrever as notas da Play
   (`composeApp/src/main/play/release-notes/pt-BR/default.txt`, ≤ 500, sem acento) **no mesmo commit**.
   Exceção: commit só de documentação/skills/`site/` leva `[skip ci]` na mensagem e não sobe versão (`site/`: depois `gh workflow run pages.yml --ref main`).
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
9. **Nenhuma recompensa ou compra de conteúdo aleatório** (ECA Digital, #49): prêmio é sempre valor e item
   conhecidos antes. A idade vinda da Play nunca sai do aparelho (só escolhe o tipo de anúncio).
10. **Banco: o arquivo em `supabase/` é a fonte da verdade**; mudança vai no arquivo e é aplicada como
   migração com o mesmo conteúdo, testada com bloco `do $$ … raise exception` que desfaz tudo.

## 2a. Esteira de engajamento (v1, 0.35.0)

- **#41 treino contra a IA e #46 aviso de atualização** (0.35.0): QA PRT-01 a PRT-06 e UPD-10 a UPD-12 no
  `QA_TEST_PLAN`. Próximas: #43 (v2), #42, #44, #47, #45, #48. Contra a IA segue zero dobrões.
- **#48 Play Games** (0.41.0) entrou **desligada**. Falta o John configurar o Play Console (passo a passo em
  `docs/BUILD.md`) e colar o XML na #48; aí vem a versão de ativação (`pgsAppId` + `games-ids.xml`) e o QA PGS-02 a PGS-06.

## 2. Como o John quer trabalhar

- **Autonomia total no release:** do pedido à Play sem pedir licença (skill `dev-cycle`); ele só
  instala e testa. Verificar o CI de verdade antes de dizer que está no ar.
- Processo que se repete vira **skill** em `.claude/skills/`.
- Com ele presente na conversa: **disparar a rodada da esteira na hora** quando algo entra na Fila
  (ritmo do agendamento só se ele pedir).
- **Contas QA01/QA02:** ativas **só durante o teste** (`is_test = false` no início, `true` no fim,
  sempre); fora disso, fora do ranking. São contas válidas do beta: **nunca remover**.
- Quando algo depende dele: **comando pronto** num bloco de shell (botão Run), um por bloco, só para
  ele executar.
- "Bateria de testes completa" = `docs/QA_TEST_PLAN.md` (skill `qa-full-test`).
- Prévia para aprovação é **build local** (APK `.preview`), nunca branch (branch também publica).
- Navegador: painel embutido só para tarefa autônoma; o que ele acompanha vai no Chrome dele.
- Botões: título em cima à esquerda, legenda embaixo à direita, nunca lado a lado.
- Esteira agêntica (`docs/ESTEIRA.md`): **bug vai direto para a Fila e é resolvido logo**; melhoria
  só entra na Fila com aprovação dele (arrasta o cartão ou comenta "aprovado"). **Mudar regra que já
  está valendo é melhoria, nunca bug** — sempre com a aprovação dele.
- Antes de fechar uma SPEC de melhoria, avisar se há bugs de jogador pendentes para incluir.

## 3. Assuntos em andamento

- **Denunciar, bloquear e filtro de nome (#58, 0.42.0):** migração `player_safety` (`supabase/safety.sql`) a aplicar só depois do APROVADO
  do Tech Lead e antes de ligar o app novo na Play (o ensaio `supabase/tests/safety_ensaio.sql` roda antes). Rodada diária da esteira: `select * from
  public.reports_pending();` (propor ao John; nada aplicado sem o OK dele, exceto a troca automática de nome com 3 denunciantes) e
  `select * from public.purge_moderation();` (12 meses). QA: SAF-01 a SAF-14.

- **Política de privacidade e regras da comunidade (#57):** `site/privacy.html` (pt/en/es em âncoras) e `site/terms.html`
  prontas; a página só vale depois de publicada (`gh workflow run pages.yml --ref main`, conferir pela URL). Mudança só em
  `site/` leva `[skip ci]` e não sobe versão. O texto de denúncias já está lá, marcado "0.42.0": a #58 precisa cumpri-lo
  (nome, motivo, onde, data; só o desenvolvedor lê; 12 meses; filtro de nome; filtro e troca automática; demais denúncias assim que possível). **Pendente do John:**
  Apple (Privacidade do app: e-mail, conteúdo do usuário, jogo, ID do usuário, diagnóstico; Tracking não) e Segurança dos
  dados da Play (push, compras, ID do app e, com a #40, o ID de publicidade). A #40 só liga os anúncios com a página no ar.

- **Desafio da semana (#47, 0.40.0):** migração `weekly_challenge` (`supabase/weekly.sql`) a aplicar só depois do APROVADO
  do Tech Lead e antes de ligar o app novo na Play. QA: WEK-01 a WEK-06; `weekly_enabled` é o interruptor.

- **Pedido de avaliação na Play (#45, 0.39.0):** Play In-App Review 2.0.2 (aprovada em 25/10). Sem banco. QA: REV-01 a
  REV-04 (build `-PreviewFake=true` para o fluxo falso; APK `-PqaSign` para a lib real).
- **Convide um amigo (#44, 0.38.0):** migração `referral` (`supabase/referral.sql`) a aplicar só depois do APROVADO do
  Tech Lead e antes de ligar o app novo na Play. QA: REF-01 a REF-07, com `referral_allow_qa = 'true'` só durante o
  teste e `'false'` no fim (como o `is_test` das QA).

- **Aviso de retorno e prêmio de retorno (#42, 0.37.0):** migração `daily_return_bonus` (`supabase/daily.sql`) a
  aplicar só depois do APROVADO do Tech Lead e antes de ligar o app novo na Play. QA: RET-01 a RET-06 (relógio do
  emulador adiantado; `last_checkin` recuado 5 dias por SQL na conta QA).

- **Primeira partida guiada e presente (#43, 0.36.0):** migração `welcome_gift` (`supabase/welcome.sql`) a aplicar
  só depois do APROVADO do Tech Lead; ela fixa `welcome_gift_since` na hora da aplicação (só conta criada depois
  recebe). Aplicar antes de ligar o app novo na Play. QA: WEL-01 a WEL-06, com `welcome_gift_reset(uuid)` na conta QA.

- **R8, edge-to-edge e telas grandes (#37, 0.34.12):** release minificado, `navalSafeArea`, conteúdo em até 600 dp
  e batalha lado a lado em tablet. R8 quebra só no release: antes de promover, bateria no app da Play (R8-01 a
  R8-06, BIG-01 a BIG-09, EDGE-01 no `QA_TEST_PLAN`) e conferir o mapping no App bundle explorer. O aviso do SDK
  de anúncios saiu na #38 (0.34.13).

- **API de idade do AdMob e hash do Gradle (#53/#54, 0.34.14):** `setAgeRestrictedTreatment` no lugar de TFUA/TFCD
  (mapeamento em `AgeRules`) e `distributionSha256Sum` no wrapper. QA: ADS-26 com `-PageFake=child|teen|adult|unknown`
  em QA01/QA02 (anúncio de teste carrega e paga). Versão 0.34.14 (101).

- **Kotlin 2.3 e SDK de anúncios (#38, 0.34.13):** Kotlin 2.3.21, CMP 1.10.3, AGP 8.13.2, Gradle 8.14.3,
  `play-services-ads` 25.5.0. Só o `ios.yml` pega o Kotlin/Native: `iosSimulatorArm64Test` e o `.ipa` verdes antes de
  promover; se o Xcode do runner quebrar o link, fixar o Xcode no `ios.yml` precisa do OK do John. Bateria completa
  no app da Play (anúncio premiado, compras, online no QA01/QA02) e a ação de edge-to-edge sumir do Release dashboard.

- **ECA Digital (#49, 0.34.7):** Play Age Signals 0.0.4 lê a faixa no primeiro toque em anúncio; menor e idade
  desconhecida = anúncio não personalizado (tratamento TEEN/CHILD, `npa`, PG/G). Gate `ads_min_app_version` pronto e **vazio**
  (migração `ads_min_version` a aplicar na publicação; ligar `0.34.7` só com o OK do John e a 0.34.7 na Play).
  Falta o John conferir no Play Console: Classificação (IARC/ClassInd) e Público-alvo. QA: ADS-19 a ADS-21
  (`-PageFake=teen`).

- **Anúncio de teste no emulador (#32, 0.34.3):** conta de teste (`'test'`) recebe o bloco real só em emulador,
  para o SSV creditar no QA. Pré-requisito: colar o SSV nos 4 blocos (ver #29).
- **Anúncios premiados (#29, 0.34.0):** servidor (`supabase/ads.sql`, migração `ads_rewarded_ssv`),
  Edge `ads-reward` (versão 1, `verify_jwt` desligado) e app prontos, **desligados**
  (`ads_enabled = 'false'`). Falta: colar a URL `https://cwtslesnthbenxswdcbv.supabase.co/functions/v1/ads-reward`
  no SSV dos 4 blocos do AdMob; mensagem GDPR do UMP; e-mail do John em `app_config.ads_test_emails`
  (só no banco); QA com anúncio de teste (ADS-01 a ADS-18); Play Console ("Contém anúncios", ID de
  publicidade, Segurança dos dados) longe do pedido de produção; ligar só com o OK do John. SDK
  25.5.0 (Kotlin 2.3.21 desde a 0.34.13, #38). **Ligar não é só virar `ads_enabled`:** a 0.34.0 tira a permissão
  `AD_ID` do manifest (`tools:node="remove"`); antes de ligar, versão nova sem essa remoção e a
  declaração de ID de publicidade e a Segurança dos dados atualizadas na Play.
- **Diagnóstico da conferência de compra (#27, 0.33.1):** Edge `dobroes-verify` registra etapa e motivo
  em Logs da Edge, sem segredo. Prova: log de `secret_ausente` após o deploy; a compra de teste (DOB-07)
  segue com o John (secret e licença).
- **iPhone com Prêmio Beta (#30, 0.33.4):** aba Dobrões no iPhone mostra só o Prêmio do testador;
  pacotes ocultos (sem Play). Pendente: John confirma no QA do DOB-11 que milhas com dobrões aparecem
  no iPhone (decisão: manter) e que não-testador segue com "chega em breve".
- **Loja beta de dobrões (#28, 0.33.3):** Prêmio Beta por RPC `claim_premio_beta` (sem Play, 6.500 1×/dia);
  vitrine paga para testador com `beta_store_enabled` (nasceu **desligada**; em produção está `'true'`, conferido em 25/10; desde 0.34.5 a vitrine só aparece para QA e o John; sem secret, pacote cobraria e não
  creditaria). Migração aplicada no banco; Edge `dobroes-verify` redeployada em 18/10 (versão 6, igual ao repositório). Gate:
  `dobroes_gate_licenca()` antes de tirar qualquer testador da licença (17 ainda sem 0.33.3 em 18/10).
- **Compras de dobrões pela Google Play (#15, 0.33.0):** servidor e app prontos (migração
  `dobroes_billing`, Edge `dobroes-verify`, Prêmio Beta, aba sem loja simulada). Os interruptores
  `real_billing_enabled` nasce **desligado**; `premio_beta_enabled` está **ligada** em produção (`'true'`,
  conferido em 18/10; já estava ligada antes do lote 0.33.3, quem ligou não se sabe pelo repo; o John
  confirma na issue #28). Venda para jogador comum só
  depois do saldo autoritativo no servidor (#15 parte e) e da produção na Play (≈ 23/10). Falta o
  John: os 4 produtos, a lista de licença, a conta de serviço de billing e o secret (ver pendências).
- **Conferência de instalação por push (#26, 0.33.2):** Edge `push-check` + `testers_install_report()`
  (só lista da Play). A `push` marca desinstalado em vez de apagar. **Pendente do John:** incluir o
  passo 0 (`select public.push_check_daily();`) na skill `esteira`; sem isso a conferência diária não
  roda sozinha (a edição foi negada, não tentar de novo). Falta o QA dos CHK-05 e CHK-06.
- **Push diário (15/10):** tarefa `push-diario-naval-battle`, 12h, 7 mensagens por dia da semana,
  travas de horário (11h–15h) e de uma vez por dia. Push manual: nunca à noite.
- **Esteira agêntica (10/10):** agentes em `.claude/agents/`, skill `esteira`, `docs/ESTEIRA.md`.
  Quadro: https://github.com/users/johncoelho/projects/1. Tarefa agendada `esteira-naval-battle`
  às 7h, 12h e 18h e `esteira-qa-naval-battle` às 7h, 12h e 18h (só com o app aberto). Primeira entrega: #7 na 0.30.3.
- **Base de conhecimento e qualidade (10/10):** CLAUDE.md, HISTORY.md, MEMORY.md, especificação técnica
  no README, design system no Claude Design, skills; testes de unidade (`commonTest`) e trava de
  release no CI (`tools/release-check.sh`).
- **iPhone na App Store (#56):** conta ativa, app criado no App Store Connect. Workflow `ios-release.yml` pronto
  (sem fastlane; ensaio em 3 níveis em docs/BUILD.md). **Pendente do John:** chave da API (papel **Admin**) e os secrets
  `ASC_KEY_ID`, `ASC_ISSUER_ID`, `ASC_KEY_P8` (`APPLE_TEAM_ID` já é variável do repositório). Depois do primeiro `enviar=true`
  que der certo: `gh variable set IOS_APPSTORE_LIVE --body true`. O `.ipa` do Sideloadly segue até a versão da Apple ter tudo.
- **Entrar com a Apple (#13, P0):** código pronto no iPhone; sai com a #56 e a #58. Console feito em 26/10 (App ID e
  provedor Apple no Supabase com Client ID `br.com.navalbattle`, secret vazio). **Pendência do John:** (1) Apple Developer >
  Keys > "+" > nome `Naval Battle Sign in with Apple`, só Sign in with Apple, Primary App ID `br.com.navalbattle`; anotar o
  Key ID e baixar o `AuthKey_<KEYID>.p8` (download único, fora do repositório). (2) Secrets da Edge: `supabase secrets set
  --project-ref cwtslesnthbenxswdcbv APPLE_TEAM_ID=G25AHMZJ7Y APPLE_SIWA_KEY_ID=<KEYID> APPLE_SIWA_KEY_P8="$(cat AuthKey_<KEYID>.p8)"`
  (ou pelo painel). (3) **Deploy da Edge, ainda não feito:** `supabase functions deploy apple-revoke --project-ref
  cwtslesnthbenxswdcbv` (`verify_jwt` ligado). **Sem os secrets, a exclusão de conta Apple não revoga o token na Apple**
  (a Edge responde `not_configured` e a conta apaga do mesmo jeito). QA no iPhone pelo TestFlight da #56: APL-01 a APL-10;
  no build do `ios-release.yml`, `codesign` deve listar `com.apple.developer.applesignin`; erro 1000 = entitlement perdida (plano B da #13).
  **Do Dev, depois do rebase sobre a #56:** acrescentar `com.apple.developer.applesignin` à lista (hoje vazia) de entitlements obrigatórias do passo de conferência do `ios-release.yml`.
- **Produção na Play (25/10):** versão 91 (0.34.4) em revisão, todos os países. **0.34.5 (#35)** em
  revisão (regra em §4). Aviso de lançamento (#36, ligar `launch_notice_enabled` quando a Play aprovar).
  CI publica só na Alpha; a promoção para a produção (#39) está pronta: `promote-production.yml` + `tools/play-rollout.sh`, 20% → 50% → 100% (docs/ESTEIRA.md). **Pendente do John:** `gh variable set PLAY_PRODUCTION_LIVE --body true` depois do *Published* da 91 (hoje `false`; permissões e Reporting API já feitas). Ação `vitals` no script (#51): lê falha/ANR por versionCode; só `ALARME` pausa.
- **Saldo de dobrões no servidor (#34, 0.34.9):** migração `dobroes_saldo` aplicada (inerte, nenhuma carteira
  aberta; abre por conta quando o 0.34.9 entra). **Pendência** (decisão 8 do John, pode rodar sozinho): com a
  0.34.9 na **produção** e o QA aprovado (bateria completa, casos DOB-30 a DOB-55), conferir os 4 produtos no
  Play Console, rodar `select public.dobroes_open_all();` e só então `real_billing_enabled = 'true'`.

## 4. Decisões recentes (detalhe e porquê no HISTORY)

- 25/10 · Brindes (#35, 0.34.5): grátis e Prêmio Beta só para QA01, QA02 e o John (`beta_eligible` =
  `'qa'` ou `app_config.beta_gift_emails`, e-mail só no banco); testadores ficam com o que ganharam.
- 25/10 · `is_test` só por robô da Google; todo jogador da loja vale (#35, 0.34.5): a lista deixa de
  decidir is_test e QA fica fora da passada. Depois do lançamento ninguém novo entra como `play`.
- 19/10 · Conta de teste ganha bloco real só em emulador (#32, 0.34.3): o exemplo não dispara o SSV.
- 18/10 · iPhone mostra só o Prêmio Beta (#30, 0.33.4): pacotes ocultos no iPhone, a aba não fecha mais.
  Aprovado pelo John na issue.
- 18/10 · Prêmio Beta sai da Play (#28, 0.33.3): RPC `claim_premio_beta`; a vitrine beta nasce desligada
  até o secret e os 4 produtos. Desvio do "já agora" da issue, aceito na rodada.
- 17/10 · Dobrões pela Google Play: biblioteca de billing aprovada; servidor decide e aparelho só
  aplica; loja simulada e limite de R$ 50/dia saem; Prêmio Beta (Tesouro, 1×/dia) só para testador de
  licença; iPhone sem pacotes (#15; o Prêmio entrou no iPhone em 18/10, #30).
- 17/10 · Conferência de instalação (#26, 0.33.2): a `push` marca desinstalado e não apaga token;
  reinstalar zera a marca. Conferência diária pelo passo 0 da `esteira` (sem pg_cron), **pendente** do
  John incluir o passo na skill. Só indicador.
- 12/10 · QA01/QA02 são conta de teste (ranqueada deles não vale pontos). Lista `allowed_players`
  **nunca no repositório** (público). (A regra "só testador registrado vale", de 12/10, saiu em 25/10.)
- 10/10 · Backlog inteiro no GitHub Issues; aqui só assuntos em andamento e decisões.
- 10/10 · Testes de unidade obrigatórios para regra de jogo; bug de lógica corrigido ganha teste.
- 10/10 · CI barra release incompleto (versão, iOS, notas, CHANGELOG).
- 10/10 · SPEC com critérios de aceite para feature média/grande; critérios viram casos de QA.
- 10/10 · Um lote por release: toda a Fila numa versão só (substituiu "uma versão por assunto").
- 10/10 · Ambiente de teste do banco (branch Supabase) adiado.
- 10/10 · Camada nova na stack só com aprovação do John.
- 09–10/10 · Ranqueada soma zero, força do adversário antes da partida, 3/dia contra o mesmo
  adversário; revanche online em sala nova.
- 08/10 · iPhone: Google login mantido (Apple depois), loja simulada mantida, `.ipa` segue ativo.

## 5. Pendências com o John

- **Aviso de lançamento (#36, 0.34.6):** pronto e **desligado** (`launch_notice_enabled = 'false'`); só Android
  (iPhone nunca mostra). Ligar só quando a Play aprovar a produção **e** a 0.34.6 estiver nela:
  `update public.app_config set value = 'true' where key = 'launch_notice_enabled';`. Medalha Beta Tester só
  conta como testador com `beta_ends_at` preenchido.
- Avisar quando a conta Apple ativar.
- **Loja beta (#28), antes de ligar a vitrine para testador:** (1) feito em 18/10: Edge `dobroes-verify` versão 6, igual ao repositório;
  (2) secret `PLAY_BILLING_SERVICE_ACCOUNT`; (3) os 4 produtos no Play Console; (4) gate
  `select * from public.dobroes_gate_licenca();` vazio antes de mudar a lista de licença para só QA01/QA02;
  (5) `premio_beta_enabled` já está `'true'` em produção (ligada antes do lote; o John confirma na issue #28);
  `beta_store_enabled` está `'true'` em produção (25/10; com a regra de 0.34.5 só QA e o John veem a vitrine); pacote real só depois de 2 e 3. Textos novos (aviso de cobrança para testador, "Pacotes pagos") aguardam o OK do John.
- **Dobrões (#15), antes de ligar o Prêmio ou a venda:** (1) criar no Play Console os 4 produtos
  `dobroes_bolsa`, `dobroes_bau`, `dobroes_cofre`, `dobroes_tesouro` (ativos, IDs exatos); (2) contas QA como
  **testadores de licença**, se faltarem; (3) permissões financeiras: o John concedeu
  ao `play-publisher-ci` (17/10 17:57); trocar por conta de serviço própria só para billing é o
  recomendado, aguarda decisão do John; (4) secret `PLAY_BILLING_SERVICE_ACCOUNT` ainda não cadastrado
  na Edge `dobroes-verify`. Depois: `update public.app_config set value = 'true' where key = 'premio_beta_enabled';`.
  Recomendações do Tech Lead aplicadas, aguardam OK do John: saldo no servidor antes da venda, conta
  de serviço só para billing. (iPhone sem pacotes: feito em 0.33.4, #30.)
- Novidades (#31, 0.34.1): tela com uma linha por minor (0.26.0 a 0.34.0). **Pendente do John:** a tela
  ler `app_releases` (melhoria; decidir se a linha passa a ser por patch). Sem OK, fica como está.
- **No dia da aprovação da produção:** gravar `beta_ends_at` com a data e hora da aprovação, horário de
  Brasília (junto com ligar `launch_notice_enabled`, #36, e tirar o Vietnã, #50). Decisão do John:
  `beta_ends_at` fica nulo até lá (hoje o gatilho ainda dá a medalha Beta Tester a conta nova).

## 6. Ambiente e armadilhas conhecidas

- **iOS: constante pública em `commonMain` vira propriedade no `ComposeApp.h`** e colide com macros C (`CHILD_MAX`, `PATH_MAX`, `NAME_MAX`…
  de `<limits.h>`). Só o `ios.yml` pega (o alvo iOS é pulado no Windows). Constante interna: `private`; nome de
  `const val` público: evitar `*_MAX`/`*_MIN` genéricos (0.34.8). Voltou na 0.34.10 (`OPEN_MAX`, macro do Darwin): o
  check de metadados (abaixo) **não pega colisão de nome com macro no `.h`**, só o `ios.yml`. Desde a 0.34.11 o
  `tools/release-check.sh` reprova constante pública (`val`/`var`/`const val` em CAIXA_ALTA, sem private/internal, em commonMain ou iosMain) cujo nome
  esteja em `tools/c-macros.txt`, seja `MAX`/`MIN` ou termine em `_MAX`/`_MIN`. A lista vem dos `#define` dos headers do
  NDK (limits, errno, signal, stdint, stdio, math...) mais nomes Darwin à mão; o iOS reclamou de outro nome: acrescentar lá.
- **API só da JVM no `commonMain` (`toSortedSet`, `String.format`, `java.*`, `synchronized`) só aparecia no CI do iOS**
  (0.34.9, `Wallet.kt`): o alvo iOS é pulado no Windows (`compileKotlinIosSimulatorArm64` = SKIPPED). Pega local:
  `./gradlew :composeApp:compileCommonMainKotlinMetadata` (TMP=D:\tmp). Conferido: com `toSortedSet` falha
  (`Unresolved reference`), sem ele passa. Está na skill `dev-cycle` (§3) e no `tools/release-check.sh` (0.34.10).
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
