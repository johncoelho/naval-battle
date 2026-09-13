# Naval Battle

Batalha naval tática para Android — com iOS no horizonte — vestida como um centro de
comando naval noturno. Todo o jogo é desenhado em Canvas: não há uma única imagem no APK.

| | |
|---|---|
| **Plataforma** | Android 8.0+ (minSdk 26), portável para iOS |
| **Stack** | Kotlin Multiplatform · Compose Multiplatform |
| **Base** | Supabase (Auth + Postgres com RLS) |
| **Idiomas** | Português (BR), inglês, espanhol |
| **Pacote** | `br.com.navalbattle` |
| **Site** | [johncoelho.github.io/naval-battle](https://johncoelho.github.io/naval-battle/) |
| **APK de teste** | [release `latest`](https://github.com/johncoelho/naval-battle/releases/download/latest/naval-battle-debug.apk) |

Documentação complementar: [processo de build](docs/BUILD.md) · [stack e convenções](docs/STACK.md) ·
[design system](docs/DESIGN_SYSTEM.md) · [histórico](CHANGELOG.md)

---

## O jogo

### Modos de combate

Regra de turno igual nos dois modos, a clássica da batalha naval: **acertou, atira de novo**;
só passa a vez para o adversário no primeiro erro.

- **Clássico** (padrão) — sem habilidades. A batalha naval de sempre.
- **Tático** — cada classe de navio concede uma habilidade, com recarga própria. Na
  batalha o ícone de cada uma vem com a descrição do efeito ao lado, para não depender
  de decorar sigla — e a barra fica ao lado do mapa da própria frota, não empilhada
  em cima dele:

| Classe | Tamanho | Habilidade | Ícone | Efeito |
|---|---|---|---|---|
| Porta-aviões | 5 | Reconhecimento aéreo | 📡 | Revela uma linha inteira |
| Encouraçado | 4 | Barragem dupla | 💥 | Dois disparos no mesmo turno |
| Cruzador | 3 | Ping de sonar | 📶 | Varre um setor 3×3 |
| Submarino | 3 | Imersão | 🫧 | Absorve o primeiro acerto, uma vez |
| Destróier | 2 | Cortina de fumaça | 💨 | Bloqueia a próxima varredura inimiga |

Cada habilidade também tem um **cartucho avulso** na Loja do Arsenal: comprado com
créditos, fica guardado no aparelho e libera um uso mesmo com a habilidade em recarga
na próxima partida tática — some ao ser usado, a recarga normal segue igual.

### Formas de jogar

- **Partida rápida** — contra a IA, que caça em padrão de paridade e persegue contatos.
  É a única modalidade que conta para a carreira.
- **Dois jogadores no mesmo aparelho** — cada comandante põe o nome, posiciona a frota
  em sigilo (com tela de passagem entre os dois) e a batalha corre toda numa tela só.
  Cada um enxerga apenas a **própria memória de tiro**; ao fim, a carta se revela inteira,
  com as duas frotas e todos os tiros, cada um na cor do seu dono.
- **Rede local** — dois celulares no mesmo Wi-Fi. Um dá um nome à partida e a cria, o
  outro encontra esse nome na busca e entra; cada um posiciona a própria frota no seu
  aparelho. A partir daí a tela é igual à do modo solo: tabuleiro alvo, frota própria
  sempre visível, alarme quando é atingido — cada aparelho é o único que vê a própria
  tela. Não passa por servidor: funciona sem internet.
  - **Revanche** — ao fim da partida, cada lado toca em "Revanche"; quando os dois
    tocam, a batalha recomeça na mesma ligação, sem precisar criar ou procurar de novo.
  - **Provocações** — um ícone de rádio na barra superior abre um mural de emojis e
    gritos de guerra prontos ("Fogo total!", "Belo tiro!", ...) para mandar ao outro
    aparelho durante a partida; aparecem como uma bolha na tela de quem recebe.
- **Ranqueada online** — planejada, ainda não implementada.

### Carreira

Partidas contra a IA rendem **XP** e **créditos**: base por jogar, dobro por vencer, mais
bônus por precisão, por navio afundado e por fechar em até 40 turnos.

- **Patentes**, de Recruta a Almirante, em dez degraus de XP.
- **Perfil** com nome de guerra, insígnia (seis brasões vetoriais) e folha de serviço
  completa: partidas, vitórias, aproveitamento, precisão, navios afundados e sequências.
- **Créditos** compram na loja. Nada de dinheiro real por enquanto — pagamento entra
  na publicação, e a forma natural de encaixá-lo é vender créditos.

### Personalização

Dois eixos independentes, ambos comprados com créditos:

- **Linha de casco** — muda a silhueta das cinco embarcações (boca, proa, superestrutura,
  chaminés): Padrão (inclusa), Imperial, Atlântica e Fantasma.
- **Camuflagem** — muda a pintura e o padrão recortado no casco: lisa, dazzle, estilhaço,
  faixas de linha d'água e retículo digital. Onze pinturas, duas inclusas.

### Idiomas

Português do Brasil, inglês e espanhol, trocáveis no Perfil e aplicados na hora. Todo o
texto vive em `i18n/Strings.kt`, com as três versões de cada frase na mesma linha.

A **Loja do Arsenal** vende; o **Estaleiro** combina o que já foi conquistado.

### Como a rede local funciona

As frotas são trocadas no começo da partida e, dali em diante, só as jogadas viajam
(`ACT|x|y`). Os dois aparelhos rodam a mesma partida e resolvem cada tiro de forma
idêntica — nada precisa ser confirmado de volta. A descoberta usa NSD/mDNS, o mesmo
mecanismo do Bonjour, o que deixa o caminho pronto para o iOS.

Como cada aparelho conhece a frota do outro para resolver os tiros, um cliente adulterado
poderia espiar. É aceitável entre amigos na mesma rede; quando houver ranqueada valendo
pontuação, a resolução passa para o lado do dono da frota.

### Conta e sincronização

A carreira é gravada **no aparelho** e, havendo conta, espelhada no Supabase.

- Cadastro com e-mail, senha e nome de usuário; login abre sessão guardada localmente.
- **Entrar com o Google**, sem senha nenhuma — cai na mesma carreira se o e-mail
  já tiver conta. O botão só aparece depois da configuração externa (ver
  [docs/BUILD.md](docs/BUILD.md#login-com-google--configuração-do-lado-de-fora-do-código)).
- Sincroniza ao entrar, na abertura do app, ao fim de cada partida, em cada compra e
  ao mudar nome ou insígnia.
- **Regra de fusão:** ao entrar, se a nuvem tiver mais XP ela desce; senão, o aparelho sobe.
  Nunca se perde o maior progresso.
- A sessão se renova sozinha quando o token vence (401 → refresh → repete a chamada).
- **Trocar senha** (logado, pede a senha atual) e **esqueci minha senha** (manda link
  por e-mail) na tela de Conta.
- Sem rede ou sem conta, o jogo roda inteiro offline.

Fica **só no aparelho**: tokens de sessão, preferência de trilha e o andamento da partida.
Fica **só no servidor**: a senha, no Auth do Supabase — o app nunca a guarda.

---

## Arquitetura

```
composeApp/src/
  commonMain/kotlin/br/com/navalbattle/
    App.kt        estado global, roteador de telas, fusão com a nuvem
    game/         motor: modelo, tabuleiro, IA, partida, carreira
    design/       tokens, tipografia, pinturas, linhas de casco, arte vetorial
    i18n/         dicionário dos três idiomas
    ui/           telas e componentes
    data/         armazenamento local e cliente da nuvem (expect)
  androidMain/    Activity, manifesto, recursos, e os actual de áudio/dados
supabase/schema.sql   tabela profiles, RLS, gatilhos e placar
site/index.html       landing page publicada no GitHub Pages
keystore/             chave de depuração fixa (ver docs/BUILD.md)
docs/                 build, stack e design system
```

Toda a lógica e toda a interface vivem em `commonMain`. O que é específico de plataforma
está isolado em três pares `expect/actual`: `SoundPlayer`, `MusicPlayer` e `Prefs`/`CloudApi`.
É o que torna o alvo iOS uma questão de escrever os `actual`, sem tocar no jogo.

### Telas

`SPLASH → MENU → {SHIPYARD, STORE, PROFILE, AUTH}` e o fluxo de partida
`NAMES → PLACEMENT → HANDOFF → BATTLE → RESULT`.

---

## Base de dados

O script [`supabase/schema.sql`](supabase/schema.sql) cria tudo. Em resumo:

- Tabela `profiles`, uma linha por comandante, presa a `auth.users`.
- **RLS ligado**: cada um só lê e escreve a própria carreira. A chave anônima sozinha
  não devolve nada — foi verificado por chamada real à API.
- Gatilho `on_auth_user_created` cria a carreira no instante em que a conta nasce,
  **inclusive em login social** — é o que faz o Google cair na mesma carreira do e-mail.
  Nome de usuário repetido ganha sufixo em vez de barrar o cadastro.
- View `leaderboard` pronta para a ranqueada.

Configuração do cliente em `data/Cloud.kt` (`SupabaseConfig`). A chave anônima é pública
por natureza; a `service_role` **nunca** entra no repositório nem no app.

---

## Build

```bash
./gradlew :composeApp:assembleDebug
```

Cada push na `main` dispara o GitHub Actions, que compila e publica o APK na release
`latest`. O processo completo — assinatura, entrega e a regra de atualizar a documentação
a cada versão — está em **[docs/BUILD.md](docs/BUILD.md)**.

---

## Créditos de áudio

Efeitos em `composeApp/src/androidMain/res/raw/`, montados a partir de gravações de
**domínio público** ou **CC0** do Wikimedia Commons, cortadas, filtradas e mixadas com ffmpeg:

- `sfx_hit.wav` — [Explosion-LS100155.ogg](https://commons.wikimedia.org/wiki/File:Explosion-LS100155.ogg) (Fg2, domínio público)
- `sfx_sunk.wav` — [Explosion 10.ogg](https://commons.wikimedia.org/wiki/File:Explosion_10.ogg) (tcpp, domínio público) em camadas com o anterior e com a água
- `sfx_miss1/2/3.wav` — três quebras de onda de [Ocean Waves on a Tropical Beach.ogg](https://commons.wikimedia.org/wiki/File:Ocean_Waves_on_a_Tropical_Beach.ogg) (CC0), sorteadas em jogo para o tiro na água não enjoar
- `sfx_launch.wav` — cauda de "Explosion 10.ogg" invertida, virando o assobio do projétil
- `sfx_alarm.wav` / `sfx_alarm_critical.wav` — alarme de bordo, que soa só quando **a sua** frota é atingida

### Trilha

Laços de um minuto cortados em número inteiro de compassos (andamento e tempo forte
detectados por análise de envelope), fusão cruzada de 1 s na emenda e volume normalizado:

- `music_theme.ogg` — "Systems Go", do conjunto de rock da [United States Air Force Band of Flight](https://commons.wikimedia.org/wiki/File:4th_Street_Exit_-_Systems_Go_-_United_States_Air_Force_Band_of_Flight.mp3) (composição de Steve Ward). Obra do governo dos EUA, **domínio público**. Abertura e menu.
- `music_battle.ogg` — ["Secret Agent Rock"](https://commons.wikimedia.org/wiki/File:John_Bartmann_-_17_-_Secret_Agent_Rock.ogg), de John Bartmann (**CC0**). Combate, em volume mais baixo para não cobrir os tiros.

---

## Roadmap

- [x] Login com Google — configurado de ponta a ponta (Google Cloud + Supabase),
      ver [docs/BUILD.md](docs/BUILD.md#login-com-google--configuração-do-lado-de-fora-do-código)
- [ ] Convite de amigo e partida rápida pela internet (via Supabase Realtime)
- [ ] Partida local por Nearby Connections (Bluetooth / Wi-Fi Direct)
- [ ] Compras com pagamento real (Google Play Billing) vendendo créditos
- [ ] Alvo iOS: escrever os `actual` de áudio, preferências e rede. O IPA instalável
      depende ainda de conta paga no Apple Developer para assinar
