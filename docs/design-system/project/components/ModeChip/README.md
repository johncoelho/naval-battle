Escolha entre poucas opções lado a lado (Clássico/Tático, Casual/Ranqueada, Bug/Melhoria).

**Código:** `ui/Components.kt` → `ModeChip(label, selected, modifier, enabled, onClick)`.

- **Selecionado:** fundo `surface3`, borda `amber`, texto `amberStrong`. Não selecionado: borda `line`,
  texto `muted`. Desabilitado: `muted` a 50%.
- **Layout:** padding vertical `space-10`, texto `mono` em caixa alta, chips em `Row` com `weight(1f)`
  e espaço `space-8`.
- **Não faça:** usar para ação (use botão) ou para mais de 3–4 opções.
