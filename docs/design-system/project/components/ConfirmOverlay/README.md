Popup e confirmação: sobreposição escura que cobre a tela com um cartão de borda `amber`.

**Código (padrão, não componente único):** `InviteBanner.kt`, `OpponentFoundPopup.kt`, `DailyPopup.kt`,
confirmação de Encerrar em `BattleScreen.kt`, Excluir conta em `ProfileScreen.kt`.

- **Estrutura:** rótulo `HudLabel` (contexto) → título `title` centralizado → conteúdo → ações em linha
  (segura à esquerda como SecondaryButton, ação à direita como PrimaryButton; destrutiva em `danger`).
- **Destrutivo:** sempre pede confirmação; o botão seguro vem primeiro.
- **Promover:** a 3ª variação nova deste padrão vira componente compartilhado em `ui/Components.kt`.
