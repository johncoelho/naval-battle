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

| Classe | Tamanho | Habilidade | Efeito |
|---|---|---|---|
| Porta-aviões | 5 | Reconhecimento aéreo | Revela uma linha inteira |
| Encouraçado | 4 | Barragem dupla | Dois disparos no mesmo turno |
| Cruzador | 3 | Ping de sonar | Varre um setor 3×3 |
| Submarino | 3 | — | Sem habilidade própria — leva tiro como qualquer navio |
| Destróier | 2 | Cortina de fumaça | Bloqueia a próxima varredura inimiga |

Cada habilidade também tem um **cartucho avulso** na Loja do Arsenal: comprado com
dobrões, fica guardado no aparelho e libera um uso mesmo com a habilidade em recarga
na próxima partida tática — some ao ser usado, a recarga normal segue igual.

Os ícones de cada habilidade são vetores desenhados a traço (`design/AbilityArt.kt`,
`drawAbilityIcon`), na mesma linguagem visual dos navios e insígnias — nenhum emoji
do sistema, para não mudar de aparência entre aparelhos. O reconhecimento aéreo tem
um avião animado sobrevoando a linha alvo (a célula só acende depois que ele passa
por cima) e o sonar um anel se expandindo a partir do ponto varrido; os dois callouts
agora dizem quantas embarcações foram identificadas. A cortina de fumaça fica visível
como uma nuvem sobre a própria frota enquanto ativa.

O Submarino teve a Imersão (absorvia o primeiro acerto escondendo o casco como se
fosse água) removida por completo: a célula "blindada" ficava visualmente idêntica
a uma água qualquer, então o próximo acerto de verdade num navio vizinho parecia
flutuar sozinho, sem nenhum casco por perto — lido como bug mesmo depois de avisado
por callout. Hoje o Submarino recebe tiro normalmente, como qualquer outro navio.

### Formas de jogar

- **Partida rápida** — contra a IA, que caça em padrão de paridade e persegue contatos.
  É a única modalidade que conta para a carreira.
- **Dois jogadores no mesmo aparelho** — cada comandante põe o nome e posiciona a frota
  em sigilo (em pé, com tela de passagem entre os dois). A batalha é **na horizontal**
  (`ui/LocalBattle.kt`; o conteúdo gira, a orientação do sistema não muda): um quadro
  para cada um, com o nome no topo, mostrando o mar inimigo que ele ataca. Só o quadro da
  vez fica aceso e tocável; a vez troca em tudo junto quando a animação do tiro termina,
  e a coluna do meio mostra de quem é a vez (seta e nome na cor dele), o turno, **⇄
  Inverter** (troca os quadros de lado) e Sair. No Tático, cada quadro tem as próprias
  habilidades. No fim, "Vitória de Fulano" no centro antes do relatório.
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
- **Online (internet)** — sala de amigo (código curto pra compartilhar por fora do
  jogo) ou partida rápida (o jogo emparelha com quem estiver procurando também). Exige
  conta conectada. Mesmo protocolo de jogadas do modo rede local, só que trafegando
  pela REST do Supabase em vez de socket — cada lado grava a própria jogada e consulta
  por novidades a cada 1-2 segundos, sem precisar dos dois na mesma rede.
  - Convidar um amigo ou procurar partida rápida abre um popup com radar de espera
    e botão de cancelar; quem é convidado vê um popup de aceitar/recusar.
  - Todo balão de partida (convite de amigo, partida rápida, adversário encontrado)
    mostra quem chama, um selo **Ranqueada** (âmbar) ou **Casual** e o modo
    (Clássico/Tático) — "Bina te convidou · Casual · Tático". Quem entra numa sala
    joga no modo de quem a abriu (`OnlineLink.roomMode`).
  - **Aceitar partida rápida de qualquer tela** — em Ajustes → Online, "Disponível
    para partida rápida" (desligado por padrão, só com conta) e a escolha Casual /
    Ranqueada / Ambas (ranqueada só depois de aceitar a temporada). Ligado, o mesmo
    polling de 4s do convite procura salas de partida rápida esperando adversário, de
    qualquer modo (`find_quick_offer` no banco, que ignora sala parada há mais de 10
    minutos), e mostra um balão (`ui/QuickOfferBanner.kt`) com Aceitar / Agora não.
    Nunca aparece durante partida, enquanto o próprio comandante procura/hospeda, com
    outro popup na tela ou com o app em segundo plano. Aceitar entra pelo mesmo
    caminho do convidado da partida rápida (`OnlineLink.joinQuickOffer`); se alguém
    entrou antes, aparece "Essa partida já começou". "Agora não" esconde aquela sala
    até fechar o app.
  - **Relatório completo também online** — vitória/derrota do ponto de vista de quem
    está no aparelho, a carta com as duas frotas reveladas, comparativo lado a lado
    (tiros, acertos, precisão, navios restantes e afundados, turnos), apuração do XP,
    condecorações, carreira e a própria frota. Partida online (casual ou ranqueada)
    rende XP, medalhas e dobrões como contra a IA, com limite anti-farm: no máximo
    3 partidas recompensadas por dia contra o mesmo adversário (contagem local em
    `Profile.claimOnlineReward`). Casual mostra "não conta para o ranking"; ranqueada
    ganha um bloco de ranking (pontos da partida e como foram compostos, pontos e
    posição na temporada antes → depois, partidas e vitórias, botão "Ver ranking").
  - **Amigos** — tela própria (`ui/FriendsScreen.kt`): cartões com o avatar que cada
    comandante escolheu no perfil (`friend_avatars`; inicial do nome como reserva), nome e
    uma ação principal; busca enquanto digita; seções Pedidos recebidos (em destaque),
    Seus amigos (com **Jogar** direto) e Pedidos enviados (com Cancelar). Tocar no
    cartão abre a folha de serviço (insígnia, XP, pontos, partidas, vitórias, % e
    sequência) com Convidar e Remover (com confirmação); convidar abre uma sala do
    mesmo jeito que criar uma manualmente.
- **Ranqueada** — alternância Casual/Ranqueada na partida rápida (convite de amigo
  continua sempre casual, de propósito). Critérios desde a 0.14.0
  (`record_ranked_result` em `supabase/online.sql`):
  1. **O servidor decide o vencedor** — o primeiro relato da sala fixa `winner_id`;
     relato posterior que discorde não muda nada (esse lado é pontuado pelo resultado
     gravado) e a sala fica marcada em `result_conflict`.
  2. **Pontos estilo Elo** pela força do adversário: esperado = 1/(1+10^((adv−eu)/400)),
     base = round(32 × (resultado − esperado)) — com os pontos de temporada dos dois
     no placar da temporada e com o `ranked_rating` no geral.
  3. **Bônus de desempenho só para quem vence**, com teto de 30% do ganho
     (precisão × 0,08 + navios restantes); vitória vale no mínimo +5. Derrota só tem
     um alívio de até 3 pontos pela precisão, sem nunca virar ganho.
  4. **Abandono é derrota cheia** — sair no meio ou estourar os 60s em segundo plano
     relata derrota com acerto 0.
  5. **Desempate** no placar e na posição própria: pontos, vitórias, aproveitamento,
     antiguidade.

  Temporadas por estação
  do ano, calculadas no servidor sem tabela nem cron (`current_season()`); a cada
  temporada nova, um popup com ícone e cor da estação pede aceite antes de liberar a
  ranqueada. Tela de **Placar** (`ui/LeaderboardScreen.kt`) mostra ranking geral e
  por temporada em faixas (ouro/prata/bronze, com retrato de cada comandante),
  sempre com a posição do próprio comandante mesmo fora do topo, mais uma aba de
  **Troféus** com o pódio final da última temporada encerrada. O placar geral e a
  posição própria só contam `ranked_matches`/`ranked_wins` (colunas dedicadas,
  atualizadas só por `record_ranked_result`) — partida contra IA ou online casual
  vale XP e patente na Carreira, mas não entra no ranking.
- **Pausa por ausência (Online)** — se o app for para segundo plano numa partida
  online, ela pausa e o adversário vê um aviso com contagem regressiva de 60s
  (`Protocol.PAUSE`/`RESUME` em `data/LanLink.kt`, cada lado mede o prazo pelo
  próprio relógio via `data/Clock.kt`, sem sincronizar nada entre os aparelhos).
  Se não voltar a tempo, o outro lado vence por desistência; na ranqueada, quem
  ficou ausente leva derrota cheia, e não ganha XP nem dobrões (`Match.forfeitByTimeout`). Nas demais
  variantes de partida o próprio sistema já suspende os turnos em segundo plano,
  então não precisa de aviso nenhum.
- **Janela de versão nova** — a cada abertura e volta do segundo plano o app compara a
  versão instalada com a tabela `app_releases` do servidor (`supabase/releases.sql`) e
  mostra "Versão X disponível" com as novidades de cada versão publicada depois dela.
  Android: só avisa quando a Play confirma a atualização para o aparelho (In-App Update
  API) e o botão abre a Play Store; iPhone: o botão abre a parte do iPhone na página do
  jogo (`#ios`), onde está o .ipa novo. A faixa do menu reabre a janela.
- **Diário de bordo** (com conta) — balão que abre sozinho a cada entrada no app com
  prêmio esperando, e pelo chip do menu. **Check-in diário** +25 dobrões numa trilha de
  7 dias seguidos (pulou um dia, volta ao 1; o 7º dia paga +300 de bônus da semana) e
  **desafio do dia** +50, uma missão em rodízio igual para todos (jogar, vencer, afundar
  5 navios, usar 2 habilidades, 60% de acerto), contado nas partidas contra a IA e online
  e resgatado depois do check-in. O servidor trava cada prêmio uma vez por dia
  (`supabase/daily.sql`, valores em `app_config`). **Lembrete diário** às 19h por
  notificação local (sem push; desliga nos Ajustes): Android agenda pelo AlarmManager e
  reagenda ao disparar e refaz o lembrete depois de reiniciar o aparelho ou atualizar o app,
  iPhone deixa a semana agendada (`data/DailyReminder.kt`). Ajustes → **Testar notificação**
  dispara uma em 5 segundos e um aviso aparece quando o aparelho bloqueia as notificações do
  jogo, com atalho para os ajustes do sistema.
- **Conexão instável (Online)** — cada jogada entra numa fila e é reenviada até o
  servidor confirmar, numerada para não duplicar (`client_seq`); a sessão se renova
  sozinha se o token vencer no meio da partida; e os dois lados batem o ponto de
  presença a cada 3s (`online_heartbeat`). Sem servidor, a tela mostra **"Sem conexão —
  reconectando"** e o relógio do turno para; ao voltar, as jogadas guardadas vão e as do
  adversário chegam. Se o **adversário** some por mais de 10s, aparece **"Adversário sem
  conexão"** com 60s de contagem; no fim, vitória por abandono, e a linha `DROP` fica na
  sala para o outro lado fechar a partida quando reconectar (`Match.lostByDisconnect`).

- **Push (Android)** — a permissão é pedida ao chegar no menu e todo aparelho recebe os
  avisos gerais (versão nova, novidades), com ou sem conta; com conta, também convite de
  amigo para partida, pedido de amizade, pedido aceito e adversário encontrado na partida
  rápida — tudo como notificação mesmo com o jogo fechado
  (Firebase Cloud Messaging; envio pelo Supabase em `supabase/push.sql` e na Edge Function
  `push`). Com o jogo aberto os balões do próprio jogo avisam. iPhone ainda sem push (APNs
  exige conta paga da Apple). Configuração em [docs/BUILD.md](docs/BUILD.md#push-firebase).

### Carreira

Partidas contra a IA e online rendem **XP** e **dobrões**: base por jogar, dobro por
vencer, mais bônus por precisão, por navio afundado e por fechar em até 40 turnos. Online
vale até 3 partidas recompensadas por dia contra o mesmo adversário.

- **Patentes**, de Recruta a Almirante, em dez degraus de XP.
- **Perfil** com nome de guerra, insígnia (seis brasões vetoriais) e folha de serviço
  completa: partidas, vitórias, aproveitamento, precisão, navios afundados e sequências.
- **Dobrões** (a moeda do jogo) compram na loja. Beta testers têm uma aba Dobrões com compra
  simulada (até R$ 50/dia, sem cobrança real); a Google Play Billing entra depois.
- **Passe de temporada**: para jogar ranqueada é preciso aderir à temporada da estação —
  Passe Gratuito (só participa) ou Passe de Temporada (dobrões: camuflagem exclusiva da
  estação, dobrões e milhas bônus; upgrade depois sai mais caro). Fim de temporada premia
  o pódio. Regras em `supabase/season.sql`.
- **Milhas náuticas**: 10 por dia; cada partida online custa 1, vitória ranqueada rende +5 e
  casual +1 (regras em `supabase/economy.sql`, ajustáveis em `app_config`).

### Feedback e badges

Em Ajustes → "Enviar feedback" (ou no atalho do Perfil), o comandante manda um bug ou uma
sugestão direto pro servidor. A avaliação é manual — bug confirmado rende 150 a 600
dobrões, melhoria aceita rende 1 ou 3 cargas de habilidade — mas o aviso é automático: o
app checa na abertura e mostra um popup quando algo foi avaliado. **Badges** são
condecorações permanentes no Perfil — "Beta Tester" para quem criou conta durante o teste
fechado, e "Colaborador" para quem já teve um feedback aprovado.

Quem quer entrar no teste fechado deixa o e-mail em **Quero testar**, no
[site do jogo](https://johncoelho.github.io/naval-battle/#testador); a fila fica no Supabase
(`supabase/beta_waitlist.sql`) e os e-mails são levados à mão para o Play Console.

### Personalização

Dois eixos independentes, ambos comprados com dobrões:

- **Linha de casco** — muda a silhueta das cinco embarcações (boca, proa, superestrutura,
  chaminés): Padrão (inclusa), Imperial, Atlântica e Fantasma.
- **Camuflagem** — muda a pintura e o padrão recortado no casco: lisa, dazzle, estilhaço,
  faixas de linha d'água e retículo digital. Onze pinturas, duas inclusas.

### Ícone, abertura e materiais da loja

Ícone "Acerto no casco" (cruzador e destróier do jogo em perspectiva 3D sobre a carta de tiro).
Android: ícone adaptativo com a arte em `drawable-xxxhdpi/ic_launcher_art.png`; iPhone:
`AppIcon-1024.png`; ficha da Play: `assets/store/icon-512.png`. As alternativas de ícone, banner
da loja e tela de abertura ficam no quadro do Claude Design do projeto. Banner da Play:
`assets/store/feature-graphic-1024x500.png`. Abertura (`ui/SplashScreen.kt`): frota do jogo em
perspectiva sobre a carta, radar, tiros na água e um acerto em chamas, com a barra e as frases
de convés.

Tudo o que vai para a ficha da Play fica em `assets/store/`: textos (`listing-pt-BR.txt`), as
7 capturas com legenda (1080×1920; a de 2 jogadores é 1920×1080) e o trailer de 1 minuto
(`promo-video-1080p.mp4`, cenas gravadas no emulador com a trilha "Naval Battle" e os efeitos do
jogo). O site usa as mesmas telas, sem moldura, em `site/media/tela-*.webp`.

### Navegação

O botão/gesto "voltar" do Android volta para a tela anterior (`ui/SystemBack.kt`); dentro da
partida ele é ignorado, para não derrubar a batalha, e no menu fecha o app.

### Menu inicial

O deque de comando (`ui/MenuScreen.kt`), de cima para baixo:
- **Comandante** — avatar, nome, patente e a **barra até a próxima patente** ("Marinheiro
  em 300 XP"); milhas (em vermelho com 2 ou menos), dobrões e ajustes. Uma dica única
  explica milhas e dobrões na primeira vez.
- **Cartão do dia** — temporada (posição no placar ou o convite para aderir) e Diário de
  bordo (check-in pendente, desafio em andamento com barra, ou "tudo feito").
- **Frota** — em tela alta, a frota inteira em formação (os 5 navios da linha e
  camuflagem equipadas) sobre a carta náutica, com ondas descendo devagar de cima
  para baixo (espuma em degradê na crista, borrifo ao bater no casco, navio subindo quando
  ela passa — `SeaFormation`), torres girando, caças decolando e pousando no porta-aviões,
  esteira de bolhas e onda de proa (`drawShip(animSeconds = …)`, só no deque), ocupando o
  espaço livre; em tela baixa, só o
  porta-aviões. Tocar (ou "Estaleiro ›") abre o Estaleiro.
- **Jogar online** como ação principal e, abaixo, vs. IA, 2 jogadores e Rede local lado a
  lado — jogar e atalhos ficam juntos no pé da tela (zona do polegar).
- **Clássico/Tático é uma etapa do fluxo, não um seletor no deque**: tocar em vs. IA, 2
  jogadores, Rede local ou em Jogar (amigo) abre "Escolha o modo" (`ui/ModePicker.kt`),
  com o último modo usado marcado; no Online o modo fica na própria tela, junto de
  Casual/Ranqueada (pareia só com o mesmo modo). Quem entra por convite ou código joga no
  modo da sala — na rede local também (o convidado adota o modo do HELLO de quem abriu).
- **Atalhos** Amigos (com selo de pedidos recebidos), Placar, Loja (selo "-30%" com a
  oferta do dia, abre direto nas camuflagens) e Feedback (bug ou ideia, rende dobrões) —
  o Perfil abre pelo avatar do topo.
- Sem vãos entre os blocos: a sobra da tela vai para a frota, não para espaços vazios.

**Oferta do dia** (`Paint.dailyOffer`): uma camuflagem por dia, igual para todos, em
rodízio pela data de Brasília, com 30% de desconto — no topo do corredor de camuflagens;
pula as já compradas, as inclusas e as exclusivas do passe.

### Configurações

Tela própria (`ui/SettingsScreen.kt`, ícone de engrenagem no menu): trilha sonora e
efeitos sonoros com toggles separados, a seção **Online** ("Disponível para partida
rápida" e o tipo aceito: Casual, Ranqueada ou Ambas) e o seletor de idioma — que saiu do
Perfil para não ficar duplicado. Cor do oceano, espessura da grade, cor do alvo e notificações push
ainda não têm preferência própria (ficam no backlog).

Uma seção **Sobre** mostra a versão instalada, um botão para compartilhar o link da
loja (`data/UpdateChecker.kt#shareStoreListing`) e leva à tela de **Novidades**
(`ui/ReleaseNotesScreen.kt`) — resumo de uma linha por versão publicada, para quem
joga; o changelog técnico completo continua só em `CHANGELOG.md`.

### Idiomas

Português do Brasil, inglês e espanhol, trocáveis em Configurações e aplicados na hora.
Todo o texto vive em `i18n/Strings.kt`, com as três versões de cada frase na mesma linha.

A **Loja do Arsenal** vende; o **Estaleiro** combina o que já foi conquistado — mostra só
as linhas de casco e as pinturas que o comandante já tem, com um cartão que leva direto à
aba certa da loja para conseguir mais. Toda compra
passa por um cartão de confirmação (preço, saldo e saldo depois); item sem saldo aparece
apagado, com cadeado e "faltam ◆ X". A prévia de cada casco/pintura desliza pelas cinco
classes de navio.

### Como a rede local funciona

As frotas são trocadas no começo da partida e, dali em diante, só as jogadas viajam
(`ACT|x|y`). Os dois aparelhos rodam a mesma partida e resolvem cada tiro de forma
idêntica — nada precisa ser confirmado de volta. A descoberta usa NSD/mDNS, o mesmo
mecanismo do Bonjour, o que deixa o caminho pronto para o iOS.

Como cada aparelho conhece a frota do outro para resolver os tiros, um cliente adulterado
poderia espiar. É aceitável entre amigos na mesma rede; quando houver ranqueada valendo
pontuação, a resolução passa para o lado do dono da frota.

### Como o modo Online funciona

Mesmo protocolo de texto do modo rede local (`data/Protocol.kt`), o transporte é que muda:
em vez de socket TCP, cada jogada vira uma linha na tabela `online_messages` do Supabase, e
cada lado consulta por linhas novas a cada 1-2 segundos (`data/OnlineLink.kt`, puro
`commonMain` — não precisa de código por plataforma, porque só reaproveita o `CloudApi` que
já fala com o Supabase). Sala de amigo e partida rápida vivem em `online_matches`; quem
cria sempre joga primeiro. Amizades (`friendships`) e a busca de comandante por nome
(`search_commander`) são independentes da sala — servem para montar a lista e convidar
depois. Detalhe do schema em [supabase/online.sql](supabase/online.sql).

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
    composeResources/files/  efeitos e trilha, lidos pelos dois lados via Res.readBytes
  androidMain/    Activity, manifesto, recursos, e os actual de áudio/dados
  iosMain/        actual de preferências, nuvem, modo Online e áudio — rede e Google
                  ainda pendentes (dependem do projeto Xcode, que ainda não existe)
supabase/schema.sql   tabela profiles, RLS, gatilhos e placar
supabase/online.sql   salas, jogadas e amizades do modo Online
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
- `music_battle.ogg` / `music_battle_intense.ogg` — tensão naval em ré menor a 72 bpm
  (zumbido grave, pad Dm–Bb–Gm–A, ostinato de cordas graves e tambores esparsos),
  **sintetizada do zero** por [tools/audio/synth-battle.js](tools/audio/synth-battle.js) —
  sem samples, sem licença de terceiros. A intensa (semicolcheias, caixa, trêmulo agudo)
  entra quando algum lado fica com um navio só e continua do mesmo ponto do compasso.
- `ambient_sea.ogg` — camada do mar por baixo da música no combate (ondas, casco rangendo,
  um ping de sonar por volta dos 22 s), sintetizada pelo mesmo script. A opção de trilha
  nos Ajustes desliga as duas camadas; os efeitos de tiro seguem.

Para trocar por uma faixa feita com IA ou de banco livre, basta substituir os arquivos
com os mesmos nomes (`.ogg` em `androidMain/res/raw`, `.m4a` em `composeResources/files`);
as duas faixas de combate precisam ter o mesmo andamento e duração para a troca casar.

---

## Roadmap

- [x] Login com Google — configurado de ponta a ponta (Google Cloud + Supabase),
      ver [docs/BUILD.md](docs/BUILD.md#login-com-google--configuração-do-lado-de-fora-do-código)
- [x] Convite de amigo e partida rápida pela internet — modo Online via REST do
      Supabase (sala de amigo com código, partida rápida, lista de amigos), com
      popup de espera/aceite dos dois lados e tela de Amigos dedicada
- [x] Ranqueada com temporadas por estação do ano e tela de Placar (geral e por
      temporada)
- [ ] Partida local por Nearby Connections (Bluetooth / Wi-Fi Direct)
- [ ] Compras com pagamento real (Google Play Billing) vendendo dobrões
- [~] Alvo iOS: preferências, nuvem, modo Online e áudio já têm `actual` de verdade;
      rede local e login com Google ainda são pendências (mudos/desativados por
      enquanto, para o resto do jogo compilar) — ver [docs/BUILD.md](docs/BUILD.md#ios--em-andamento).
      Projeto Xcode criado — `.ipa` **sem assinatura** sai como artefato de todo build
      (ver [docs/BUILD.md](docs/BUILD.md#o-projeto-xcode-e-o-ipa-sem-assinatura) para
      instalar via Sideloadly sem pagar Apple Developer Program; assinatura de
      verdade no CI fica para quando/se uma conta paga entrar em cena)
- [~] Publicação na Play Store: ficha da loja, classificação indicativa, público-alvo e
      segurança de dados já preenchidos na Play Console; `.aab` de release assinado sai
      do CI quando os secrets de keystore estão configurados (ver
      [docs/BUILD.md](docs/BUILD.md#chave-de-release-upload-key-da-play-store)) — falta
      rodar o teste fechado obrigatório (12+ testers por 14 dias) antes de pedir acesso
      à produção
