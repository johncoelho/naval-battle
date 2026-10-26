# Pendências do design system

- **Cartão de pacote da loja (#15, 0.33.0): Prêmio Beta e vitrine sem preço.** O cartão está em
  `docs/DESIGN_SYSTEM.md` (seção "Cartão de pacote da loja"), mas não entrou no artefato Claude Design
  `Naval Battle Command HUD` (`project/components/` e `project/tokens.json`). Pendente: criar
  `project/components/` do cartão do Prêmio e do estado "Indisponível agora" da vitrine (com
  `preview.html`), atualizar `build.py` e publicar pela skill `design-system`, passo 3. Não foi feito
  no lote 0.33.0 porque o artefato precisa de leitura e publicação próprias.

- **Dica da partida guiada (#43, 0.36.0).** Componente `GuidedTip` e os dois popups (Primeira missão e Presente de
  boas-vindas) estão em `docs/DESIGN_SYSTEM.md` e em `project/components/GuidedTip/`, mas o artefato Claude Design
  `Naval Battle Command HUD` ainda não foi republicado com eles (skill `design-system`, passo 3).

- **Cartão Convidar amigo (#44, 0.38.0).** `ReferralCard` e os popups do convite estão em `docs/DESIGN_SYSTEM.md`, mas o
  artefato Claude Design `Naval Battle Command HUD` ainda não foi republicado com eles (skill `design-system`, passo 3).

- **Popup de Denunciar e Bloquear (#58, 0.42.0).** `PlayerActionsPopup`, `ReportReasonPopup`, `BlockConfirmPopup` e `NameResetPopup` estão em
  `docs/DESIGN_SYSTEM.md`, mas o artefato Claude Design `Naval Battle Command HUD` ainda não foi republicado com eles (skill `design-system`, passo 3).
