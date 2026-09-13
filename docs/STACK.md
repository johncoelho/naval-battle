# Stack técnica e convenções

O que o projeto usa, em que versão, e as regras que mantêm o padrão. Mudou alguma coisa
aqui? Atualize este arquivo no mesmo commit.

## Versões fixadas

| Peça | Versão | Onde |
|---|---|---|
| Kotlin | 2.1.0 | `gradle/libs.versions.toml` |
| Compose Multiplatform | 1.7.3 | idem |
| Android Gradle Plugin | 8.7.3 | idem |
| Gradle | 8.11.1 | `gradle/wrapper/gradle-wrapper.properties` |
| JDK (CI) | 21 Temurin | `.github/workflows/android.yml` |
| JVM target | 17 | `composeApp/build.gradle.kts` |
| compileSdk / targetSdk | 35 | catálogo de versões |
| minSdk | 26 (Android 8.0) | catálogo de versões |
| activity-compose | 1.9.3 | catálogo de versões |

**Cuidado com o AGP.** A partir do 9.0 o plugin `com.android.application` deixa de ser
compatível com `org.jetbrains.kotlin.multiplatform`. Subir para 9.x quebra o projeto —
já aconteceu uma vez, por copiar versões de um projeto irmão que não é KMP. Antes de
mexer em versão, confirmar a compatibilidade da dupla AGP × KMP.

Toda versão nova passa pelo catálogo `libs.versions.toml`. Nada de número solto no
`build.gradle.kts`.

## Dependências — a regra é não ter

O APK tem **uma** dependência além do Compose: `activity-compose`. Nada de imagem, nada
de fonte externa, nada de biblioteca de rede, nada de serialização.

Consequências práticas dessa escolha, todas deliberadas:

- **Arte**: tudo desenhado em Canvas (embarcações, insígnias, radar, abertura).
- **Tipografia**: famílias do sistema, com peso e espaçamento fazendo o trabalho.
- **Rede**: `HttpURLConnection` + `org.json`, no `androidMain`. São cinco chamadas REST
  ao Supabase; uma biblioteca inteira para isso não se paga.
- **Persistência**: `SharedPreferences` por trás de um `expect class Prefs`.

Antes de somar uma dependência, a pergunta é: quanto de APK e de acoplamento ela custa,
e quanto código próprio ela realmente elimina?

## Multiplataforma: onde cada coisa mora

```
commonMain/   tudo — motor, telas, estado, contratos expect
androidMain/  só o que é do Android: Activity, manifesto, recursos e os actual
```

Três pares `expect/actual` isolam a plataforma:

| Contrato | `commonMain` | `androidMain` |
|---|---|---|
| `SoundPlayer` | efeitos de combate | `SoundPool` + `R.raw` |
| `MusicPlayer` | trilha em laço | `MediaPlayer` com `isLooping` |
| `Prefs` | chave-valor da carreira | `SharedPreferences` |
| `CloudApi` | conta e sincronização | `HttpURLConnection` + `org.json` |

**Portar para iOS é escrever esses quatro `actual`.** Nenhuma tela, nenhuma regra de jogo
precisa mudar. Manter essa propriedade é a razão de existir da separação: código de
plataforma que vaza para `commonMain` é dívida imediata.

## Arquitetura do estado

- `AppState` (em `App.kt`) guarda tela atual, modo, partida e a conta; é o roteador.
- `Profile` guarda a carreira e **grava a cada mudança** — nada fica só em memória
  esperando um "salvar".
- `Match` é o estado de uma partida; morre com ela.
- Estado de UI observável via `mutableStateOf`/`mutableStateListOf` dentro das classes de
  domínio — o Compose recompõe sozinho, sem ViewModel nem camada de eventos.

Efeitos colaterais (som, rede, temporizadores) vivem em `LaunchedEffect`, com chaves que
descrevem exatamente quando devem rodar de novo. Nunca dentro da composição.

## Convenções de código

- **Idioma**: código e identificadores em inglês; **comentários e textos de tela em
  português**, como o jogo.
- **Comentários explicam o porquê**, não o quê. Se o comentário repete o código, sai.
- Funções de desenho são `private fun DrawScope.drawAlgo(...)`, em coordenadas do viewBox
  do desenho, nunca em pixels de tela.
- Nomes de domínio em português naval quando o termo é do jogo (`Livery`, `FleetLine`,
  `Rank`, `Insignia`), porque é o vocabulário do produto.
- Nada de `TODO` órfão: ou vira item do roadmap no README, ou vira código.

## Segurança

- A **chave anônima** do Supabase é pública e fica no código — quem protege é o RLS.
- A **`service_role`** não entra no repositório, no app, nem em conversa. Sem exceção.
- A chave de **publicação** (upload key) vai para os secrets do GitHub quando existir;
  só a de depuração é versionada, e o motivo está em [BUILD.md](BUILD.md).
- Tokens de sessão hoje ficam em `SharedPreferences` comum. Para endurecer, o caminho é
  `EncryptedSharedPreferences` — pendência conhecida, ainda não paga.
