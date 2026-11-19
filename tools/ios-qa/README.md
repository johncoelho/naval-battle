# QA iOS no simulador (#116)

Roteiro do Maestro que o `.github/workflows/ios-qa.yml` roda no runner macOS (disparo manual:
`gh workflow run ios-qa.yml --ref main`; artefato `ios-qa-<run>` com prints, vídeo e junit).

- `vs-ia.yaml` (IOS-SIM-01): convidado, vs. IA, Clássico, posicionamento, Confirmar, 1 tiro e resposta da IA;
  `assertNotVisible` de "Passe o aparelho" em 3 pontos, com a batalha visível em cada um.
- `local-controle.yaml` (IOS-SIM-02): controle positivo no modo local; sem ele, o "não vejo" da IA poderia ser árvore vazia.

Maestro é ferramenta só de CI (versão em `MAESTRO_VERSION` no workflow): não entra no app nem no build da loja.
Fica fora do simulador: Entrar com a Apple, push e compra (docs/QA_IPHONE.md).
Ajustes prováveis no 1º ensaio: toque por coordenada no tabuleiro (Canvas), nome do botão da tela de nomes e modelo do simulador.
