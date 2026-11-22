package br.com.navalbattle.i18n

/**
 * Android: os textos que citam Google Play, beta ou teste fechado (#118). O iPhone devolve vazio
 * (`AndroidOnlyText.ios.kt`), então esses termos nem entram no binário da Apple (guideline 2.3.10).
 * Texto e idiomas idênticos aos que viviam em [K] antes da 0.47.1.
 */
private class Txt(val pt: String, val en: String, val es: String)

private val textos: Map<AndroidOnlyKey, Txt> = mapOf(
    AndroidOnlyKey.LAUNCH_TITLE to Txt(
        "Naval Battle Classic chegou à Google Play!",
        "Naval Battle Classic is now on Google Play!",
        "¡Naval Battle Classic llegó a Google Play!"
    ),
    AndroidOnlyKey.LAUNCH_BODY_TESTER to Txt(
        "Obrigado a você que testou o jogo no beta: cada partida, bug relatado e ideia ajudou a chegar aqui. " +
        "Todos os dobrões e brindes que você ganhou continuam na sua conta. " +
        "A partir de agora, pacotes, passe e itens da loja passam a ser pagos para todos.",
        "Thank you for testing the game in the beta: every match, reported bug and idea helped get here. " +
        "All the doubloons and gifts you earned stay in your account. " +
        "From now on, packs, pass and store items are paid for everyone.",
        "Gracias por probar el juego en la beta: cada partida, error reportado e idea ayudó a llegar hasta aquí. " +
        "Todos los doblones y regalos que ganaste siguen en tu cuenta. " +
        "A partir de ahora, paquetes, pase y artículos de la tienda son de pago para todos."
    ),
    AndroidOnlyKey.LAUNCH_BODY_NEW to Txt(
        "Bem-vindo a bordo! Jogue contra a IA, com amigos no mesmo aparelho, na rede local ou online. Bons combates, comandante.",
        "Welcome aboard! Play against the AI, with friends on the same device, on the local network or online. Good hunting, commander.",
        "¡Bienvenido a bordo! Juega contra la IA, con amigos en el mismo dispositivo, en la red local u online. Buenos combates, comandante."
    ),
    AndroidOnlyKey.DOUBLOON_BETA_CHARGE to Txt("Pacotes pagos podem cobrar de verdade no seu Google Play. Só compre se quiser testar a cobrança.", "Paid packs may charge your real Google Play account. Only buy if you want to test the charge.", "Los paquetes pagados pueden cobrar de verdad en tu Google Play. Compra solo si quieres probar el cobro."),
    AndroidOnlyKey.PREMIO_TITLE to Txt("Prêmio Beta", "Beta prize", "Premio Beta"),
    AndroidOnlyKey.PREMIO_BODY to Txt("Um pacote de dobrões por dia para quem testa o jogo. Pegue uma vez por dia; o prêmio volta à meia-noite de Brasília.", "One doubloon pack a day for those testing the game. Claim it once a day; it resets at midnight in Brasilia.", "Un paquete de doblones al día para quienes prueban el juego. Reclámalo una vez al día; se reinicia a medianoche en Brasilia."),
    AndroidOnlyKey.PREMIO_TAKE to Txt("Pegar o Prêmio", "Claim the prize", "Reclamar el premio"),
    AndroidOnlyKey.PREMIO_TAKEN to Txt("Você já pegou o Prêmio de hoje. Volta em %s.", "You already claimed today's prize. It comes back in %s.", "Ya reclamaste el premio de hoy. Vuelve en %s."),
    AndroidOnlyKey.PREMIO_DONE_NOTICE to Txt("Você já pegou o Prêmio de hoje. Ele volta amanhã.", "You already claimed today's prize. It comes back tomorrow.", "Ya reclamaste el premio de hoy. Vuelve mañana."),
    AndroidOnlyKey.PREMIO_UNAVAILABLE to Txt("Prêmio indisponível agora.", "Prize unavailable right now.", "Premio no disponible ahora."),
    AndroidOnlyKey.BADGE_BETA_TESTER to Txt("Beta Tester", "Beta Tester", "Beta Tester"),
    AndroidOnlyKey.BADGE_BETA_TESTER_DESC to Txt(
        "Jogou durante os testes fechados",
        "Played during the closed testing",
        "Jugó durante las pruebas cerradas"
    )
)

actual fun androidOnlyText(key: AndroidOnlyKey): String {
    val txt = textos.getValue(key)
    return when (I18n.lang) {
        Lang.PT -> txt.pt
        Lang.EN -> txt.en
        Lang.ES -> txt.es
    }
}
