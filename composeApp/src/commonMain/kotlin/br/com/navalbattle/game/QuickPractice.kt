package br.com.navalbattle.game

/**
 * Treino contra a IA enquanto a partida rapida procura adversario (#41). Regras puras, sem tela
 * nem rede: quando oferecer o treino, o que fazer quando alguem entra e se o treino conta.
 */
object QuickPractice {
    /** O que fazer quando o adversario da sala rapida entra. */
    enum class JoinAction { AUTO_SWITCH, START }

    /** Espera antes de oferecer o treino (ms). */
    private const val OFFER_AFTER_MILLIS = 20_000L

    /** Tempo do aviso "Adversario encontrado" antes de trocar para a online (ms). */
    private const val SWITCH_MILLIS = 3_000L

    /** Aviso de adversario encontrado, em segundos, para o contador do popup. */
    val switchSeconds: Int get() = (SWITCH_MILLIS / 1000L).toInt()

    /** Pausa do aviso antes de abrir o posicionamento online. */
    val switchDelayMillis: Long get() = SWITCH_MILLIS

    /** A oferta aparece so na partida rapida e depois de 20 s procurando; sala de amigo e codigo ficam como estao. */
    fun practiceOfferVisible(searchStartedAt: Long, now: Long, isQuick: Boolean): Boolean =
        isQuick && searchStartedAt > 0L && now - searchStartedAt >= OFFER_AFTER_MILLIS

    /** Com treino ativo a troca e automatica (o convidado ja entrou); sem treino, segue o fluxo normal. */
    fun onOpponentJoined(practicing: Boolean): JoinAction =
        if (practicing) JoinAction.AUTO_SWITCH else JoinAction.START

    /** O treino trocado pela online e descartado (sem XP, estatistica ou desafio); terminado conta. */
    fun practiceCounts(switched: Boolean): Boolean = !switched
}
