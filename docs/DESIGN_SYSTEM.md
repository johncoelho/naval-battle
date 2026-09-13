# Design system — Command HUD

A identidade do Naval Battle é um **centro de comando naval noturno**: carta de radar
esverdeada no escuro, âmbar reservado para ação, tipografia condensada em caixa alta.
Este documento é a referência para manter isso consistente em qualquer tela nova.

Código-fonte da verdade: `design/Theme.kt`, `design/Paint.kt`, `design/FleetLine.kt`,
`design/ShipArt.kt`, `design/InsigniaArt.kt` e `ui/Components.kt`.

## Princípios

1. **Noturno sempre.** O jogo não segue o tema do sistema. Um único mundo visual, escuro,
   em qualquer aparelho.
2. **Nada de bitmap.** Toda arte é vetorial, desenhada em Canvas. O APK não carrega imagem,
   e o mesmo desenho serve de 16 dp a tela cheia.
3. **Verde é o mar, âmbar é a ação.** Verde estrutura (radar, grade, contatos); âmbar marca
   o que se pode fazer agora; vermelho é dano e perigo. Cor não decora, informa.
4. **Texto é instrumento.** Rótulos em caixa alta com espaçamento largo, em fonte
   monoespaçada, imitam painel de instrumentos. Frase longa não é do jogo.
5. **Toda informação tem posição fixa.** O HUD não pula: espaços reservados mantêm altura
   mesmo vazios (o banner de mensagem abaixo do tabuleiro é o caso exemplar).

## Cores (`Naval`)

| Token | Hex | Uso |
|---|---|---|
| `bg` | `#080B07` | fundo geral |
| `surface` / `surface2` / `surface3` | `#101509` / `#161C10` / `#1F2717` | cartões, campos, estado selecionado |
| `line` / `lineSoft` | `#2B3520` / `#1B2214` | bordas e divisórias |
| `abyss` / `abyss2` | `#08120E` / `#0C1A14` | água do tabuleiro (gradiente radial) |
| `gridLine` | `#298ED17A` | malha da carta |
| `ink` / `inkSoft` / `muted` | `#EFF2E4` / `#B4C0A4` / `#6B7760` | texto principal, secundário, rótulo |
| `green` / `greenBright` | `#5F8F4A` / `#8ED17A` | radar, sonar, confirmação |
| `amber` / `amberStrong` / `amberInk` | `#E6AC3F` / `#FFC95C` / `#1E1402` | ação, destaque, texto sobre âmbar |
| `danger` | `#E05A35` | dano, perda, encerrar |
| `commanderOne` / `commanderTwo` | `#48C9F0` / `#F06AC2` | identidade dos dois comandantes no modo local |

**Regra das cores de comandante:** ciano e magenta existem para distinguir pessoas, não
estados. Nunca use uma delas para "sucesso" ou "erro", e nunca use verde/âmbar/vermelho
para identificar um jogador.

## Tipografia (`NavalType`)

| Estilo | Família | Peso / tamanho | Uso |
|---|---|---|---|
| `display` | sans | Black 30 | títulos de tela |
| `title` | sans | ExtraBold 20 | números grandes, nomes |
| `button` | sans | Bold 15 · +1.2 sp | botões e campos |
| `body` | sans | Normal 14 | texto corrido (raro) |
| `mono` | mono | Medium 12 · +0.8 sp | valores e dados |
| `monoSmall` | mono | Normal 10 · +1 sp | rótulos de HUD |
| `timer` | mono | Bold 26 · +2 sp | cronômetro de turno |

Famílias do sistema, de propósito: sem fonte externa o APK fica leve e nada depende de rede.
Rótulos passam por `HudLabel`, que aplica caixa alta — não escreva `.uppercase()` à mão.

## Componentes (`ui/Components.kt`)

| Componente | Quando usar |
|---|---|
| `PrimaryButton` | a ação principal da tela; fundo âmbar, no máximo um por tela |
| `SecondaryButton` | alternativas e navegação; contorno, fundo de superfície |
| `ModeChip` | escolha entre poucas opções lado a lado |
| `AbilityButton` | habilidade tática, com código, nome e recarga |
| `CalloutBanner` | mensagem de combate, sempre **abaixo** do tabuleiro |
| `HudLabel` | qualquer rótulo curto de painel |
| `ScreenTopBar` | cabeçalho: contexto à esquerda, estado à direita |
| `Gap` / `GapW` | espaçamento vertical e horizontal |

Ações destrutivas (encerrar partida, zerar carreira) usam `danger` e **pedem confirmação**
numa sobreposição que cobre a tela.

## Arte das embarcações

Cada classe é desenhada num viewBox de `(tamanho × 50) × 50`, vista de topo, **proa à
direita**. `drawShip(type, center, lengthPx, thicknessPx, vertical, skin, alpha)` posiciona
e escala; o desenho nunca conhece pixels de tela.

O visual é a combinação de dois eixos independentes, no tipo `Skin`:

- **`Paint`** (camuflagem) — quatro tokens de cor (`hull`, `deck`, `trim`, `dark`) mais um
  padrão `Camo` recortado no contorno do casco. O termo *libré* saiu do projeto: para quem
  joga é **pintura**, e o código acompanha.
- **`FleetLine`** (linha de casco) — parâmetros de silhueta: `beam` (multiplicador de
  largura), `prow` (padrão, clipper, bulbosa, facetada), `tower` e `funnels`.

**Ao criar item novo:** camuflagem nova é uma entrada em `Paint.all`; casco novo é uma
entrada em `FleetLine.all`. Nenhum desenho precisa ser refeito — essa é a razão de os dois
eixos serem paramétricos, e é o que torna barato produzir item de loja.

## Marcas no tabuleiro

| Marca | Desenho |
|---|---|
| Água | anel fino |
| Acerto | célula preenchida, borda e ponto central |
| Afundado | célula preenchida com X por cima |
| Sonar quente / frio | célula esverdeada / escurecida |

No modo local, a cor da marca é a do **dono da frota atingida** — é o que permite ler a
carta final sem legenda. Contra a IA, a marca usa a paleta padrão (vermelho para dano).

## Texto e idiomas

Nenhuma frase nasce dentro de uma tela. Todo texto entra como chave em `i18n/Strings.kt`,
com português, inglês e espanhol na mesma linha — se a frase muda, as três mudam juntas.
Na tela se usa `t(K.CHAVE)`, e `t(K.CHAVE, valor)` quando há um número ou nome no meio.

Traduzir é parte de criar a tela, não uma etapa posterior: tela nova sem as três versões
é tela pela metade.

## Movimento

- **Abertura** (3,6 s): grade acende, radar dá duas voltas, contatos acendem, clarão e
  carimbo da marca. Toque pula.
- **Disparo**: míssil viaja 420 ms, impacto explode, tabuleiro treme conforme o resultado.
- **Naufrágio** (2,6 s): o navio inclina, pega fogo, submerge; tripulação salta; destroços.
- **Radar**: varredura contínua de 5 s no tabuleiro em foco; parada nos mapas inativos.

Animação sempre serve à leitura do jogo: mostra o que aconteceu, onde, e de quem foi.
Movimento sem informação não entra.

## Som

Efeitos com três variantes sorteadas e afinação aleatória no tiro na água (é o som mais
frequente, e repetição cansa). Alarme de bordo só quando a **sua** frota é atingida — é a
diferença que informa quem levou o tiro. Trilha em laço, mais baixa no combate.

Detalhes de licença e produção no [README](../README.md#créditos-de-áudio).
