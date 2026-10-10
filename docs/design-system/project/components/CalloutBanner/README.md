Mensagem de combate, sempre **abaixo** do tabuleiro, em posição fixa.

**Código:** `ui/Components.kt` → `CalloutBanner(callout)`.

- **Cor pelo tom:** acerto `amberStrong`, afundado `danger`, varredura `greenBright`, água/info `inkSoft`.
- **Layout:** fundo `bg` a 92%, borda `stroke-hair` na cor do tom, principal em `title`, legenda em
  `monoSmall`/`inkSoft`. Some sozinho (1,9 s + 50 ms por caractere da legenda, até 5 s).
