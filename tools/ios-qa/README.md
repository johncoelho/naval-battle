# QA iOS no simulador (#116)

Roteiro do Maestro que o `.github/workflows/ios-qa.yml` roda no runner macOS (disparo manual:
`gh workflow run ios-qa.yml --ref main`; artefato `ios-qa-<run>` com prints, vídeo e junit).

- `vs-ia.yaml` (IOS-SIM-01): convidado, vs. IA, Clássico, posicionamento, Confirmar, 1 tiro e resposta da IA;
  `assertNotVisible` de "Passe o aparelho" em 3 pontos, com a batalha visível em cada um.
- `local-controle.yaml` (IOS-SIM-02): controle positivo no modo local; sem ele, o "não vejo" da IA poderia ser árvore vazia.

Maestro é ferramenta só de CI (versão em `MAESTRO_VERSION` no workflow): não entra no app nem no build da loja.
- `captura-iap.yaml` (modo `capturas-iap`, #65): login com a conta de demonstração (`-e EMAIL`, `-e PASSWORD` vindos dos secrets `IOS_REVIEW_EMAIL`/`IOS_REVIEW_PASSWORD`), Loja, aba Dobrões e uma captura por produto com a folha de compra do StoreKit aberta. `NavalIAP.storekit` descreve os 4 produtos. Disparo: `gh workflow run ios-qa.yml --ref main -f modo=capturas-iap`.

Fica fora do simulador: Entrar com a Apple, push e compra (docs/QA_IPHONE.md).
Aprendido no ensaio (run 44443698499): o Maestro 1.41 não tem `--test-output-dir` (usa `--debug-output`); o texto casa pelo elemento INTEIRO, então regex precisa de `.*...*` (o título é "POSICIONAMENTO · COMANDANTE 1"); o simulador mostra o app em pt-BR mesmo com o sistema em en; o vs-ia passou com os 3 `assertNotVisible`.
Ajustes que podem faltar: toque por coordenada no tabuleiro (Canvas), nome do botão da tela de nomes e modelo do simulador.
