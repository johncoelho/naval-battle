# Histórico de alterações

Todas as mudanças relevantes do Naval Battle, da mais recente para a mais antiga.
Cada entrada aponta os commits que a compõem, para o versionamento servir de resgate.

Formato inspirado em [Keep a Changelog](https://keepachangelog.com/pt-BR/1.1.0/).
Atualizar este arquivo é obrigatório a cada versão compilada — ver
[docs/BUILD.md](docs/BUILD.md#regra-dos-artefatos).

---

## [0.12.0] — 2026-09-13 · Primeiro compile para iOS

### Adicionado
- Alvos `iosX64`, `iosArm64` e `iosSimulatorArm64` em `composeApp/build.gradle.kts`,
  com o framework nomeado `ComposeApp` (o que um projeto Xcode importaria).
- `.github/workflows/ios.yml`: compila o framework Kotlin/Native num runner
  `macos-latest` a cada push — sem Mac à mão, é a única forma de saber se o
  multiplataforma continua compilando para iOS.
- `composeApp/src/iosMain`: `MainViewController.kt` (ponto de entrada via
  `ComposeUIViewController`), `Prefs.ios.kt` (via `NSUserDefaults`) e `Cloud.ios.kt`
  (via `NSURLSession`, mesma lógica do cliente Android) — os dois primeiros `actual`
  de verdade do alvo iOS.
- `SoundPlayer.ios.kt`, `MusicPlayer.ios.kt`, `GoogleAuth.ios.kt`, `LanLink.ios.kt`:
  compilam, mas ficam mudos/desativados de propósito por enquanto — áudio, rede local
  e login com Google no iOS ficam para uma etapa seguinte. Detalhes do que falta em
  cada um em [docs/BUILD.md](docs/BUILD.md#ios--em-andamento).

### Nota de projeto
Ainda não existe projeto Xcode no repositório — este build prova que o código Kotlin
compila para iOS, não que o jogo já roda num simulador ou iPhone. Isso e a assinatura
para dispositivo físico (precisa de conta Apple Developer) são os próximos passos.

## [0.11.2] — 2026-09-13 · Login com Google liberado para todo mundo

### Adicionado
- **Página de privacidade** ([site/privacy.html](site/privacy.html), publicada em
  `johncoelho.github.io/naval-battle/privacy.html`): o que o jogo guarda com e sem
  conta, o que o login do Google devolve, e como pedir a exclusão dos dados.

### Configurado
- App "Naval Battle" **publicado em produção** no Google Auth Platform (saiu do modo
  de teste) — a página de privacidade era o único requisito que faltava. Qualquer conta
  Google agora consegue entrar, não só os e-mails cadastrados como testador.

## [0.11.1] — 2026-09-13 · Google configurado de ponta a ponta

### Configurado
- Projeto **AIGAMESFACTORY** no Google Cloud: tela de consentimento OAuth (app
  "Naval Battle", externa, em teste), cliente **Web** (redirect para o Supabase) e
  cliente **Android** (pacote `br.com.navalbattle` + SHA-1 da chave de depuração).
  Provedor Google ativado no Supabase com os dois Client IDs e o Client Secret do
  cliente Web. `GoogleAuthConfig.WEB_CLIENT_ID` preenchido — o botão "Entrar com o
  Google" já aparece e funciona. Passo a passo e estado atual em
  [docs/BUILD.md](docs/BUILD.md#login-com-google--configuração-do-lado-de-fora-do-código).

## [0.11.0] — 2026-09-13 · Login com Google

### Adicionado
- **"Entrar com o Google"** na tela de Conta, ao lado do cadastro por e-mail: pede a
  credencial pelo Credential Manager do Android e troca o token de identidade por uma
  sessão no Supabase (`CloudApi.signInWithGoogle`, `grant_type=id_token`). Cai na
  mesma carreira de uma conta por e-mail com o mesmo endereço — o gatilho
  `on_auth_user_created` já tratava login social desde o início.
- Botão só aparece com `GoogleAuthConfig.WEB_CLIENT_ID` preenchido — depende de
  configuração externa (Google Cloud Console + provedor Google no Supabase) que só
  o dono da conta consegue fazer. Passo a passo completo, com os links exatos, em
  [docs/BUILD.md](docs/BUILD.md#login-com-google--configuração-do-lado-de-fora-do-código).
- Novas dependências Android: `androidx.credentials` e
  `com.google.android.libraries.identity.googleid`, só para esse fluxo.

### Notas
- "Convidar um amigo para jogar online" (fora da rede local) ainda não está nesta
  versão — depende de um canal de verdade pela internet (a ideia é usar o Supabase
  Realtime, reaproveitando o mesmo protocolo que já roda na rede local), que é o
  próximo passo depois do login funcionar de ponta a ponta.

## [0.10.1] — 2026-09-13 · Corrige travamento do tático em rede

### Corrigido
- **Rede local perdia o dono da vez ao usar habilidades** (relatado sobretudo no
  tático): `LanLink.android.kt` abria uma thread nova a cada `send()`, sem nenhuma
  garantia de ordem entre chamadas. Usar reconhecimento aéreo ou sonar manda duas
  linhas em sequência — `ABIL` armando a habilidade, depois `ACT` com a coordenada —
  e se a segunda chegasse antes da primeira no outro aparelho, ele aplicava um tiro
  comum em vez do efeito da habilidade. Dali em diante os dois lados discordavam
  sobre de quem era a vez, e a partida travava para os dois.
- A mesma corrida também podia embaralhar uma sequência de **tiros consecutivos**
  (a regra "acertou, joga de novo" da v0.9.1 tornou isso bem mais comum, já que
  agora um jogador dispara várias vezes seguidas sem passar a vez).
- Correção: uma única thread escritora, nascida junto com a conexão, drena uma fila
  (`LinkedBlockingQueue`) em ordem estrita — `send()` só enfileira. Mesmo tiro ou
  habilidade disparados em sequência rápida agora chegam na ordem certa.

## [0.10.0] — 2026-09-13 · Revanche, provocações e tático repaginado

### Adicionado
- **Revanche em rede** — `Protocol.REMATCH`: cada lado pede pelo botão na tela de
  resultado; quando os dois pedem, a mesma ligação TCP recomeça a partida do zero
  (nova frota, nova posição) sem passar de novo por hospedar/procurar. Se a ligação
  cair enquanto se espera o outro lado, a tela avisa em vez de deixar o botão vivo.
- **Provocações em rede** — `Protocol.TAUNT`: ícone de rádio na barra da batalha abre
  um mural com seis emojis e seis gritos de guerra pré-definidos (traduzidos nos três
  idiomas); a escolha aparece como uma bolha por cima da tela de quem recebe, e some
  sozinha. Decoração pura — não participa da lógica da partida.
- **Ícone + descrição em cada habilidade tática** (`Ability.icon`/`.description`):
  em vez do código de duas ou três letras, cada botão mostra um glifo e o efeito por
  extenso ao lado, nos três idiomas.
- **Cartucho avulso de habilidade na Loja do Arsenal**: cada habilidade ativa tem um
  preço em créditos; comprar guarda um cartucho no estoque do aparelho (não viaja
  para a nuvem, como a trilha e o idioma). Na próxima partida tática, o cartucho
  libera o botão mesmo com a habilidade em recarga — some ao ser usado, o cooldown
  normal continua contando à parte. `Ability.abilityAvailable`/`selectAbility` ganham
  o parâmetro `ignoreCooldown`, e `Protocol.ability` passa a levar essa informação
  para o outro aparelho em rede, senão os dois lados divergiam sobre se o efeito
  realmente aconteceu.

### Alterado
- **Modo padrão volta a ser Clássico** — o Tático continua disponível, só deixou de
  vir pré-selecionado.
- **Layout do tático contra a IA e em rede**: o mapa da própria frota fica à esquerda
  e a coluna de habilidades à direita, lado a lado, em vez de empilhados — a barra de
  habilidades ganhou rolagem própria para não cortar a última linha em telas baixas
  (dobrável aberto na horizontal, tablet em paisagem).
- **Cor do segundo comandante** no modo local trocou de rosa para verde
  (`commanderTwo` `#F06AC2` → `#3ED598`), para não repetir o rosa em nenhum lugar da
  interface.
- `OwnFleetPanel` deixou de depender de receiver `ColumnScope`: agora recebe o
  `Modifier` pronto do chamador, o que permitiu dividir espaço com a coluna de
  habilidades dentro de uma `Row`.

## [0.9.1] — 2026-09-13 · Acertou, joga de novo

Regra clássica da batalha naval que faltava: quem acerta continua atirando, em vez de
passar a vez a cada tiro. Vale para os dois modos e todas as formas de jogar — IA, mesmo
aparelho e rede local, já que a resolução do tiro é idêntica nos três.

### Alterado
- `Match.act()` só passa o turno quando o tiro erra; acerto ou afundamento mantêm o
  mesmo atacante na jogada seguinte. A barragem dupla do Encouraçado continua garantindo
  o segundo tiro mesmo se o primeiro errar — ela soma à regra, não a substitui.
- Turno automático da IA (`BattleScreen`) passou a repetir o disparo enquanto for
  acertando, no mesmo ritmo de antes, em vez de atirar só uma vez por vez de posse da vez.
- O cronômetro de turno do jogador reinicia a cada tiro (não só quando a vez muda de
  dono), senão o disparo automático por tempo esgotado dispararia cedo demais numa
  sequência de acertos.
- Textos do modo Clássico, no jogo e na landing page, trocam "um tiro por turno" pela
  regra nova.

## [0.9.0] — 2026-09-13 · Rede local com a cara do single player

Depois do primeiro teste real em dois aparelhos: a experiência da rede local vira
idêntica à de jogar contra a IA, e ganha nome próprio.

### Adicionado
- **Nome da partida** ao criar um jogo em rede: o campo aparece antes do botão
  "Criar partida" e é isso que aparece na busca do outro aparelho — não mais o nome
  do comandante. Em branco, cai num nome padrão ("Partida de <comandante>").
- **Aviso de saída em rede**: ao encerrar a partida, o aparelho manda `QUIT` para o
  outro antes de fechar a ligação, e quem ficou leva a vitória na hora, sem esperar
  a vez de alguém que já saiu.

### Corrigido
- **A tela de batalha em rede tratava os dois jogadores como se estivessem no mesmo
  aparelho** — frota escondida, revezamento de visão, placar dos dois lados na mesma
  tela. Como cada um está no seu próprio celular, sem ver a tela do outro, isso só
  atrapalhava. Agora a rede usa exatamente a interface do modo solo: tabuleiro alvo
  grande, frota própria sempre visível ocupando a metade de baixo, alarme de bordo
  quando é atingido. O modo "mesmo aparelho" (hot-seat) continua com sua interface
  própria, que existe justamente para esconder a frota de quem está do lado.
- **O alarme de bordo soava para o lado errado** para quem entra numa partida em
  rede (não hospeda): o som de "minha frota foi atingida" e o de "eu acertei o tiro"
  estavam trocados nesse caso, porque o gatilho comparava o lado absoluto do tiro
  (`PLAYER`/`ENEMY`) em vez de compará-lo com o lado que aquele aparelho realmente
  comanda. Corrigido para comparar contra `match.mySide`.
- Corrigida uma corrida entre mandar o `QUIT` e fechar o socket: o envio acontece
  numa linha à parte, e fechar a ligação imediatamente podia derrubar a conexão
  antes do aviso sair. Agora há um respiro curto entre as duas coisas.

---

## [0.8.2] — 2026-09-13 · Trocar e recuperar senha

### Adicionado
- **Trocar senha**, dentro da tela de Conta, para quem já está logado: pede a senha
  atual, a nova e a confirmação. A senha atual é usada para reautenticar no Supabase
  Auth antes de aceitar a troca — ninguém troca a senha de uma sessão esquecida aberta
  no aparelho sem saber a senha de verdade.
- **Esqueci minha senha**, na tela de login: manda o e-mail de recuperação padrão do
  Supabase Auth (`/auth/v1/recover`) para o endereço digitado.
- `CloudApi.updatePassword` (PUT `/auth/v1/user`) e `CloudApi.sendPasswordReset`
  (POST `/auth/v1/recover`), com as mesmas mensagens de erro legíveis do resto do
  cliente — senha curta, senha igual à atual, limite de tentativas.

### Nota de projeto
Não existe forma de recuperar ou visualizar uma senha existente — nem o app, nem o
painel do Supabase leem a senha original, só o hash dela. Trocar e recuperar são os
dois únicos caminhos, e é exatamente o que esta versão entrega.

---

## [0.8.1] — 2026-09-13 · Landing page e carta maior

### Adicionado
- **Landing page** em [johncoelho.github.io/naval-battle](https://johncoelho.github.io/naval-battle/),
  publicada pelo GitHub Pages a cada mudança em `site/`. O botão de download aponta para a
  release rolante `latest`, então o mesmo link serve sempre a compilação mais nova; a página
  ainda consulta a API do GitHub para mostrar data e tamanho do APK atual.

### Alterado
- **A carta da própria frota, na batalha contra a IA, deixou de ter tamanho fixo** e passou
  a ocupar toda a faixa livre da metade de baixo, sempre quadrada. Os números foram para uma
  linha única acima dela. No modo clássico o ganho é maior, já que não há barra de
  habilidades disputando o espaço.

---

## [0.8.0] — 2026-09-13 · Três idiomas e vocabulário mais claro

### Adicionado
- **Português do Brasil, inglês e espanhol.** Um dicionário único (`i18n/Strings.kt`) guarda
  as três versões de cada frase lado a lado — juntas de propósito, porque é o que impede
  uma tradução de ficar para trás quando o texto muda. São 250 chaves cobrindo telas,
  avisos de combate, patentes, insígnias, classes de navio, pinturas e cascos.
- **Seletor de idioma no Perfil.** A troca vale na hora, sem reiniciar, e fica gravada
  no aparelho.

### Alterado
- **"Libré" virou "pintura".** O termo técnico do esquema de pintura de uma frota não
  dizia nada para quem joga. Na tela o jogo já falava *camuflagem*; agora o código
  acompanha: a classe `Livery` passou a se chamar `Paint`, e a documentação idem.
- Nome do comandante em branco agora exibe o título traduzido em vez do texto fixo
  "Comandante", o que também corrige o nome anunciado na rede local.

---

## [0.7.0] — 2026-09-13 · Rede local e embarcações com volume

Dois celulares no mesmo Wi-Fi passam a jogar um contra o outro, sem servidor.

### Adicionado
- **Modo rede local**: um aparelho anuncia a partida, o outro encontra e entra. A
  descoberta usa NSD (o mesmo mDNS do Bonjour, o que abre caminho para o iOS) e a
  conversa corre num socket TCP direto. Sem servidor, sem internet, sem dependência nova.
- **Protocolo de linha** (`HELLO`, `FLEET`, `ACT`, `ABIL`, `QUIT`): as frotas são
  trocadas no início e depois só as jogadas viajam. Os dois aparelhos rodam a mesma
  partida e resolvem cada tiro igual — inclusive as habilidades táticas.
- `Match` ganhou `mySide`: em rede, quem hospeda comanda o lado PLAYER e abre o fogo;
  quem entra comanda o ENEMY. O motor já era simétrico desde o modo local.
- Tela **Rede local** com criar, procurar, lista de partidas encontradas, estado da
  ligação e as três instruções de uso.

### Alterado
- **Arte das embarcações agora tem volume.** Luz fixa vindo de cima à esquerda: sombra
  projetada na água, gradiente de bordo a bordo no casco, fio de luz na amurada
  iluminada, superestruturas com face superior extrudada e torres com cúpula sombreada.
  A vista de topo continua chapada, mas lê como maquete.
- Partidas em rede não contam carreira, pela mesma razão do modo local: o placar de
  progresso é do aparelho, não da mesa.
- O relatório final mostra os dois comandantes por nome também em rede.

### Nota de projeto
A frota inteira é enviada ao adversário no início da partida, e cada aparelho resolve os
tiros localmente. É o desenho mais simples e o que mantém os dois lados sincronizados sem
mensagem de confirmação. Em compensação, um cliente adulterado poderia ler a frota
adversária — aceitável para jogo entre amigos na mesma rede, e a trocar por resolução no
lado do dono quando existir ranqueada valendo pontuação.

---

## [0.6.0] — 2026-09-13 · Conta na nuvem

A carreira deixa de morrer no aparelho: passa a ter conta, e a conta vive no Supabase.

### Adicionado
- **Cadastro e login** com e-mail, senha e nome de usuário, em tela própria (`AuthScreen`),
  acessível pelo menu e pelo cartão de conta dentro do Perfil. `6cf938e`, `47422de`
- **Cliente Supabase** escrito à mão sobre `HttpURLConnection` e `org.json`, sem somar
  biblioteca de rede ao APK: cadastro, login, renovação, leitura e gravação da carreira.
  `10f4b71`, `2186c00`
- **Sincronização automática** ao entrar, na abertura do app, ao fim de cada partida
  contra a IA, em cada compra e ao mudar nome ou insígnia. `6cf938e`, `3d5180a`
- **Regra de fusão** ao entrar: se a nuvem tiver mais XP ela desce; senão o aparelho sobe.
  O maior progresso nunca se perde. `47422de`
- **Renovação silenciosa da sessão**: o token do Supabase vive uma hora; agora um 401
  dispara o refresh e repete a chamada, e a abertura do app já revalida a sessão.
  `007a9c5`, `e46c44f`, `011ac72`
- **Esquema da base** em `supabase/schema.sql`: tabela `profiles` presa ao Auth, RLS por
  dono, gatilho que cria a carreira junto com a conta — inclusive em login social, para o
  Google cair na mesma carreira do e-mail — e view `leaderboard`. `acc115a`, `c940322`
- Preferência de trilha passa a ser gravada entre sessões. `d03a76c`

### Corrigido
- **Progresso zerava a cada APK novo.** A causa não era o app: o CI assinava cada build com
  uma chave de depuração nova, o Android recusava instalar por cima e a desinstalação
  apagava os dados. Passou a existir uma chave fixa versionada. `11f755d`, `fc4138b`
- Nome de usuário repetido derrubava o cadastro; agora ganha sufixo. `c940322`

### Alterado
- Títulos das telas viraram "Perfil" e "Conta" — a patente já aparece no conteúdo. `1c7b587`
- Permissão de internet no manifesto. `19d2b7c`

### Verificado
- Cadastro, login, gravação e isolamento testados por chamada real à API: com a chave
  anônima sozinha, sem sessão, a tabela devolve vazio. O RLS está segurando.

---

## [0.5.0] — 2026-09-13 · Loja, cascos e camuflagens

Personalização deixa de ser só troca de cor e vira item de verdade.

### Adicionado
- **Linhas de casco** (`FleetLine`), que mudam a silhueta das cinco embarcações por
  parâmetros de boca, proa, superestrutura e chaminés: Padrão, Imperial, Atlântica e
  Fantasma. `a836a8f`
- **Padrões de camuflagem** (`Camo`) recortados no contorno do casco: dazzle, estilhaço,
  faixas de linha d'água e retículo digital; três pinturas novas. `a836a8f`
- **Loja do Arsenal** com dois corredores, pré-visualização de cada item aplicado à frota,
  compra com créditos e aviso de quanto falta. `d871063`, `4471208`
- **Estaleiro** vira bancada de montagem: combina casco e camuflagem já conquistados.
  `d871063`

### Alterado
- O visual da frota passou a ser o tipo `Skin` (casco + camuflagem), trocando o antigo
  parâmetro `livery` em todas as telas. `6d62516`
- O perfil guarda os dois conjuntos de itens conquistados. `5fd251c`

---

## [0.4.0] — 2026-09-13 · Carreira, perfil e estaleiro

### Adicionado
- **Persistência local** (`Prefs` sobre `SharedPreferences`) — a base de tudo que vem
  depois. `71b6103`, `3307573`
- **Carreira**: XP, patentes de Recruta a Almirante, créditos ganhos por partida e folha
  de serviço completa. `bb4674b`
- **Perfil** com nome de guerra, seis insígnias vetoriais, barra de patente, estatísticas
  e "zerar carreira". `e646896`, `c7ca133`
- **Economia**: partidas contra a IA rendem XP e créditos, com bônus por precisão, navios
  afundados e vitória rápida; o relatório mostra o ganho e a promoção. `bb4674b`
- Librés passam a ter preço em créditos; entram Portugal e Dazzle 1918. `f963126`

---

## [0.3.0] — 2026-09-13 · Abertura, trilha e modo local maduro

### Adicionado
- **Tela de abertura "Varredura"**: grade acendendo, duas voltas de radar, contatos,
  clarão e carimbo da marca, com barra de carregamento e jargão naval. `aba4535`, `21b8a8b`
- **Trilha sonora**: laços de um minuto cortados em número inteiro de compassos, com rock
  naval de domínio público no menu e faixa CC0 mais contida no combate; botão de liga e
  desliga. `f9e75b8`, `aac0d86`, `73ef93e`, `f3c6d41`, `3fe6a40`, `e4cb16e`
- **Memória de tiro por comandante** no modo local: cada um vê só os próprios disparos, e
  a carta se revela inteira no fim da partida. `aba4535`
- Cores de identificação dos dois comandantes. `23143e5`

### Alterado
- O modo local passou por três formatos até achar o certo: troca de tela com aviso de
  passagem (`11f4d7f`), dois mapas lado a lado (`2f24572`) e, por fim, **uma carta só**
  com memória individual (`397c22e`, `aba4535`).
- Botão de encerrar partida com confirmação, e botões de voltar em todos os fluxos.
  `11f4d7f`, `bab627d`, `2f24572`

### Corrigido
- Ao virar o tabuleiro, a animação do disparo anterior tocava de novo e redesenhava o
  impacto sobre as marcas. Passou a animar só o disparo mais recente. `2e49601`
- O cronômetro não reiniciava depois da troca de vez. `2e49601`

---

## [0.2.0] — 2026-09-12 · Ação, som e sensação

### Adicionado
- Míssil que cruza a tela até o alvo, explosão, tremor do tabuleiro e sequência de
  naufrágio com fogo, fumaça, destroços e tripulação saltando ao mar.
- Efeitos sonoros reais, montados a partir de gravações de domínio público e CC0: água,
  explosão, naufrágio e assobio do projétil.
- Alarme de bordo que soa **só** quando a sua frota é atingida.
- Posicionamento por manipulação direta: toque no navio gira, arrasto reposiciona.

### Corrigido
- **Partidas que nunca terminavam**: a imersão do submarino marcava a célula como água,
  e células marcadas não aceitavam novo tiro — o navio ficava imortal e a vitória nunca
  chegava. A célula absorvida passou a contar para o afundamento. `6a9eb2d`, `f25e4a5`
- Tiro na água soava repetitivo e artificial: virou três gravações sorteadas com afinação
  variável.

---

## [0.1.0] — 2026-09-12 · MVP jogável

### Adicionado
- Motor de jogo completo: posicionamento, turnos, resolução de tiro e vitória. `14a86d3`
- IA de caça com padrão de paridade e perseguição de contatos.
- Modos Clássico e Tático, com as cinco habilidades por classe de navio.
- Tabuleiro de radar em Canvas e as cinco embarcações em vetor, repintáveis por libré.
- Identidade visual Command HUD (verde naval, âmbar de ação, tipografia de painel).

### Infraestrutura
- Build por GitHub Actions publicando o APK na release rolante `latest`. `ced8d40`, `db0b83c`
- Versões alinhadas em AGP 8.7.3 / Gradle 8.11.1 / SDK 35, depois de descobrir que o AGP 9
  não é compatível com o plugin multiplataforma do Kotlin. `a7e5a82`, `18d8fcd`, `4477997`, `7c237dc`

---

## Notas sobre o histórico

Os commits entre `6869cb2` e `b6bc64a` aparecem como "Add files via upload" porque o envio
foi feito pela interface web do GitHub — o `git push` local está quebrado nesta máquina
(o gerenciador de credenciais trava). O conteúdo de cada um está descrito nas entradas
acima, agrupado por tema.
