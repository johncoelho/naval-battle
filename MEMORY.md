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
- Issues do GitHub: trabalhar sozinho ao juntar 5 abertas, ou quando ele pedir.
- Antes de fechar uma SPEC de melhoria, avisar se há bugs de jogador pendentes para incluir.

## 3. Estado atual (10/10/2026)

- App **0.30.1 (72)**. Android no teste fechado da Play (faixa Alpha), iOS por `.ipa` (Sideloadly).
- Teste fechado: **12 testadores inscritos**; 14 dias seguidos até poder pedir produção (≈ 23/10).
  Contas QA não contam.
- **Apple Developer** pago em 08/10, aguardando ativação. Depois: chave da API do App Store Connect
  (o John coloca como secret), app no App Store Connect, envio ao TestFlight **ao lado** do `.ipa`.
  Login com Google mantido no iPhone por ora (Apple depois); loja de dobrões simulada mantida;
  **`.ipa` do Sideloadly segue ativo** até a versão da Apple ter tudo.
- Ranqueada: soma zero, força do adversário antes da partida, 3 por dia contra o mesmo adversário.
- Design system no Claude Design criado em 10/10.

## 4. Pendências com o John

- Avisar quando a conta Apple ativar.
- Trocar as senhas das contas QA (a da qa01 passou pelo chat; a da qa02 apareceu na tela).
- Opcional: `gh auth login` no terminal para o CI ser acompanhado com menos voltas.

## 5. Backlog combinado (adiado de propósito)

- Preferências de tabuleiro: cor do oceano, espessura da grade, cor do alvo.
- Vitória por desistência no posicionamento ainda rende XP/dobrões (limite de 3/dia por adversário);
  avaliar zerar recompensa nesse caso.
- Busca de amigos mostra "Pedido enviado" para quem já virou amigo até buscar de novo.
- Relógio do turno continua correndo sem conexão (README diz que para).
- "Trocar senha" aparece em conta só-Google.
- Rede local sem botão Cancelar durante o anúncio.
- Google Play Billing (dobrões reais), Sign in with Apple, push no iOS (APNs).
- Endurecer tokens com `EncryptedSharedPreferences`.

## 6. Ambiente e armadilhas conhecidas

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
