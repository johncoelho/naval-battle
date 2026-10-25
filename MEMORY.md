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

- **Anúncio de teste no emulador (#32, 0.34.3):** conta de teste (`'test'`) recebe o bloco real só em emulador,
  para o SSV creditar no QA. Pré-requisito: colar o SSV nos 4 blocos (ver #29).
- **Anúncios premiados (#29, 0.34.0):** servidor (`supabase/ads.sql`, migração `ads_rewarded_ssv`),
  Edge `ads-reward` (versão 1, `verify_jwt` desligado) e app prontos, **desligados**
  (`ads_enabled = 'false'`). Falta: colar a URL `https://cwtslesnthbenxswdcbv.supabase.co/functions/v1/ads-reward`
  no SSV dos 4 blocos do AdMob; mensagem GDPR do UMP; e-mail do John em `app_config.ads_test_emails`
  (só no banco); QA com anúncio de teste (ADS-01 a ADS-18); Play Console ("Contém anúncios", ID de
  publicidade, Segurança dos dados) longe do pedido de produção; ligar só com o OK do John. SDK
  25.2.0 (25.3+ exige Kotlin 2.3). **Ligar não é só virar `ads_enabled`:** a 0.34.0 tira a permissão
  `AD_ID` do manifest (`tools:node="remove"`); antes de ligar, versão nova sem essa remoção e a
  declaração de ID de publicidade e a Segurança dos dados atualizadas na Play.
- **Diagnóstico da conferência de compra (#27, 0.33.1):** Edge `dobroes-verify` registra etapa e motivo
  em Logs da Edge, sem segredo. Prova: log de `secret_ausente` após o deploy; a compra de teste (DOB-07)
  segue com o John (secret e licença).
- **iPhone com Prêmio Beta (#30, 0.33.4):** aba Dobrões no iPhone mostra só o Prêmio do testador;
  pacotes ocultos (sem Play). Pendente: John confirma no QA do DOB-11 que milhas com dobrões aparecem
  no iPhone (decisão: manter) e que não-testador segue com "chega em breve".
- **Loja beta de dobrões (#28, 0.33.3):** Prêmio Beta por RPC `claim_premio_beta` (sem Play, 6.500 1×/dia);
  vitrine paga para testador com `beta_store_enabled` **desligada** (sem secret, pacote cobraria e não
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
- **iPhone na App Store:** aguardando a ativação da conta Apple Developer (paga em 08/10). Depois:
  chave da API do App Store Connect como secret, app no App Store Connect, TestFlight **ao lado** do
  `.ipa` do Sideloadly (que segue ativo até a versão da Apple ter tudo).
- **Produção na Play (25/10):** versão 91 (0.34.4) em revisão, todos os países. **0.34.5 (#35)** traz a
  regra de jogador válido (só robô da Google e QA ficam fora de `is_test`). Funções aplicadas no banco;
  a passada de produção (`refresh_all_is_test(true)`) **espera o OK do John**. Brindes: decidido em
  7afa37e (grátis só QA01, QA02 e John), implementação pendente (ver HISTORY, 25/10). Aviso de lançamento
  (#36, ligar `launch_notice_enabled` quando a Play aprovar). Venda aberta só depois do saldo no servidor
  (#34). CI ainda publica só na Alpha (falta decidir a promoção).

## 4. Decisões recentes (detalhe e porquê no HISTORY)

- 25/10 · `is_test` só por robô da Google; todo jogador da loja vale (#35, 0.34.5): a lista deixa de
  decidir is_test e QA fica fora da passada. Brindes só QA e John: decidido em 7afa37e, implementação pendente.
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
- 12/10 · Só testador registrado vale como jogador (#23, 0.32.1): lista da Play, iPhone ou exceção
  manual; o resto é `is_test`. Lista em `allowed_players`, **nunca no repositório** (público). QA01/QA02
  são conta de teste (ranqueada deles não vale pontos).
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

- Avisar quando a conta Apple ativar.
- **Loja beta (#28), antes de ligar a vitrine para testador:** (1) feito em 18/10: Edge `dobroes-verify` versão 6, igual ao repositório;
  (2) secret `PLAY_BILLING_SERVICE_ACCOUNT`; (3) os 4 produtos no Play Console; (4) gate
  `select * from public.dobroes_gate_licenca();` vazio antes de mudar a lista de licença para só QA01/QA02;
  (5) `premio_beta_enabled` já está `'true'` em produção (ligada antes do lote; o John confirma na issue #28);
  `beta_store_enabled` segue `'false'` e só liga depois de 2 e 3. Textos novos (aviso de cobrança para testador, "Pacotes pagos") aguardam o OK do John.
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
- #23 (12/10) e #35 (25/10): a regra de `is_test` agora é só robô da Google (`is_robot_account`); a
  lista (`allowed_players`) só decide benefícios. Ensaio em `supabase/tests/allowlist_ensaio.sql`. Em
  produção, a passada `refresh_all_is_test(false)` deu 0 a virar teste e 0 a voltar (120 robôs já são
  teste, 15 jogadores, 2 QA): a passada de produção não muda nada hoje e espera o OK do John.
  Testador novo na Play = passo da skill `beta-testers`.

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
