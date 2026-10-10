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
- Feature média/grande: escreva uma SPEC curta (o que, por quê, telas afetadas, regras, riscos)
  antes de codar; o John aprova. Correção pequena: siga direto.
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

## 7. Registrar a versão no servidor

```sql
insert into app_releases (version_name, version_code, notes_pt, notes_en, notes_es, android_live, ios_live, notify)
values ('0.X.Y', N, '…', '…', '…', true, false, false);
```
Notas com acento nas três línguas. `notify = true` só quando o John quiser push de "versão nova".

## 8. Acompanhar o CI e liberar o iPhone

- CI: `android.yml` (APK + `.aab` + Play), `ios.yml` (framework + `.ipa`), `pages.yml`. Sem `gh`
  logado, acompanhar pela API pública:
  `curl -s https://api.github.com/repos/johncoelho/naval-battle/actions/runs?per_page=6`
  (laço em segundo plano até os runs do commit terminarem).
- Falhou: ler o log, corrigir, nova versão. Nunca dizer "no ar" sem o CI verde.
- `ios.yml` verde → `update app_releases set ios_live = true where version_code = N`.
- Site: se mudou, abrir a página publicada e conferir.

## 9. Depois da revisão da Play

- A versão passa por revisão do Google (horas). Quando o John avisar que saiu (ou pedir para
  conferir): atualizar QA01/QA02 pela Play e testar o que mudou, nos casos do `QA_TEST_PLAN.md`.
- Achou falha: volta ao passo 2 com versão nova.

## 10. Fechar com o John

Resposta curta em pt-BR: o que mudou para o jogador, versão, o que foi verificado e como, o que
ficou pendente (e se depende dele). Follow-up real vira card de sessão, não promessa em texto.
