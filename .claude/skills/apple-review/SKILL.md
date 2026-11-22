---
name: apple-review
description: Checklist do Release para enviar o Naval Battle à revisão da Apple (App Review) sem idas e vindas. Ensaio de revisão e varredura do binário verdes no SHA enviado, Paid Apps Agreement, os 4 produtos anexados, conta demo, notas de revisão escritas só com o que o ensaio provou e resposta no Resolution Center. Use antes de todo envio à Apple (TestFlight para revisão ou "Submit for Review") e quando a Apple recusar uma versão.
---

# Revisão da Apple: o que fazer antes de enviar

Criada na #118 (3 recusas seguidas: 2.2 beta, 2.1(b) IAP não encontrado, depois 2.1(b) botão "Comprar Passe" sem resposta e 2.3.10 binário citando Google Play).
**Causa raiz:** corrigíamos só o que a Apple citava e nunca rodávamos o que o revisor roda (o app no iPad, com a conta demo,
tocando em cada botão). Agora o ensaio roda antes, e **nada vai à Apple sem ele verde**. Quem executa é o **Release**; o PM confere.

## Checklist (nesta ordem, cada item com prova)

1. **Ensaio de revisão verde no SHA enviado.** `gh workflow run ios-qa.yml --ref main -f modo=ensaio-revisao`; espere o fim
   (`gh run watch <id> --exit-status`) e confira no artefato `ios-qa-<run>`: `ensaio-revisao.md` com `RESULTADO: VERDE`. Link do run no cartão.
   Ensaio vermelho = **não enviar**: o relatório diz qual toque ficou mudo ou qual texto casou a lista; volta para o Dev como bug.
2. **Varredura do binário** do `ios.yml` e do `ios-release.yml` sem achado novo (resumo do job): nenhum termo de
   `tools/ios-forbidden-terms.txt` fora de `tools/ios-binary-allowlist.txt`. A allowlist só o Tech Lead altera.
3. **Paid Apps Agreement em vigor** (App Store Connect > Business > Agreements, conta do John). O Release confere; se não estiver
   Active, pede ao John (banco e impostos são dele) e **não envia**.
4. **Os 4 produtos** (`dobroes_bolsa`, `dobroes_bau`, `dobroes_cofre`, `dobroes_tesouro`) **Ready to Submit e anexados à versão**
   (seção "In-App Purchases and Subscriptions" da página da versão), com as **capturas de revisão** (`modo=capturas-iap`) anexadas.
5. **Conta demo** (App Review Information) com **saldo de dobrões para o Passe**, **não `beta_eligible`** e sem medalha beta. O Release
   confere por SQL de **leitura** (`supabase db query`); se faltar saldo, crédito de teste pela skill `supabase-change` (dado, não arquivo; OK do PM).
   Atenção: o Passe é comprado uma vez por temporada; se a demo já tiver o Passe, o ensaio registra "CTA não exercitado" e a nota não pode afirmar o passo.
6. **Notas de revisão geradas do que o ensaio provou** (abaixo). Frase sem PNG ou linha do relatório não entra.
7. **Resposta no Resolution Center** cita os dois itens da recusa e o que mudou (versão e build), em inglês, curta.
8. **Depois do envio:** registrar no HISTORY o que o ensaio **não** pegou (se a Apple recusar de novo, é lacuna do ensaio: vira item do teste antes do próximo envio).

## Notas de revisão: só o que o ensaio provou

Cada frase liga a um PNG ou a uma linha de `ensaio-revisao.md`. Proibido afirmar o que não foi testado (nem "a folha da Apple abre" se o
ensaio só viu a mensagem da vitrine: a StoreKit Configuration não é aplicada pelo `xcodebuild`). Modelo, a ajustar ao relatório:

> **Doubloons (in-game currency)** are bought with In-App Purchase in Menu > Store > Doubloons > tap a pack (4 products). The Apple purchase sheet opens. [só se o ensaio viu a folha]
>
> **Season Pass is not a subscription**: it does not renew and no real money is charged for it. It is bought with Doubloons (the in-game currency) in Menu > Season > "Join with N doubloons". With too few Doubloons the app opens "Not enough doubloons" with a shortcut to the Doubloon store. [cada frase com seu PNG]
>
> The demo account already has Doubloons, so the Season Pass can be joined directly.

Evite a palavra "subscription" fora de "não é assinatura" (a 0.45.3 já nos custou por ela); no app o Passe nunca é chamado de assinatura.

## O que o ensaio cobre e o que não cobre

Cobre: 4 pacotes, Passe (sem saldo, com saldo, atalho para a loja), milhas, botões de preço da Loja, varredura de texto das telas, resposta em até 10 s.
Não cobre: o iPadOS do revisor (pode diferir do runtime do runner), a folha de login do Apple ID (nunca fotografada nem registrada, nunca se digita senha),
a decisão humana do revisor. É portão **necessário, não suficiente**: a Apple ainda pode citar um item novo; aí o ensaio ganha esse caso.

## Portão no `ios-release.yml`

Variável de repositório `IOS_REVIEW_GATE`: `off`, `warn` (padrão, só avisa) ou `on` (reprova o envio sem ensaio verde no mesmo SHA).
**Ligar `on` depende do OK do John**, depois do primeiro ensaio verde; com `on` o envio automático (`IOS_APPSTORE_LIVE`) nunca passa, porque o ensaio ainda
não existe para o SHA recém-publicado: dispare o ensaio e depois o `ios-release.yml` com `enviar=true`.
Varredura do binário: `IOS_SCAN_ENFORCE=true` liga o reprovar, depois de o Tech Lead fechar a allowlist.
