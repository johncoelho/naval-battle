# Processo de build e entrega

Como uma versão nova do Naval Battle sai daqui e chega ao celular.

## O caminho curto

1. Alterar o código.
2. **Atualizar a documentação** (ver [regra dos artefatos](#regra-dos-artefatos) — não é opcional).
3. Commitar na `main`.
4. O GitHub Actions compila e publica sozinho.
5. Baixar o APK e testar no aparelho.

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

## Landing page

`site/index.html` é publicada pelo GitHub Pages através do workflow `.github/workflows/pages.yml`,
que roda a cada mudança em `site/` (e sob demanda). A origem do Pages está configurada como
**GitHub Actions** nas configurações do repositório.

Endereço: **https://johncoelho.github.io/naval-battle/**

O botão de download aponta para a release rolante:
`releases/download/latest/naval-battle-debug.apk`. Como o build de APK recria essa release a
cada push na `main`, o link nunca precisa ser atualizado.

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
