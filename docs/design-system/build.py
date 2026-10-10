# Gera os arquivos do design system "Naval Battle Command HUD" (artefato Claude Design) em
# docs/design-system/project/. Rodar: python docs/design-system/build.py  — depois publicar (skill design-system).
# a partir dos valores reais do código (design/Theme.kt, ui/Components.kt, MenuScreen.kt).
import json, os, datetime

ROOT = os.path.dirname(os.path.abspath(__file__))
P = os.path.join(ROOT, "project")
NOW = datetime.datetime.now(datetime.timezone.utc).replace(microsecond=0).isoformat().replace("+00:00", "Z")


def w(path, text):
    full = os.path.join(P, path)
    os.makedirs(os.path.dirname(full), exist_ok=True)
    with open(full, "w", encoding="utf-8", newline="\n") as f:
        f.write(text)


# ---------------------------------------------------------------- tokens
colors = [
    ("bg", "#080b07", "Fundo de toda tela. O jogo é sempre noturno: não segue o tema do sistema."),
    ("surface", "#101509", "Cartões e atalhos do deque, campo de busca, painéis em repouso."),
    ("surface2", "#161c10", "Botão secundário, campos, botão primário desabilitado, popups."),
    ("surface3", "#1f2717", "Estado selecionado: ModeChip escolhido, habilidade armada."),
    ("line", "#2b3520", "Bordas de botão secundário, chips não selecionados, divisórias fortes."),
    ("lineSoft", "#1b2214", "Bordas discretas (atalhos, painéis), trilho da UnderlineTab."),
    ("abyss", "#08120e", "Água do tabuleiro: centro do gradiente radial."),
    ("abyss2", "#0c1a14", "Água do tabuleiro: borda do gradiente radial."),
    ("gridLine", "#298ed17a", "Malha da carta náutica (greenBright a 16% de opacidade)."),
    ("ink", "#eff2e4", "Texto principal sobre bg, surface, surface2 e surface3."),
    ("inkSoft", "#b4c0a4", "Texto secundário e rótulos de cabeçalho sobre bg e surfaces; legenda de callout."),
    ("muted", "#939f88", "Rótulos de HUD, legendas, estados desabilitados, sobre bg e surfaces."),
    ("green", "#5f8f4a", "Estrutura do radar, borda de habilidade disponível, confirmação discreta."),
    ("greenBright", "#8ed17a", "Varredura do radar, sonar, contato encontrado, status online, sucesso."),
    ("amber", "#e6ac3f", "AÇÃO: fundo do PrimaryButton, borda do selecionado, selos de oferta. Nunca decoração."),
    ("amberStrong", "#ffc95c", "Texto/ícone de destaque em âmbar sobre bg e surfaces (valor, aba ativa, acerto)."),
    ("amberInk", "#1e1402", "Texto sobre amber (PrimaryButton, selo -30%)."),
    ("danger", "#e05a35", "Dano, afundado, encerrar, excluir, selo de contagem. Texto ink sobre danger tem só ~3.4:1: usar apenas em selo curto/bold."),
    ("commanderOne", "#48c9f0", "Identidade do comandante 1 no modo 2 jogadores. Identifica PESSOA, nunca estado."),
    ("commanderTwo", "#3ed598", "Identidade do comandante 2 no modo 2 jogadores. Identifica PESSOA, nunca estado."),
]
tokens = {
    "name": "Naval Battle Command HUD",
    "version": 1,
    "meta": {
        "source": "github", "repo": "johncoelho/naval-battle", "ref": "main",
        "package": "composeApp/src/commonMain/kotlin/br/com/navalbattle",
        "paths": {"tokens": ["design/Theme.kt"], "components": ["ui/Components.kt", "ui/MenuScreen.kt"],
                  "docs": ["docs/DESIGN_SYSTEM.md"]},
        "synced": NOW[:10],
        "note": "Valores em dp do Compose transcritos 1:1 como px. Fonte da verdade continua sendo o código Kotlin.",
    },
    "color": {
        "themes": [{"id": "night", "name": "Noturno"}],
        "tokens": [{"name": n, "value": v, "usage": u} for n, v, u in colors],
    },
    "type": {
        "fonts": [],
        "families": {
            "sans": "Roboto, system-ui, \"Segoe UI\", Helvetica, Arial, sans-serif",
            "mono": "\"Roboto Mono\", \"Droid Sans Mono\", ui-monospace, Menlo, Consolas, monospace",
        },
        "groups": [
            {"name": "Títulos e ação", "family": "sans", "styles": [
                {"name": "display", "fontSize": "30px", "lineHeight": "34px", "fontWeight": 900, "letterSpacing": "0.5px",
                 "sample": "BATALHA PELA INTERNET", "usage": "Título de tela, sempre em caixa alta (NavalType.display)."},
                {"name": "title", "fontSize": "20px", "lineHeight": "26px", "fontWeight": 800, "letterSpacing": "1px",
                 "sample": "ACERTO DIRETO!", "usage": "Números grandes, nomes, callout de combate (NavalType.title)."},
                {"name": "button", "fontSize": "15px", "lineHeight": "20px", "fontWeight": 700, "letterSpacing": "1.2px",
                 "sample": "JOGAR ONLINE", "usage": "Título de botão e campo, caixa alta (NavalType.button)."},
                {"name": "body", "fontSize": "14px", "lineHeight": "20px", "fontWeight": 400,
                 "sample": "Nenhum amigo no radar ainda.", "usage": "Texto corrido — raro no jogo; frases curtas (NavalType.body)."},
            ]},
            {"name": "Instrumentos", "family": "mono", "styles": [
                {"name": "mono", "fontSize": "12px", "lineHeight": "16px", "fontWeight": 500, "letterSpacing": "0.8px",
                 "sample": "CASUAL · CLÁSSICO", "usage": "Valores, dados, ModeChip (NavalType.mono)."},
                {"name": "monoSmall", "fontSize": "10px", "lineHeight": "14px", "fontWeight": 400, "letterSpacing": "1px",
                 "sample": "AGUARDANDO COORDENADA", "usage": "Rótulos de HUD via HudLabel, legendas de botão (NavalType.monoSmall)."},
                {"name": "timer", "fontSize": "26px", "lineHeight": "30px", "fontWeight": 700, "letterSpacing": "2px",
                 "sample": "00:17", "usage": "Cronômetro do turno (NavalType.timer)."},
            ]},
        ],
    },
    "spacing": {"tokens": [
        {"name": "space-3", "value": "3px", "usage": "Ícone de habilidade → nome embaixo."},
        {"name": "space-4", "value": "4px", "usage": "Título → legenda no botão normal; respiro de selo no canto."},
        {"name": "space-6", "value": "6px", "usage": "Rótulo → campo; ícone → rótulo no atalho do deque (Gap(6))."},
        {"name": "space-8", "value": "8px", "usage": "Entre botões irmãos; título → legenda no botão grande (Gap(8))."},
        {"name": "space-10", "value": "10px", "usage": "Padding vertical de ModeChip, UnderlineTab e atalho."},
        {"name": "space-14", "value": "14px", "usage": "Padding vertical do botão; entre blocos de uma seção (Gap(14))."},
        {"name": "space-16", "value": "16px", "usage": "Padding horizontal do botão e da tela de batalha."},
        {"name": "space-20", "value": "20px", "usage": "Gutter lateral das telas; entre seções (Gap(20))."},
        {"name": "space-22", "value": "22px", "usage": "Padding vertical do botão grande (big = true)."},
        {"name": "space-24", "value": "24px", "usage": "Antes de um grupo novo de conteúdo (Gap(24))."},
    ]},
    "radius": {"tokens": [
        {"name": "radius-none", "value": "0px", "usage": "TODO controle, cartão, painel, chip e popup: cantos retos de instrumento."},
        {"name": "radius-full", "value": "9999px", "usage": "Só selo numérico (badge), avatar e indicador de status — círculos."},
    ]},
    "stroke": {"tokens": [
        {"name": "stroke-hair", "value": "1px", "usage": "Borda padrão de botão, chip, painel, callout."},
        {"name": "stroke-ability", "value": "1.5px", "usage": "Borda do AbilityButton e do anel de avatar."},
        {"name": "stroke-tab", "value": "2px", "usage": "Sublinhado da UnderlineTab."},
    ]},
}
w("tokens.json", json.dumps(tokens, ensure_ascii=False, indent=2))

# ---------------------------------------------------------------- README
README = """O Naval Battle é um **centro de comando naval noturno**: carta de radar esverdeada no escuro,
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
"""
w("README.md", README)

# ---------------------------------------------------------------- bundle.css (estilos das prévias)
CSS = """/* Rendições estáticas dos componentes Compose do Naval Battle. Cada valor vem de um token;
   o segundo argumento do var() repete o valor do código para a prévia nunca ficar sem cor. */
html, body { background: var(--bg, #080b07); color: var(--ink, #eff2e4); margin: 0; }
body { font-family: Roboto, system-ui, "Segoe UI", Helvetica, Arial, sans-serif; padding: 16px 20px; }
.nb-stack { display: flex; flex-direction: column; gap: var(--space-8, 8px); max-width: 360px; }
.nb-row { display: flex; gap: var(--space-8, 8px); align-items: stretch; }
.nb-row > * { flex: 1; min-width: 0; }
.nb-label { font-family: "Roboto Mono", "Droid Sans Mono", ui-monospace, Menlo, Consolas, monospace;
  font-size: 10px; letter-spacing: 1px; text-transform: uppercase; color: var(--muted, #939f88); }
.nb-btn { display: flex; flex-direction: column; gap: var(--space-4, 4px);
  padding: var(--space-14, 14px) var(--space-16, 16px); border: 0; text-align: left; cursor: pointer; }
.nb-btn.big { padding-block: var(--space-22, 22px); gap: var(--space-8, 8px); }
.nb-btn .t { font-weight: 700; font-size: 15px; letter-spacing: 1.2px; text-transform: uppercase; }
.nb-btn .s { align-self: flex-end; font-family: "Roboto Mono", ui-monospace, monospace; font-size: 10px; letter-spacing: 1px; }
.nb-primary { background: var(--amber, #e6ac3f); color: var(--amberInk, #1e1402); }
.nb-primary .s { color: rgba(30, 20, 2, 0.7); }
.nb-primary.off { background: var(--surface2, #161c10); color: var(--muted, #939f88); }
.nb-secondary { background: var(--surface2, #161c10); color: var(--ink, #eff2e4); box-shadow: inset 0 0 0 1px var(--line, #2b3520); }
.nb-secondary .s { color: var(--muted, #939f88); }
.nb-danger { background: var(--danger, #e05a35); color: var(--bg, #080b07); }
.nb-chip { padding-block: var(--space-10, 10px); text-align: center; box-shadow: inset 0 0 0 1px var(--line, #2b3520);
  font-family: "Roboto Mono", ui-monospace, monospace; font-weight: 500; font-size: 12px; letter-spacing: 0.8px;
  text-transform: uppercase; color: var(--muted, #939f88); }
.nb-chip.on { background: var(--surface3, #1f2717); box-shadow: inset 0 0 0 1px var(--amber, #e6ac3f); color: var(--amberStrong, #ffc95c); }
.nb-tab { text-align: center; font-family: "Roboto Mono", ui-monospace, monospace; font-size: 10px; letter-spacing: 1px;
  text-transform: uppercase; color: var(--muted, #939f88); padding-block: var(--space-10, 10px);
  border-bottom: 2px solid var(--lineSoft, #1b2214); }
.nb-tab.on { color: var(--amberStrong, #ffc95c); border-bottom-color: var(--amber, #e6ac3f); }
.nb-top { display: flex; justify-content: space-between; align-items: center; }
.nb-callout { display: inline-flex; flex-direction: column; align-items: center; padding: 8px 18px;
  background: rgba(8, 11, 7, 0.92); box-shadow: inset 0 0 0 1px var(--c, #ffc95c); }
.nb-callout .m { font-weight: 800; font-size: 20px; letter-spacing: 1px; text-transform: uppercase; color: var(--c, #ffc95c); }
.nb-callout .sub { font-family: "Roboto Mono", ui-monospace, monospace; font-size: 10px; letter-spacing: 1px; color: var(--inkSoft, #b4c0a4); }
.nb-ability { display: inline-flex; flex-direction: column; align-items: center; gap: 3px; }
.nb-ability .box { position: relative; width: 48px; height: 48px; display: grid; place-items: center;
  box-shadow: inset 0 0 0 1.5px var(--green, #5f8f4a); }
.nb-ability.sel .box { background: var(--surface3, #1f2717); }
.nb-ability.off .box { box-shadow: inset 0 0 0 1.5px var(--line, #2b3520); }
.nb-ability .cd { position: absolute; top: -6px; right: -6px; width: 18px; height: 18px; display: grid; place-items: center;
  background: var(--bg, #080b07); box-shadow: inset 0 0 0 1px var(--muted, #939f88); font: 10px "Roboto Mono", monospace; color: var(--muted, #939f88); }
.nb-shortcut { position: relative; display: flex; flex-direction: column; align-items: center; gap: 6px;
  padding-block: var(--space-10, 10px); background: var(--surface, #101509); box-shadow: inset 0 0 0 1px var(--lineSoft, #1b2214); }
.nb-badge { position: absolute; top: 4px; right: 4px; width: 18px; height: 18px; border-radius: 9999px; display: grid; place-items: center;
  background: var(--danger, #e05a35); color: var(--ink, #eff2e4); font: 700 10px "Roboto Mono", monospace; }
.nb-tag { position: absolute; top: 4px; right: 4px; padding: 1px 4px; background: var(--amber, #e6ac3f); color: var(--amberInk, #1e1402);
  font: 10px "Roboto Mono", monospace; }
.nb-panel { padding: 12px 14px; background: var(--surface, #101509); box-shadow: inset 0 0 0 1px var(--lineSoft, #1b2214); }
.nb-overlay { background: rgba(8, 11, 7, 0.86); padding: 18px; }
.nb-dialog { background: var(--surface2, #161c10); box-shadow: inset 0 0 0 1px var(--amber, #e6ac3f); padding: 18px; display: flex; flex-direction: column; gap: 12px; }
.nb-dialog h3 { margin: 0; font-weight: 800; font-size: 20px; letter-spacing: 1px; color: var(--ink, #eff2e4); text-align: center; }
svg { display: block; }
"""
w("components/bundle.css", CSS)

ICON_SONAR = '<svg width="26" height="26" viewBox="0 0 26 26" fill="none" stroke="currentColor" stroke-width="1.6"><circle cx="13" cy="13" r="10"/><circle cx="13" cy="13" r="5.5"/><circle cx="13" cy="13" r="1.6" fill="currentColor"/></svg>'
ICON_FRIENDS = '<svg width="26" height="26" viewBox="0 0 26 26" fill="none" stroke="currentColor" stroke-width="1.8" stroke-linecap="round"><circle cx="9" cy="9" r="3.6" opacity=".6"/><path d="M3 21c0-3.6 2.7-6 6-6" opacity=".6"/><circle cx="16.5" cy="9.5" r="3.8"/><path d="M10 22c0-4 2.9-6.5 6.5-6.5S23 18 23 22"/></svg>'

comps = {
    "PrimaryButton": ("Ações", 190, """A ação principal da tela: fundo `amber`, texto `amberInk`, no máximo um por tela.

**Código:** `ui/Components.kt` → `PrimaryButton(text, subtitle?, enabled, modifier, big, onClick)`.

- **Quando usar:** a próxima ação esperada (Jogar online, Confirmar frota, Comprar).
- **O consumidor fornece:** `text` (vira caixa alta), `subtitle` opcional, `enabled`, `big` para o cartão
  de destaque do deque, e o `onClick`.
- **Layout:** título em cima à esquerda (`button`), legenda embaixo à direita (`monoSmall`, `amberInk` a 70%),
  padding `space-16` × `space-14` (`space-22` se `big`). Nunca título e legenda na mesma linha.
- **Desabilitado:** fundo `surface2`, texto `muted`.
- **Não faça:** dois PrimaryButton na mesma tela; âmbar em botão destrutivo (use `danger` + confirmação).
""", """<div class="nb-stack">
  <button class="nb-btn nb-primary big"><span class="t">Jogar online</span><span class="s">Ranqueada e amigos · Temporada de Primavera</span></button>
  <button class="nb-btn nb-primary"><span class="t">Confirmar</span></button>
  <button class="nb-btn nb-primary off"><span class="t">Enviar</span><span class="s">mínimo de 20 caracteres</span></button>
</div>"""),
    "SecondaryButton": ("Ações", 150, """Alternativas e navegação: fundo `surface2`, borda `line`, texto `ink`.

**Código:** `ui/Components.kt` → `SecondaryButton(text, subtitle?, enabled, modifier, big, onClick)`.

- **Quando usar:** Voltar, Voltar ao deque, opções lado a lado (vs. IA / 2 jogadores / Rede local),
  a saída segura de uma confirmação.
- **Layout:** igual ao PrimaryButton (empilhado); legenda em `muted`.
- **Não faça:** usar como ação principal; inventar variação com cor própria.
""", """<div class="nb-stack">
  <div class="nb-row">
    <button class="nb-btn nb-secondary"><span class="t">Vs. IA</span><span class="s">treino rápido</span></button>
    <button class="nb-btn nb-secondary"><span class="t">Rede local</span><span class="s">mesmo Wi-Fi</span></button>
  </div>
  <button class="nb-btn nb-secondary"><span class="t">Voltar ao deque</span></button>
</div>"""),
    "ModeChip": ("Seleção", 110, """Escolha entre poucas opções lado a lado (Clássico/Tático, Casual/Ranqueada, Bug/Melhoria).

**Código:** `ui/Components.kt` → `ModeChip(label, selected, modifier, enabled, onClick)`.

- **Selecionado:** fundo `surface3`, borda `amber`, texto `amberStrong`. Não selecionado: borda `line`,
  texto `muted`. Desabilitado: `muted` a 50%.
- **Layout:** padding vertical `space-10`, texto `mono` em caixa alta, chips em `Row` com `weight(1f)`
  e espaço `space-8`.
- **Não faça:** usar para ação (use botão) ou para mais de 3–4 opções.
""", """<div class="nb-stack">
  <div class="nb-row"><div class="nb-chip on">Clássico</div><div class="nb-chip">Tático</div></div>
  <div class="nb-row"><div class="nb-chip">Casual</div><div class="nb-chip on">Ranqueada</div></div>
</div>"""),
    "UnderlineTab": ("Navegação", 90, """Aba de navegação (Loja, Placar): só texto e sublinhado de `stroke-tab`.

**Código:** `ui/Components.kt` → `UnderlineTab(label, selected, modifier, onClick)`.

- **Selecionada:** texto `amberStrong`, sublinhado `amber`. Demais: `muted` sobre trilho `lineSoft`.
- **Por que sem caixa:** não confundir com o item equipado, que usa borda âmbar.
""", """<div class="nb-row" style="max-width:360px">
  <div class="nb-tab">Cascos</div><div class="nb-tab on">Camuflagens</div><div class="nb-tab">Habilidades</div><div class="nb-tab">Dobrões</div>
</div>"""),
    "HudLabel": ("Texto", 80, """Rótulo curto de painel em `monoSmall`, caixa alta aplicada pelo próprio componente.

**Código:** `ui/Components.kt` → `HudLabel(text, color = muted, modifier)`.

- **Cores aceitas:** `muted` (padrão), `inkSoft` (cabeçalho), `amberStrong` (estado/valor), `greenBright`
  (sucesso), `danger` (erro curto).
- **Não faça:** `.uppercase()` à mão; frase longa (vira `body`).
""", """<div class="nb-stack">
  <span class="nb-label">Descreva com detalhes</span>
  <span class="nb-label" style="color:var(--amberStrong,#ffc95c)">Aguardando aceitar…</span>
  <span class="nb-label" style="color:var(--greenBright,#8ed17a)">1 comandante encontrado</span>
</div>"""),
    "ScreenTopBar": ("Navegação", 70, """Cabeçalho de tela: contexto à esquerda (`inkSoft`), estado ou saldo à direita (`amberStrong`).

**Código:** `ui/MenuScreen.kt` → `ScreenTopBar(left, right)` e `ScreenTopBar(left, coins)`.

- Sempre a primeira linha da tela, dentro do gutter `space-20`.
""", """<div class="nb-top" style="max-width:360px">
  <span class="nb-label" style="color:var(--inkSoft,#b4c0a4)">Online</span>
  <span class="nb-label" style="color:var(--amberStrong,#ffc95c)">Pronto</span>
</div>"""),
    "CalloutBanner": ("Feedback", 130, """Mensagem de combate, sempre **abaixo** do tabuleiro, em posição fixa.

**Código:** `ui/Components.kt` → `CalloutBanner(callout)`.

- **Cor pelo tom:** acerto `amberStrong`, afundado `danger`, varredura `greenBright`, água/info `inkSoft`.
- **Layout:** fundo `bg` a 92%, borda `stroke-hair` na cor do tom, principal em `title`, legenda em
  `monoSmall`/`inkSoft`. Some sozinho (1,9 s + 50 ms por caractere da legenda, até 5 s).
""", """<div class="nb-row" style="max-width:380px;align-items:center">
  <div class="nb-callout" style="--c:var(--amberStrong,#ffc95c)"><span class="m">Acerto direto!</span><span class="sub">Porta-aviões atingido · F5</span></div>
  <div class="nb-callout" style="--c:var(--greenBright,#8ed17a)"><span class="m">Sonar</span><span class="sub">2 contatos</span></div>
</div>"""),
    "AbilityButton": ("Combate", 110, """Habilidade tática: quadrado de 48 com ícone de traço, nome embaixo e selo de recarga.

**Código:** `ui/Components.kt` → `AbilityButton(ability, name, enabled, selected, cooldown, onClick)`.

- **Disponível:** borda `green` (`stroke-ability`), ícone `greenBright`. **Armada:** fundo `surface3`, ícone
  `amberStrong`. **Indisponível:** borda `line`, ícone `muted`.
- **Recarga:** selo quadrado 18 no canto, fundo `bg`, borda e número `muted`.
- Ao lado, a descrição do efeito em `monoSmall` (a barra fica ao lado da própria frota).
""", f"""<div class="nb-row" style="max-width:260px">
  <div class="nb-ability"><div class="box" style="color:var(--greenBright,#8ed17a)">{ICON_SONAR}</div><span class="nb-label" style="color:var(--inkSoft,#b4c0a4)">Sonar</span></div>
  <div class="nb-ability sel"><div class="box" style="color:var(--amberStrong,#ffc95c)">{ICON_SONAR}</div><span class="nb-label" style="color:var(--inkSoft,#b4c0a4)">Armada</span></div>
  <div class="nb-ability off"><div class="box" style="color:var(--muted,#939f88)">{ICON_SONAR}<span class="cd">3</span></div><span class="nb-label">Recarga</span></div>
</div>"""),
    "Shortcut": ("Navegação", 110, """Atalho do deque (Amigos, Placar, Loja, Feedback): ícone de traço + rótulo, com selo opcional.

**Código:** `ui/MenuScreen.kt` → `Shortcut(label, badge, tag, icon, onClick)` (privado do deque).

- Fundo `surface`, borda `lineSoft`, ícone e rótulo `inkSoft`.
- **Selo numérico** (`badge` > 0): círculo `radius-full` 18 em `danger`, número em `ink` (até "9+").
- **Etiqueta** (`tag`, ex.: "-30%"): retângulo `amber` com texto `amberInk`.
- Se outra tela precisar de atalho igual, promova para `ui/Components.kt` antes de copiar.
""", f"""<div class="nb-row" style="max-width:300px">
  <div class="nb-shortcut" style="color:var(--inkSoft,#b4c0a4)">{ICON_FRIENDS}<span class="nb-label" style="color:var(--inkSoft,#b4c0a4)">Amigos</span><span class="nb-badge">1</span></div>
  <div class="nb-shortcut" style="color:var(--inkSoft,#b4c0a4)">{ICON_SONAR}<span class="nb-label" style="color:var(--inkSoft,#b4c0a4)">Loja</span><span class="nb-tag">-30%</span></div>
</div>"""),
    "ConfirmOverlay": ("Feedback", 200, """Popup e confirmação: sobreposição escura que cobre a tela com um cartão de borda `amber`.

**Código (padrão, não componente único):** `InviteBanner.kt`, `OpponentFoundPopup.kt`, `DailyPopup.kt`,
confirmação de Encerrar em `BattleScreen.kt`, Excluir conta em `ProfileScreen.kt`.

- **Estrutura:** rótulo `HudLabel` (contexto) → título `title` centralizado → conteúdo → ações em linha
  (segura à esquerda como SecondaryButton, ação à direita como PrimaryButton; destrutiva em `danger`).
- **Destrutivo:** sempre pede confirmação; o botão seguro vem primeiro.
- **Promover:** a 3ª variação nova deste padrão vira componente compartilhado em `ui/Components.kt`.
""", """<div class="nb-overlay" style="max-width:380px">
  <div class="nb-dialog">
    <span class="nb-label" style="text-align:center">Convite recebido</span>
    <h3>QA02 te convidou</h3>
    <div class="nb-row"><button class="nb-btn nb-secondary"><span class="t">Recusar</span></button><button class="nb-btn nb-primary"><span class="t">Aceitar</span></button></div>
  </div>
</div>"""),
}
for name, (group, height, readme, html) in comps.items():
    w(f"components/{name}/README.md", readme)
    w(f"components/{name}/preview.html", f'<!-- @dsCard group="{group}" height={height} -->\n{html}\n')

# ---------------------------------------------------------------- asset group READMEs
w("assets/Logos/README.md", "Ícone do app (512 px, PNG). Usar só na loja, no site e em materiais de divulgação; dentro do app a marca é desenhada em Canvas.\n")
w("assets/Telas/README.md", "Capturas reais do app (1080×1920; a de dois jogadores é 1920×1080), as mesmas da ficha da Play. Referência de composição para telas novas: conferir gutter, hierarquia e uso de âmbar.\n")

# ---------------------------------------------------------------- cover
COVER = """<!-- @dsCard height=300 -->
<style>
  .cv { position: relative; width: 960px; height: 300px; background: var(--bg, #080b07); overflow: hidden; }
  .cv svg { position: absolute; left: 0; top: 0; }
  .g { fill: var(--green, #5f8f4a); } .gb { fill: var(--greenBright, #8ed17a); } .am { fill: var(--amber, #e6ac3f); }
  .ab { fill: var(--abyss2, #0c1a14); } .s3 { fill: var(--surface3, #1f2717); } .dg { fill: var(--danger, #e05a35); }
  .grid { stroke: var(--green, #5f8f4a); stroke-width: 1; opacity: .55; fill: none; }
  .name { position: absolute; left: 40px; bottom: 54px; width: 430px; margin: 0;
    font: 900 64px/0.95 Roboto, system-ui, "Segoe UI", sans-serif; letter-spacing: 0.5px; color: var(--ink, #eff2e4); text-transform: uppercase; }
  .tag { position: absolute; left: 40px; bottom: 28px; width: 430px; margin: 0;
    font: 13px "Roboto Mono", ui-monospace, monospace; letter-spacing: 1px; color: var(--muted, #939f88); text-transform: uppercase; }
</style>
<div class="cv">
  <svg width="960" height="300" viewBox="0 0 960 300">
    <!-- blocos: abyss2 (água) 480×300 de base, green 200×140, surface3 160×80, amber 120×60 (ação), danger 40×40 (dano, pequeno)
         arranjo: carta náutica à direita, blocos encaixados como painéis do HUD, sangrando pela borda superior e direita
         padrão: malha de carta a cada 40px (10 casas, o tabuleiro) — o sistema é um instrumento de precisão sobre a grade
         escalas: passos de 20/40 (space-20 × 2), radius-none em tudo (cantos retos) -->
    <rect class="ab" x="480" y="0" width="480" height="300"/>
    <g class="grid">
      <path d="M520 0V300M560 0V300M600 0V300M640 0V300M680 0V300M720 0V300M760 0V300M800 0V300M840 0V300M880 0V300M920 0V300"/>
      <path d="M480 20H960M480 60H960M480 100H960M480 140H960M480 180H960M480 220H960M480 260H960"/>
    </g>
    <rect class="g" x="760" y="0" width="200" height="140"/>
    <rect class="s3" x="560" y="140" width="160" height="80"/>
    <rect class="am" x="720" y="220" width="120" height="60"/>
    <rect class="dg" x="880" y="180" width="40" height="40"/>
    <rect class="gb" x="600" y="60" width="40" height="40"/>
  </svg>
  <p class="name">Naval Battle Command HUD</p>
  <p class="tag">Centro de comando naval noturno</p>
</div>
"""
w("components/Cover/preview.html", COVER)

# ---------------------------------------------------------------- index (escrito por último)
assets = {
    "Logos": {"name": "Logos", "tile": "m", "order": ["naval-battle-icone-512.png"], "files": {
        "naval-battle-icone-512.png": {"name": "naval-battle-icone-512.png", "blob": "e48c0b3c83631939c7129672a95879c8", "size": 266207, "type": "image/png"}}},
    "Telas": {"name": "Telas", "tile": "l", "order": [], "files": {}},
}
telas = [("tela-01-abertura.png", "bb21dac3836655a4e411bb076d11f2c9", 954600), ("tela-02-deque.png", "32519c60c6f04b18c35ee7955627dfa3", 478049),
         ("tela-03-modos.png", "4e3c9e00272704dade6d81e49171210c", 300090), ("tela-04-posicionamento.png", "c9e763bb4e425a1cec234d31532c5d25", 395242),
         ("tela-05-combate.png", "9272da799ba8a9b695a70c686893b7a4", 530849), ("tela-06-dois-jogadores.png", "17dbcbfd2cc744bf80c51390b8328191", 403749),
         ("tela-07-loja.png", "d20065495978f8724a76c865b9b20f4f", 335175)]
for n, b, s in telas:
    assets["Telas"]["order"].append(n)
    assets["Telas"]["files"][n] = {"name": n, "blob": b, "size": s, "type": "image/png"}
index = {
    "v": 3, "layout": "files", "createdOnFiles": {"v": 1, "at": NOW},
    "title": "Naval Battle Command HUD", "namespace": "NavalHUD", "libraries": [],
    "sections": {}, "groups": ["Logos", "Telas"], "assetGroups": assets, "blobs": {},
    "docs": {"readme": "project/README.md", "sections": []},
    "lastChange": {"by": "John Coelho", "at": NOW, "via": "Claude Code · johncoelho/naval-battle",
                   "note": "Sistema criado a partir de design/Theme.kt e ui/Components.kt"},
}
w("design-system.json", json.dumps(index, ensure_ascii=False, indent=2))
files = []
for d, _, fs in os.walk(P):
    for f in fs:
        rel = os.path.relpath(os.path.join(d, f), ROOT).replace("\\", "/")
        files.append(rel)
print(json.dumps(sorted(files)))
