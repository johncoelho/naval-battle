O Naval Battle é um **centro de comando naval noturno**: carta de radar esverdeada no escuro,
âmbar reservado para ação, tipografia de instrumento em caixa alta. Use este sistema para
qualquer tela, popup ou componente novo do app. A fonte da verdade é o código Kotlin
(`design/Theme.kt`, `ui/Components.kt`); este sistema espelha esses valores e as regras de uso.
Se o código mudar, este sistema muda junto, no mesmo ciclo.

## Princípios

1. **Noturno sempre.** Um único tema, `night`. Toda tela tem fundo `bg`; nada segue o tema do sistema.
2. **Nada de bitmap.** Navios, insígnias, ícones, radar e abertura são vetores desenhados em Canvas.
   Não adicione imagem ao app; o ícone da loja é a única arte em arquivo.
3. **Verde é o mar, âmbar é a ação, vermelho é dano.** `green`/`greenBright` estruturam (radar, contato,
   online); `amber` marca o que se pode fazer agora; `danger` é dano, perda, encerrar. Cor informa, não decora.
4. **Texto é instrumento.** Rótulos curtos, caixa alta, mono com espaçamento largo (`monoSmall` via
   `HudLabel`). Frase longa não é do jogo.
5. **Posição fixa.** O HUD não pula: espaços reservados mantêm altura mesmo vazios (o callout abaixo do
   tabuleiro é o caso exemplar).
6. **Cantos retos.** `radius-none` em tudo; círculo (`radius-full`) só para selo numérico, avatar e status.

## Conteúdo e voz

- Português do Brasil, com inglês e espanhol na mesma linha de `i18n/Strings.kt`. Tela nova sem as três
  versões é tela pela metade. Nunca escreva texto direto na tela: use `t(K.CHAVE)`.
- Vocabulário naval do produto: **deque** (menu), **comandante**, **frota**, **dobrões** (moeda),
  **milhas náuticas**, **patente**, **estaleiro**, **varredura**. "Pintura", nunca "libré".
- Botões dizem a ação: "Jogar online", "Excluir para sempre", "Manter conta". Confirmação destrutiva
  sempre oferece a saída segura ao lado ("Continuar jogando" / "Encerrar partida").
- Mensagens de estado são curtas e concretas: "Aguardando coordenada", "Sem conexão — reconectando",
  "Convite recusado". Erro diz o que fazer: "Confira a conexão e tente de novo."
- Sem emoji na interface (as provocações do rádio são a exceção, num catálogo fixo).

## Cor

- Fundo de tela: `bg`. Cartão/atalho em repouso: `surface` com borda `lineSoft`. Controle secundário e
  popup: `surface2` com borda `line`. Selecionado: `surface3` com borda `amber`.
- Texto: `ink` principal, `inkSoft` secundário, `muted` rótulo e desabilitado — todos legíveis sobre
  `bg` e as três surfaces (≥ 6.5:1).
- Ação principal: fundo `amber`, texto `amberInk`. Destaque de valor ou aba ativa: `amberStrong`.
  **No máximo um `amber` sólido por tela** (o PrimaryButton).
- Dano e perigo: `danger` (texto sobre `bg` ≈ 5.4:1). Selo numérico usa `danger` com `ink` em `monoSmall`
  bold de 1–2 caracteres; não use `ink` sobre `danger` em texto corrido (≈ 3.4:1).
- Sucesso, online, sonar: `greenBright`. Nunca use verde para identificar jogador.
- `commanderOne` e `commanderTwo` só no modo 2 jogadores, para distinguir **pessoas**. Nunca para estado.
- Tabuleiro: gradiente radial `abyss` → `abyss2`, malha `gridLine`. Marca no tabuleiro: água = anel fino,
  acerto = célula preenchida com ponto, afundado = célula com X.

## Tipografia

- Famílias do sistema, de propósito (APK leve, sem rede): `sans` (Roboto no Android) e `mono`.
- `display` título de tela; `title` número/nome grande e callout; `button` botões e campos;
  `mono` valores e chips; `monoSmall` todo rótulo de HUD; `timer` cronômetro; `body` raro.
- Caixa alta: títulos, botões, chips e rótulos. Use `HudLabel` para rótulo (ele aplica a caixa alta);
  não escreva `.uppercase()` à mão em rótulo.

## Espaço e layout

- Gutter lateral das telas: `space-20` (batalha: `space-16`). Entre seções `space-20`/`space-24`,
  dentro de seção `space-14`, entre irmãos `space-8`.
- Botão: padding `space-16` × `space-14` (grande: × `space-22`). Título em cima à esquerda, legenda
  embaixo à direita, **sempre empilhados** — nunca lado a lado na mesma linha.
- Ações ficam no pé da tela (zona do polegar): primária em cima, "Voltar"/secundária embaixo.
- Bordas `stroke-hair`; habilidade e avatar `stroke-ability`; aba ativa `stroke-tab`.
- Sem sombra. Profundidade vem de surface mais clara + borda, nunca de elevação.

## Ícones e arte

- Ícones são funções `DrawScope` em traço (`design/AbilityArt.kt`, atalhos do deque, insígnias),
  coloridos por token (`inkSoft` em repouso, `greenBright` disponível, `amberStrong` selecionado,
  `muted` desabilitado). Nada de ícone de sistema ou emoji como ícone.
- Navios: viewBox `(tamanho × 50) × 50`, vista de topo, proa à direita. Visual = `Paint` (camuflagem)
  × `FleetLine` (linha de casco); item novo de loja é uma entrada em `Paint.all` ou `FleetLine.all`.
- Grupo **Logos**: o ícone do app (só loja/site). Grupo **Telas**: capturas reais para referência.

## Movimento e som

- Animação só quando informa: onde foi o tiro, de quem, o que afundou. Disparo 420 ms; naufrágio 2,6 s;
  radar contínuo de 5 s, com a borda acesa à frente e o rastro apagando atrás.
- Alarme de bordo só quando a **sua** frota é atingida.

## Estados

- Desabilitado: fundo `surface2`, texto `muted`, sem borda âmbar.
- Selecionado: `surface3` + borda `amber` + texto `amberStrong`.
- Destrutivo: `danger`, sempre com confirmação em sobreposição que cobre a tela.
- Carregando/esperando: radar girando + rótulo `amberStrong` ("Aguardando aceitar…") + botão Cancelar.

## Ao criar algo novo

1. Procure primeiro um componente aqui. Existe? Use-o, sem variação local.
2. Não existe? Crie em `ui/Components.kt` (compartilhado), com tokens deste sistema, e registre aqui:
   `components/<Nome>/README.md` + `preview.html`, e a linha em `docs/DESIGN_SYSTEM.md`.
3. Token novo (cor, tamanho) só com motivo claro: entra em `design/Theme.kt` e em `tokens.json`, no
   mesmo ciclo. Nunca escreva hex, sp ou dp solto numa tela.

## Componentes (rendições estáticas)

As prévias são **rendições estáticas em HTML/CSS** dos componentes Compose, feitas à mão a partir
dos arquivos Kotlin citados em cada README — servem para conferir aparência e regra de uso, não
são código executável do app.
