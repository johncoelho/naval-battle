Dica da primeira partida guiada (#43): painel compacto com "Passo N de 4", título, uma linha de instrução e
**Pular** sempre visível (no último passo vira **Concluir**).

**Código:** `ui/GuidedOverlay.kt` → `GuidedTip(step, onSkip, onFinish)`.

- **Layout:** `surface2` com borda `amber` de 1 dp, padding 12/10 dp, rótulo do passo em `HudLabel`/`muted`, título em
  `title`/`amberStrong`, instrução em `body`/`inkSoft`. "Pular" em `danger`, "Concluir" em `amberStrong`.
- **Posição:** no posicionamento fica no fluxo da coluna (acima da frota em escala); na batalha fica fixa embaixo,
  sobre a frota própria, com `navalSafeArea()` e `navalPanelWidth()`. Nunca sobre o alvo.
- **Regra de ouro 8:** a dica não realça casa do alvo nem altera estado de célula.
