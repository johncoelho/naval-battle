---
name: dev-cycle
description: Ciclo completo de desenvolvimento do Naval Battle, do pedido do John até a publicação (Play faixa Alpha, .ipa na release latest, site, app_releases e documentação). Use em TODA mudança de código, banco ou site que vá para a main — feature, correção, ajuste visual ou de texto.
---

# Ciclo de desenvolvimento: pedido → publicação

Todo push na `main` publica (Play, `.ipa`, site). Por isso este ciclo é obrigatório e completo,
mesmo para uma linha de código. A autonomia é total: siga até o fim sem pedir licença; pare só
para decisões que são do John (stack nova, regra de produto ambígua, aprovação de feedback).

## 1. Entender o pedido

- Releia o pedido e confira contra `MEMORY.md` (regras, backlog, estado) e `HISTORY.md` (decisão
  anterior sobre o mesmo assunto). Se contradiz uma decisão registrada, diga e pergunte.
- Feature média/grande: escreva uma **SPEC com critérios de aceite** antes de codar (modelo
  abaixo); o John aprova. Correção pequena: siga direto.
- **Uma versão por assunto.** Não misture recursos independentes no mesmo release: pacote pequeno
  passa mais rápido na revisão da Play e, se algo quebrar, a causa é óbvia. Correções do mesmo
  assunto podem ir juntas.
- Backlog é o **GitHub Issues** (`johncoelho/naval-battle`). Pedido grande dividido: uma issue por
  parte. Ao concluir, feche a issue citando a versão.

### Modelo de SPEC

```markdown
# <Assunto> — SPEC
**Por quê:** o problema do jogador, em uma ou duas frases.
**O que muda:** telas, regras, servidor, textos (com as três línguas quando houver texto novo).
**Fora do escopo:** o que fica para depois (vira issue).
**Critérios de aceite** (cada um vira caso no docs/QA_TEST_PLAN.md):
- [ ] Dado <situação>, quando <ação>, então <resultado visível>.
**Riscos e compatibilidade:** app antigo ainda no ar, regra no servidor, migração.
**Stack:** nenhuma camada nova | precisa de aprovação: <qual e por quê>.
```
SPEC aprovada fica como artefato (link no HISTORY) e os critérios entram no QA_TEST_PLAN no
mesmo ciclo da implementação.
- Antes de fechar uma SPEC de melhoria, rode a skill `feedback-triage` para ver se há bug de
  jogador pendente que caiba no mesmo pacote, e avise.
- Precisa de biblioteca, serviço, plugin ou versão maior nova? **Pare e peça aprovação**, com
  motivo, custo e alternativa sem dependência (README → Governança da stack).

## 2. Implementar no padrão

- Siga **README → Especificação técnica** (estrutura, contratos `expect/actual`, estado, padrões).
- UI: skill `design-system` (tokens, componentes, textos nas três línguas).
- Banco: skill `supabase-change` (arquivo em `supabase/` + migração + teste que desfaz).
- Lógica e interface em `commonMain`; plataforma só nos `actual`. iOS lê booleano do servidor com
  `(x as? Boolean) ?: padrão`.

## 3. Verificar localmente

- **Testes de unidade** (`composeApp/src/commonTest`): rode e mantenha verdes. Regra nova de jogo,
  pontuação ou rede ganha teste; bug de lógica corrigido ganha teste de regressão.
```bash
./gradlew :composeApp:testDebugUnitTest
```

```bash
bash "<scratchpad>/preview.sh" :composeApp:compileKotlinIosSimulatorArm64
```
(ou o equivalente: troca temporária do `applicationId` para `.preview` com `sed`, `assembleDebug`,
`sed` de volta; TMP em `D:\tmp` — docs/BUILD.md). **Nunca** `git checkout --` no
`build.gradle.kts` para desfazer a troca: já apagou edições reais.
- Mudança visual ou de fluxo que o John deve ver antes: instalar o APK `.preview` no Pixel_6 e
  mandar print. App `.preview` não faz login com Google.

## 4. Versão e notas (mesmo commit do código)

- `composeApp/build.gradle.kts`: `versionCode + 1`, `versionName` (patch para correção, minor para
  recurso).
- `iosApp/iosApp/Info.plist`: `CFBundleShortVersionString` igual ao `versionName`.
- `composeApp/src/main/play/release-notes/pt-BR/default.txt`: o que o jogador nota, ≤ 500
  caracteres, **sem acento**.

## 5. Documentação (mesmo commit)

| Arquivo | Quando |
|---|---|
| `CHANGELOG.md` | sempre: entrada nova no topo (`## [0.X.0] — data · título`, "App x.y.z (versionCode N)") |
| `HISTORY.md` | toda decisão de produto/técnica/processo, com o porquê |
| `MEMORY.md` | regra, preferência, estado ou pendência nova; apagar o que deixou de valer |
| `README.md` | comportamento do jogo; Especificação técnica se mudou padrão/stack |
| `docs/DESIGN_SYSTEM.md` + artefato | token, componente ou padrão visual novo |
| `docs/QA_TEST_PLAN.md` | tela ou fluxo novo ganha caso de teste |
| `docs/BUILD.md` | build, assinatura, CI |
| `site/index.html` | recurso que valha aparecer no site |

## 6. Commit e push

- Mensagem em português, sem acento no título, com o número da versão: `Titulo curto (0.X.Y)`,
  corpo com os pontos, e a linha de coautoria pedida pelo ambiente.
- `git push origin main` (conta `johncoelho`).
- **Commit só de documentação/skills** (nada em `composeApp/`, `iosApp/`, `supabase/`, `site/`):
  ponha `[skip ci]` na mensagem. O GitHub não roda os workflows, nada é publicado, e então **não**
  sobe versão nem registra `app_releases` (os passos 4, 7 e 8 não se aplicam).

- O CI barra o release incompleto antes de compilar (`tools/release-check.sh`: versionCode
  maior, iOS alinhado, notas da Play novas/≤ 500/sem acento, CHANGELOG com `App <versão>`) e roda os
  testes (JVM no `android.yml`, simulador no `ios.yml`). Para conferir antes do push:
  `bash tools/release-check.sh origin/main`.

## 7. Registrar a versão no servidor

```sql
insert into app_releases (version_name, version_code, notes_pt, notes_en, notes_es, android_live, ios_live, notify)
values ('0.X.Y', N, '…', '…', '…', true, false, false);
```
Notas com acento nas três línguas. `notify = true` só quando o John quiser push de "versão nova".

## 8. Acompanhar o CI e liberar o iPhone

- CI: `android.yml` (APK + `.aab` + Play), `ios.yml` (framework + `.ipa`), `pages.yml`. Com o `gh`:
  `gh run list -c <sha-completo>` (abreviado não acha) e `gh run watch <id> --exit-status` (em segundo plano). Sem `gh`, pela API pública:
  `curl -s https://api.github.com/repos/johncoelho/naval-battle/actions/runs?per_page=6`
  (laço em segundo plano até os runs do commit terminarem).
- Falhou: ler o log, corrigir, nova versão. Nunca dizer "no ar" sem o CI verde.
- `ios.yml` verde → `update app_releases set ios_live = true where version_code = N`.
- Site: se mudou, abrir a página publicada e conferir.

## 9. Depois da revisão da Play

- A versão passa por revisão do Google (horas). Quando o John avisar que saiu (ou pedir para
  conferir): atualizar QA01/QA02 pela Play e testar o que mudou, nos casos do `QA_TEST_PLAN.md`.
- Achou falha: volta ao passo 2 com versão nova.

## 10. Checklist de fechamento (toda entrega)

- [ ] CI verde (Android e iOS) e `ios_live` marcado.
- [ ] `HISTORY.md` tem as decisões tomadas nesta entrega, com o porquê.
- [ ] `MEMORY.md` reflete os assuntos em andamento, decisões recentes e pendências (e não tem nada
      que deixou de valer).
- [ ] README → Especificação técnica atualizado se padrão, stack ou arquitetura mudou.
- [ ] Design system (código + docs + artefato) atualizado se algo visual novo entrou.
- [ ] `docs/QA_TEST_PLAN.md` com os casos novos.
- [ ] Issues: as resolvidas fechadas com a versão; o que ficou para depois aberto como issue nova.

## 11. Fechar com o John

Resposta curta em pt-BR: o que mudou para o jogador, versão, o que foi verificado e como, o que
ficou pendente (e se depende dele). Follow-up real vira card de sessão, não promessa em texto.
