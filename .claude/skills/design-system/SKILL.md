---
name: design-system
description: Garante consistência visual do Naval Battle. Use ao criar ou alterar qualquer tela, popup, componente, cor, texto de interface, ícone ou animação do app, e para incluir no design system (código + docs + Claude Design) algo que ele ainda não tinha.
---

# Design system — Naval Battle Command HUD

Três lugares, sempre em sincronia:

1. **Código (fonte da verdade):** `design/Theme.kt` (`Naval`, `NavalType`), `ui/Components.kt`,
   `design/*Art.kt`.
2. **`docs/DESIGN_SYSTEM.md`**: regras e tabelas no repositório.
3. **Claude Design:** [Naval Battle Command HUD](https://claude.ai/artifact/LCZELzjB8RFDv2pdGo7HNg)
   — tokens, README de uso, componente com prévia, capturas reais.

## Antes de desenhar

- Leia o README do artefato (Artifact `read`, `path: project/README.md`) ou o `docs/DESIGN_SYSTEM.md`.
- Abra as capturas do grupo **Telas** no artefato ou rode o app no Pixel_6 para ver o contexto real.

## Ao construir

- Só tokens: nenhuma cor hex, `sp` ou `dp` solto numa tela. Cor por papel: verde estrutura, âmbar
  ação (um sólido por tela), `danger` dano/destrutivo, `commanderOne/Two` só para pessoas.
- Componentes existentes primeiro: `PrimaryButton`, `SecondaryButton`, `ModeChip`, `UnderlineTab`,
  `HudLabel`, `ScreenTopBar`, `CalloutBanner`, `AbilityButton`, padrão de popup/confirmação.
- Botão: título em cima à esquerda, legenda embaixo à direita. Cantos retos; círculo só em selo,
  avatar e status. Sem sombra.
- Texto: chave nova em `i18n/Strings.kt` com pt-BR, en e es na mesma linha; caixa alta em rótulo
  via `HudLabel`. Vocabulário do jogo (deque, comandante, dobrões, milhas, pintura).
- Arte: vetorial em Canvas (`DrawScope`), colorida por token. Nada de bitmap ou emoji como ícone.
- Animação só se informa algo do jogo; respeitar a posição fixa do HUD.
- Ações no pé da tela; destrutivo sempre com confirmação e saída segura ao lado.

## Quando o sistema não tem o que você precisa

1. Crie o componente em `ui/Components.kt` (compartilhado, parametrizado) ou o token em
   `design/Theme.kt`. Token novo precisa de papel claro; não duplique um existente.
2. `docs/DESIGN_SYSTEM.md`: linha na tabela de cores/tipografia/componentes.
3. Artefato: siga o `SKILL.md` do tipo Design System no próprio artefato. Em resumo:
   - **Componente:** `project/components/<Nome>/README.md` (primeira frase = resumo; quando
     usar, o que o consumidor fornece, estados, o arquivo Kotlin de origem) + `preview.html`
     (linha 1 `<!-- @dsCard group="…" height=N -->`, rendição estática com as classes de
     `project/components/bundle.css`; adicione classe nova lá se preciso).
   - **Token:** entrada em `project/tokens.json` (lista, com `usage`), arquivo enviado inteiro.
   - A fonte do artefato é versionada em `docs/design-system/project/…` (gerada por
     `docs/design-system/build.py`, que lê os valores do código transcritos nele). Para mudança
     pequena, edite o arquivo direto; para mudança grande, ajuste o `build.py` e rode de novo.
   - Publique só os arquivos mudados: `url` do artefato, `root: docs/design-system`,
     `file_path` = caminho absoluto de um deles, `files` = os demais. O índice
     `project/design-system.json` só vai se mudar título/asset: leia o do artefato logo antes,
     mantenha `createdOnFiles` e atualize `lastChange`.
4. Captura nova de tela relevante: subir no grupo **Telas** (asset + registro no índice).
5. Registre no CHANGELOG (e no HISTORY se for decisão de identidade).

## Conferência final

- Print da tela no emulador comparado às capturas de referência: gutter 20, hierarquia, âmbar único.
- Texto legível: `ink`/`inkSoft`/`muted` sobre `bg`/surfaces; nada de texto longo em `ink` sobre `danger`.
- As três línguas cabem sem cortar (trocar idioma nos Ajustes).
