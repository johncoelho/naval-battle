package br.com.navalbattle.game

/**
 * Vitória por ausência no posicionamento online (#85): o adversário aceitou a sala e nunca entrou.
 * Regra pura, sem tela nem rede. A decisão final é do servidor (`resolve_walkover`, 60 s sem sinal
 * desde a entrada na sala); aqui só a contagem que a faixa mostra e o momento de pedir a vitória.
 * Fica só no posicionamento: durante a batalha valem a pausa e a queda de conexão.
 */
object WalkoverRules {
    /** Mesmo valor de `walkover_seconds` no servidor. */
    const val TIMEOUT_SECONDS = 60

    /** A faixa só aparece depois de 15 s sem sinal; antes disso é só abrir o app devagar. */
    const val BANNER_AFTER_SECONDS = 15

    /** Intervalo mínimo entre dois pedidos ao servidor quando ele ainda diz que não. */
    const val RETRY_SECONDS = 5

    /**
     * O adversário deu sinal depois da entrada: uma linha dele na sala, ou a presença dele mais
     * recente que a entrada ([opponentAwaySeconds] menor que [sinceJoinSeconds]). Sem presença nenhuma
     * (nulo) e sem linha, não houve sinal.
     */
    fun hasSignal(sinceJoinSeconds: Int, opponentAwaySeconds: Int?, opponentLineSeen: Boolean): Boolean =
        opponentLineSeen || (opponentAwaySeconds != null && opponentAwaySeconds < sinceJoinSeconds)

    /**
     * Segundos até a vitória por ausência, ou nulo quando não há contagem: fora do posicionamento,
     * com este aparelho sem conexão (não dá para culpar o outro lado) ou com o adversário aparecendo.
     */
    fun secondsLeft(
        placement: Boolean,
        sinceJoinSeconds: Int,
        opponentAwaySeconds: Int?,
        opponentLineSeen: Boolean,
        selfOffline: Boolean
    ): Int? {
        if (!placement || selfOffline) return null
        if (hasSignal(sinceJoinSeconds, opponentAwaySeconds, opponentLineSeen)) return null
        return (TIMEOUT_SECONDS - sinceJoinSeconds).coerceIn(0, TIMEOUT_SECONDS)
    }

    /** A faixa de aviso aparece a partir de [BANNER_AFTER_SECONDS] sem sinal. */
    fun bannerVisible(secondsLeft: Int?): Boolean =
        secondsLeft != null && TIMEOUT_SECONDS - secondsLeft >= BANNER_AFTER_SECONDS

    /** Pede a vitória ao servidor quando a contagem zerou e já passou o intervalo da última tentativa. */
    fun shouldClaim(secondsLeft: Int?, nowMillis: Long, lastAttemptMillis: Long?): Boolean =
        secondsLeft == 0 && (lastAttemptMillis == null || nowMillis - lastAttemptMillis >= RETRY_SECONDS * 1000L)
}
