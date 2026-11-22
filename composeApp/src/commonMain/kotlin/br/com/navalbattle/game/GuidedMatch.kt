package br.com.navalbattle.game

/** Passos da primeira partida guiada (#43). [DONE] = sem mais dicas (terminou ou pulou). */
enum class GuidedStep { PLACE, FIRE, HIT, SUNK, DONE }

/** O que acontece na partida e muda o passo. Nenhum evento revela o estado de casa alguma. */
enum class GuidedEvent { PLACED, MISSED, HIT_SHIP, SUNK_SHIP, FINISHED, SKIPPED }

/** Próximo passo da guiada. Pular termina em qualquer passo; tiro errado não anda. */
fun nextGuidedStep(current: GuidedStep, event: GuidedEvent): GuidedStep = when {
    current == GuidedStep.DONE -> GuidedStep.DONE
    event == GuidedEvent.SKIPPED || event == GuidedEvent.FINISHED -> GuidedStep.DONE
    current == GuidedStep.PLACE && event == GuidedEvent.PLACED -> GuidedStep.FIRE
    current == GuidedStep.FIRE && event == GuidedEvent.HIT_SHIP -> GuidedStep.HIT
    (current == GuidedStep.FIRE || current == GuidedStep.HIT) && event == GuidedEvent.SUNK_SHIP -> GuidedStep.SUNK
    else -> current
}

/**
 * A "Primeira missão" só aparece no menu, sem partida e SEM outro popup do Passe na tela (#118). Antes ela era desenhada por cima do
 * Passe de Temporada (ordem de composição) e o botão "Entrar com N dobrões" ficava atrás de um popup que o revisor da Apple não esperava;
 * agora o Passe tem a vez, e a missão volta quando ele fecha ("Agora não" ou OK).
 */
fun guidedOfferVisible(
    offer: Boolean, onMenu: Boolean, hasMatch: Boolean,
    seasonPopup: Boolean, seasonEnd: Boolean, rankedLocked: Boolean, seasonResult: Boolean
): Boolean = offer && onMenu && !hasMatch && !seasonPopup && !seasonEnd && !rankedLocked && !seasonResult

/** Oferece a guiada só a quem nunca jogou (0 partidas) e ainda não a fez nem pulou. */
fun shouldOfferGuided(matchesPlayed: Int, guidedDone: Boolean): Boolean = matchesPlayed <= 0 && !guidedDone

/** Resposta de `claim_welcome_gift()` já traduzida para o app (o aparelho nunca credita). */
data class WelcomeGift(val status: String, val doubloons: Int, val miles: Int, val balance: Int)

/** Resultado de pedir o presente, para a interface decidir o que mostrar e o que guardar. */
enum class WelcomeOutcome { PAID, DONE_BEFORE, NOT_ELIGIBLE, NO_WALLET, OFFLINE }

fun welcomeOutcome(gift: WelcomeGift?): WelcomeOutcome = when (gift?.status) {
    null -> WelcomeOutcome.OFFLINE
    "ok" -> WelcomeOutcome.PAID
    "already" -> WelcomeOutcome.DONE_BEFORE
    "not_eligible" -> WelcomeOutcome.NOT_ELIGIBLE
    "no_wallet" -> WelcomeOutcome.NO_WALLET
    else -> WelcomeOutcome.OFFLINE
}

/** Só PAID mostra o popup com valor; "já recebeu" e "não elegível" encerram em silêncio; o resto tenta de novo. */
fun welcomeShouldRetry(outcome: WelcomeOutcome): Boolean =
    outcome == WelcomeOutcome.OFFLINE || outcome == WelcomeOutcome.NO_WALLET

/**
 * Espera antes da nova tentativa do presente com o app aberto (#72): 3s, 10s e 30s; depois disso só na
 * reabertura. [attempt] conta de 0; nulo = acabaram as tentativas.
 */
fun welcomeRetryDelayMs(attempt: Int): Long? = when (attempt) {
    0 -> 3_000L
    1 -> 10_000L
    2 -> 30_000L
    else -> null
}

/** Valores do presente só para MOSTRAR antes do resgate; quem decide e paga é o servidor (`welcome.sql`). */
internal const val WELCOME_SHOWN_DOUBLOONS = 150
internal const val WELCOME_SHOWN_MILES = 5
