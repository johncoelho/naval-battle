# Processo de build e entrega

Como uma versão nova do Naval Battle sai daqui e chega ao celular.

## O caminho curto

1. Alterar o código.
2. **Atualizar a documentação** (ver [regra dos artefatos](#regra-dos-artefatos) — não é opcional).
3. Commitar na `main`.
4. O GitHub Actions compila e publica sozinho.
5. Baixar o APK e testar no aparelho.
6. **Conferir a [landing page](https://johncoelho.github.io/naval-battle/)** — abrir e checar
   que a data/tamanho da última compilação bateram com o build que acabou de sair (ver
   [Landing page](#landing-page)). É uma checagem real, não uma suposição de que "deve
   estar automático".

## Compilação local

```bash
./gradlew :composeApp:assembleDebug
```

O APK sai em `composeApp/build/outputs/apk/debug/`.

> **Nota de ambiente:** na máquina de desenvolvimento atual o Gradle falha com
> *"Unable to establish loopback connection"* (limitação de JDK/Windows). Enquanto isso
> não se resolve, **toda compilação acontece no CI** — que é, de qualquer forma, a fonte
> de verdade do APK entregue.

## Integração contínua

`.github/workflows/android.yml`, disparado a cada push na `main`:

| Passo | O que faz |
|---|---|
| `setup-java` | JDK 21 (Temurin) |
| `setup-android` | SDK do Android |
| `assembleDebug` | compila o APK |
| `upload-artifact` | guarda o APK como artefato da execução |
| `gh release create latest` | recria a release rolante `latest` com o APK |

Link fixo do APK, sempre apontando para a última versão:

```
https://github.com/johncoelho/naval-battle/releases/download/latest/naval-battle-debug.apk
```

## Assinatura — por que existe uma chave no repositório

`keystore/naval-debug.keystore` é uma **chave de depuração fixa**, versionada de propósito,
e `composeApp/build.gradle.kts` a usa no `buildType` debug.

O motivo é concreto: sem ela, cada execução do CI gera uma chave de depuração nova, o
Android recusa instalar o APK por cima do anterior (assinaturas diferentes), o comandante
desinstala para atualizar — e **perde todo o progresso gravado no aparelho**. Foi
exatamente o que aconteceu antes da correção.

Com a chave fixa, cada APK novo instala por cima como atualização e os dados permanecem.

> Esta chave é **só de depuração**. A chave de publicação (upload key da Play Store)
> nunca entra no repositório: ela vai para os *secrets* do GitHub quando chegarmos lá.

Se um dia a chave precisar ser trocada, todo mundo que tem o jogo instalado terá que
desinstalar uma vez. Trocar sem necessidade é quebrar a base instalada.

## Entrega ao testador

O APK é enviado diretamente ao celular como arquivo. O link bruto do GitHub deu problema
de download em rede móvel, então o arquivo vai anexado.

Na instalação: permitir "fontes desconhecidas" para o app que estiver abrindo o arquivo.

## Configuração do servidor

A URL e a chave anônima do Supabase ficam em
`composeApp/src/commonMain/kotlin/br/com/navalbattle/data/Cloud.kt`. Com os campos em
branco, o jogo compila e roda normalmente — só local, sem conta. A chave anônima é pública
por natureza (é o que protege o RLS, não o segredo da chave). A **`service_role` nunca**
entra no repositório, no app ou em conversa.

Para recriar a base do zero em outro projeto: rodar [`supabase/schema.sql`](../supabase/schema.sql)
no SQL Editor e desligar *Confirm email* em Authentication → Sign In / Providers enquanto
estiver em teste.

## Login com Google — configuração do lado de fora do código

> **Já configurado e publicado** (2026-09-13): projeto **AIGAMESFACTORY** no Google
> Cloud, app "Naval Battle" com tela de consentimento externa **em produção**
> (`Publishing status: In production` — não fica mais restrito a testadores). Página de
> privacidade em [site/privacy.html](../site/privacy.html), publicada em
> `johncoelho.github.io/naval-battle/privacy.html`, foi o que faltava para liberar o
> "Publish app". Dois clientes OAuth criados — **Naval Battle - Web (Supabase)** e
> **Naval Battle - Android** (pacote `br.com.navalbattle`, SHA-1 da chave de depuração
> do repositório). O provedor Google está **ativado** no Supabase com os dois Client IDs
> (separados por vírgula) e o Client Secret do cliente Web, e
> `GoogleAuthConfig.WEB_CLIENT_ID` já tem o valor certo. O passo a passo abaixo fica
> registrado para o dia em que for preciso trocar de projeto ou girar a chave de
> depuração (novo SHA-1, exige um novo cliente Android).

O botão "Entrar com o Google" só aparece quando `GoogleAuthConfig.WEB_CLIENT_ID`
(em `composeApp/src/commonMain/kotlin/br/com/navalbattle/data/GoogleAuth.kt`) não está
em branco.

### 1. Google Cloud Console

1. Abrir **[console.cloud.google.com](https://console.cloud.google.com/)** e escolher ou
   criar um projeto (pode ser o mesmo já usado para outra coisa, ou um novo chamado
   "Naval Battle").
2. **Tela de consentimento OAuth** —
   **[console.cloud.google.com/apis/credentials/consent](https://console.cloud.google.com/apis/credentials/consent)**:
   tipo de usuário "Externo", nome do app, e-mail de suporte. Pode ficar em modo de teste
   (adicionando o próprio e-mail como testador) enquanto o jogo não sai da fase de teste.
3. **Criar credenciais** —
   **[console.cloud.google.com/apis/credentials](https://console.cloud.google.com/apis/credentials)**
   → "Create Credentials" → "OAuth client ID". Precisa de **dois** clientes:
   - **Web application** — nome livre (ex.: "Naval Battle · Supabase"). Em
     *Authorized redirect URIs* adicionar exatamente:
     `https://cwtslesnthbenxswdcbv.supabase.co/auth/v1/callback`.
     Depois de criar, copiar o **Client ID** e o **Client Secret** — os dois vão para o
     Supabase no passo seguinte. **O Client ID desse cliente Web é o valor que também
     entra em `GoogleAuthConfig.WEB_CLIENT_ID`** (o app pede a credencial ao Google
     citando esse ID, para o token de identidade sair com a audiência que o Supabase
     espera).
   - **Android** — *Package name*: `br.com.navalbattle`. *SHA-1 certificate fingerprint*:
     o da chave de depuração fixa do repositório. Para obter, rodar (com o JDK instalado,
     não precisa do Gradle funcionando):
     ```bash
     keytool -list -v -keystore keystore/naval-debug.keystore -alias navalbattle -storepass navalbattle -keypass navalbattle
     ```
     e copiar a linha `SHA1:`. Esse cliente não tem Client Secret — só destrava a caixa
     de seleção de conta para quem instalar o APK assinado com essa chave.

### 2. Painel do Supabase

Em **[supabase.com/dashboard/project/cwtslesnthbenxswdcbv/auth/providers](https://supabase.com/dashboard/project/cwtslesnthbenxswdcbv/auth/providers)**,
achar **Google** na lista de provedores, ativar, e colar ali o **Client ID** e o
**Client Secret** do cliente **Web** criado no passo anterior. Salvar.

### 3. De volta ao código

Só falta uma linha: mandar o **Client ID do cliente Web** (não o Android) para entrar em
`GoogleAuthConfig.WEB_CLIENT_ID`. O trigger `on_auth_user_created` do
[`supabase/schema.sql`](../supabase/schema.sql) já cria a carreira automaticamente também
para quem entra pelo Google — é o mesmo mecanismo que já funciona para o cadastro por
e-mail, sem mudança nenhuma na base.

## iOS — em andamento

O alvo iOS foi adicionado ao `composeApp/build.gradle.kts` (`iosX64`, `iosArm64`,
`iosSimulatorArm64`) e o `.github/workflows/ios.yml` compila o framework Kotlin/Native
a cada push, num runner `macos-latest` — é a única forma de validar isso sem um Mac à
mão, então esse workflow verde é a fonte de verdade de que o multiplataforma ainda
compila para iOS.

> **Primeiro build verde em 2026-09-14** (execução #10 do `ios.yml`), depois de várias
> rodadas de correção guiadas só pelo erro do compilador na CI (sem toolchain local para
> testar). As lições ficam registradas aqui porque a próxima pessoa mexendo em
> `Cloud.ios.kt`, ou em qualquer chamada nova a `NSURLSession`/`NSMutableURLRequest`, vai
> tropeçar nas mesmas pegadinhas do binding Objective-C do Kotlin/Native:
> - **Import específico não basta** — vários membros de `NSMutableURLRequest`
>   (`HTTPMethod`, `HTTPBody`, `allHTTPHeaderFields`) e do `NSError` (`localizedDescription`)
>   vêm de categorias Objective-C, e o Kotlin/Native os expõe como extensão de nível de
>   pacote. Importar só a classe (`import platform.Foundation.NSMutableURLRequest`) não
>   traz essas extensões — dava "Unresolved reference" mesmo com o nome certo. A saída foi
>   trocar a lista de imports específicos por `import platform.Foundation.*`.
> - **HTTPMethod/HTTPBody são funções, não propriedades** — `setHTTPMethod(_:)` etc.
>   viram `req.setHTTPMethod(valor)`, não `req.HTTPMethod = valor` (o getter mora na classe
>   base `NSURLRequest`, o "setter" é um método de categoria separado no
>   `NSMutableURLRequest`, e o Kotlin não funde os dois numa `var`).
> - **`dataTaskWithRequest` não aceita nomear o parâmetro do completion handler** — usar
>   lambda posicional (`dataTaskWithRequest(req) { data, response, error -> ... }`), não
>   `dataTaskWithRequest(request = req, completionHandler = { ... })`.
> - **`NSDictionary` não é tipado** — `setAllHTTPHeaderFields` pede `Map<Any?, *>`, então
>   um `Map<String, String>` precisa de cast (`as Map<Any?, *>`) na chamada.
> - **`NSURL(string = ...)` pode falhar** — é um inicializador que pode devolver nulo
>   (URL malformada); sem `!!` (ou tratamento), o restante do código que depende dessa
>   URL fica frágil.
> - **Texto para `NSData` sem depender de `NSString`** — converter `String` para `NSData`
>   via `encodeToByteArray()` + `usePinned { NSData.create(bytes = ..., length = ...) }`
>   evita todo o vaivém incerto de `NSString.dataUsingEncoding`.

**O que já tem `actual` de verdade para iOS** (`composeApp/src/iosMain`):
- `Prefs` — via `NSUserDefaults`.
- `CloudApi` — via `NSURLSession`, mesma lógica do cliente Android (`Cloud.android.kt`),
  só a chamada HTTP muda.
- `SoundPlayer` / `MusicPlayer` — via `AVAudioPlayer`, um tocador por efeito/faixa,
  carregado em segundo plano e reaproveitado a cada `play()`. Os `.wav` dos efeitos são
  os mesmos do Android; as duas faixas de música foram convertidas de `.ogg` para
  `.m4a` (`ffmpeg -i x.ogg -c:a aac -b:a 160k x.m4a`) porque o `AVAudioPlayer` não lê
  Ogg Vorbis. Todos os arquivos vivem em `commonMain/composeResources/files` e são lidos
  via `Res.readBytes(...)` (biblioteca `compose.components.resources`,
  `packageOfResClass` fixado em `br.com.navalbattle.generated.resources` porque o
  projeto não define `group` nenhum, e o padrão `{group}.{module}...` viraria um pacote
  começando com ponto). `AVAudioPlayer(data = ..., error = null)` é um inicializador que
  pode devolver nulo — a chamada é tratada com `?.let { ... }`, mesmo cuidado do
  `NSURL(string = ...)` acima.

**O que está de propósito só como pendência** (compila, mas não faz nada ainda):
- `GoogleAuth` — sempre devolve falha. `ASWebAuthenticationSession` (a via sem SDK
  externo) precisa de um esquema de URL de retorno registrado no `Info.plist` — que só
  existe dentro de um projeto Xcode de verdade, ainda inexistente. Alternativa com SDK:
  GoogleSignIn-iOS via Swift Package Manager, mesma pendência do projeto Xcode.
- `LanLink` — qualquer hospedar/procurar/entrar falha na hora. Precisa de
  `NetService`/`NetServiceBrowser` (Bonjour, o mesmo mDNS que o NSD do Android já usa
  de propósito, pensando nisso) para anunciar/descobrir, e `Network.framework` ou
  sockets BSD para a conversa TCP linha a linha.

**Ainda não existe projeto Xcode no repositório** (`iosApp/`) — sem ele não dá para
rodar o jogo de verdade num simulador ou aparelho, só provar que o Kotlin compila.
Criar esse projeto (e depois assinar para um iPhone físico) é o próximo passo, e exige
uma conta Apple Developer para distribuir além do simulador.

## Landing page

`site/index.html` é publicada pelo GitHub Pages através do workflow `.github/workflows/pages.yml`,
que roda a cada mudança em `site/` (e sob demanda). A origem do Pages está configurada como
**GitHub Actions** nas configurações do repositório.

Endereço: **https://johncoelho.github.io/naval-battle/**

O botão de download aponta para a release rolante:
`releases/download/latest/naval-battle-debug.apk`. Como o build de APK recria essa release a
cada push na `main`, **o endereço do link nunca precisa ser editado** — o arquivo por trás
dele é que troca sozinho. O rodapé da página também busca data e tamanho do APK atual na
API do GitHub ao carregar, então esse texto também se atualiza sozinho.

Mesmo assim, **a cada APK novo o site é aberto e conferido de verdade** (passo 6 do
[caminho curto](#o-caminho-curto)) — a API do GitHub pode estar com limite de chamadas
esgotado no momento em que alguém abre a página, e nesse caso ela cai no texto genérico de
reserva em vez da data real. Uma checagem rápida evita entregar um APK sem saber se a
vitrine dele está mostrando a informação certa.

## Regra dos artefatos

**Toda versão compilada atualiza a documentação no mesmo ciclo, antes da entrega do APK:**

| Arquivo | Quando atualizar |
|---|---|
| `CHANGELOG.md` | **sempre** — uma entrada por versão, com o que mudou e por quê |
| `README.md` | quando mudar comportamento do jogo, telas, economia ou arquitetura |
| `docs/BUILD.md` | quando mudar build, assinatura, CI ou entrega |
| `docs/STACK.md` | quando mudar versão de ferramenta, dependência ou convenção |
| `docs/DESIGN_SYSTEM.md` | quando mudar token, tipografia, arte ou padrão de interface |
| `site/index.html` | quando entrar recurso que valha aparecer na apresentação do jogo |

O commit de documentação anda junto com o de código — não depois, não "quando der".
O objetivo é que o histórico do versionamento sirva para resgatar o que foi decidido,
sem depender da memória de ninguém.
