Atalho do deque (Amigos, Placar, Loja, Feedback): ícone de traço + rótulo, com selo opcional.

**Código:** `ui/MenuScreen.kt` → `Shortcut(label, badge, tag, icon, onClick)` (privado do deque).

- Fundo `surface`, borda `lineSoft`, ícone e rótulo `inkSoft`.
- **Selo numérico** (`badge` > 0): círculo `radius-full` 18 em `danger`, número em `ink` (até "9+").
- **Etiqueta** (`tag`, ex.: "-30%"): retângulo `amber` com texto `amberInk`.
- Se outra tela precisar de atalho igual, promova para `ui/Components.kt` antes de copiar.
