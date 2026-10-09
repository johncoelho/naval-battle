---
name: qa-full-test
description: Bateria de testes completa do Naval Battle no Android (app da Play Store, emuladores QA01 e QA02 com contas Google de teste). Use sempre que o usuário pedir "bateria de testes completa", "teste completo do app", "testar tudo" ou um teste de funcionalidades de ponta a ponta antes de release.
---

# Bateria de testes completa

O roteiro é a SPEC `docs/QA_TEST_PLAN.md` — ler inteiro antes de começar e seguir a ordem
de execução definida lá (primeiro tudo que exige dois aparelhos, depois desligar o QA02 e
seguir só no QA01; limpeza e relatório no fim).

Lembretes que mais fazem diferença:
- Ligar os emuladores com `-feature -Vulkan`; desligar com `adb emu kill`.
- O serial muda conforme a ordem de boot — mapear com `adb -s <serial> emu avd name`
  antes do primeiro toque.
- Nunca digitar senha. Login Google do aparelho é do usuário.
- Toque por coordenada vem de print atual (`adb exec-out screencap -p`), ou de
  `uiautomator dump` quando o elemento tiver texto.
- Falha P0/P1 confirmada segue o fluxo de release normal (`play-store-release`).
- Relatório final em pt-BR no formato da seção 15 da SPEC.
- Se o app ganhar tela ou fluxo novo, acrescentar o caso na SPEC no mesmo commit.
