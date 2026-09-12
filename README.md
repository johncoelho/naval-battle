# Naval Battle

Batalha naval tática para Android (e iOS no futuro), com identidade visual de centro de comando naval noturno.

## Estado atual

MVP jogável contra a IA, com os dois modos de jogo definidos no design:

- **Clássico** — um tiro por turno, sem habilidades.
- **Tático** — habilidades por classe de navio: ping de sonar, reconhecimento aéreo, barragem dupla, cortina de fumaça e imersão do submarino.

Já implementado:

- Motor de jogo completo (posicionamento, turnos, resolução de tiro, condições de vitória)
- IA de caça com padrão de paridade e perseguição de contatos
- Tabuleiro de radar desenhado em Canvas: varredura animada, anéis, névoa de guerra e impacto animado
- As cinco embarcações desenhadas em vetor, repintáveis por libré (três tokens: casco, convés, detalhe)
- Estaleiro com frotas temáticas (Padrão, Brasil, Japão, EUA, Reino Unido, Ártica, Furtiva)
- Frases de comando e retorno háptico a cada disparo
- Timer de turno de 20s com disparo automático ao expirar

Ainda não implementado (próximas fases):

- Multiplayer online (Supabase Realtime + Auth com Google)
- Partida local via Nearby Connections (Bluetooth / Wi-Fi Direct)
- Progressão persistente, ranking ELO e loja com pagamento

## Stack

- Kotlin Multiplatform + Compose Multiplatform (alvo Android hoje, iOS depois)
- Toda a lógica de jogo e a UI vivem em `commonMain`, portáveis sem alteração
- Backend planejado: Supabase (Auth, Postgres, Realtime, Edge Functions)

## Estrutura

```
composeApp/
  src/commonMain/kotlin/br/com/navalbattle/
    game/      motor de jogo (modelo, tabuleiro, IA, partida)
    design/    tokens de cor, tipografia, librés e desenho das embarcações
    ui/        telas e componentes
  src/androidMain/   Activity, manifesto e recursos Android
```

## Build

```bash
./gradlew :composeApp:assembleDebug
```

O APK sai em `composeApp/build/outputs/apk/debug/`.

Cada push na `main` dispara o workflow do GitHub Actions, que compila o APK de depuração
e o publica na release `latest` para download direto no celular.
