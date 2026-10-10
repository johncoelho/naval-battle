Rótulo curto de painel em `monoSmall`, caixa alta aplicada pelo próprio componente.

**Código:** `ui/Components.kt` → `HudLabel(text, color = muted, modifier)`.

- **Cores aceitas:** `muted` (padrão), `inkSoft` (cabeçalho), `amberStrong` (estado/valor), `greenBright`
  (sucesso), `danger` (erro curto).
- **Não faça:** `.uppercase()` à mão; frase longa (vira `body`).
