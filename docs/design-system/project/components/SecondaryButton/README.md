Alternativas e navegação: fundo `surface2`, borda `line`, texto `ink`.

**Código:** `ui/Components.kt` → `SecondaryButton(text, subtitle?, enabled, modifier, big, onClick)`.

- **Quando usar:** Voltar, Voltar ao deque, opções lado a lado (vs. IA / 2 jogadores / Rede local),
  a saída segura de uma confirmação.
- **Layout:** igual ao PrimaryButton (empilhado); legenda em `muted`.
- **Não faça:** usar como ação principal; inventar variação com cor própria.
