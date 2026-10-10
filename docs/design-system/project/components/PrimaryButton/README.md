A ação principal da tela: fundo `amber`, texto `amberInk`, no máximo um por tela.

**Código:** `ui/Components.kt` → `PrimaryButton(text, subtitle?, enabled, modifier, big, onClick)`.

- **Quando usar:** a próxima ação esperada (Jogar online, Confirmar frota, Comprar).
- **O consumidor fornece:** `text` (vira caixa alta), `subtitle` opcional, `enabled`, `big` para o cartão
  de destaque do deque, e o `onClick`.
- **Layout:** título em cima à esquerda (`button`), legenda embaixo à direita (`monoSmall`, `amberInk` a 70%),
  padding `space-16` × `space-14` (`space-22` se `big`). Nunca título e legenda na mesma linha.
- **Desabilitado:** fundo `surface2`, texto `muted`.
- **Não faça:** dois PrimaryButton na mesma tela; âmbar em botão destrutivo (use `danger` + confirmação).
