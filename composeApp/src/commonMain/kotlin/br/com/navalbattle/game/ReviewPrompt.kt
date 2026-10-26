package br.com.navalbattle.game

private const val FIRST_ASK_WINS = 3
private const val ASK_GAP_DAYS = 30
private const val LIFETIME_ASKS = 3
private const val MILLIS_PER_DAY = 86_400_000L

/**
 * Quando pedir a avaliação na Play (In-App Review, #45). Puro: o App só lê as marcas locais e
 * chama [shouldAsk] ao voltar ao menu.
 *
 * - Só depois de vitória: a 1ª vez com 3 vitórias na carreira ou ao subir de patente; derrota nunca.
 * - No máximo 1 pedido a cada 30 dias e 3 na vida da instalação (a Google ainda aplica a cota dela).
 * - Nunca em aparelho de teste automático.
 * - Sem pergunta prévia "está gostando?": a política da Google proíbe filtrar quem avalia.
 */
object ReviewPrompt {
    fun shouldAsk(
        totalWins: Int,
        rankedUp: Boolean,
        wonLast: Boolean,
        lastAskMillis: Long,
        askCount: Int,
        now: Long,
        isTest: Boolean
    ): Boolean {
        if (isTest || !wonLast) return false
        if (askCount >= LIFETIME_ASKS) return false
        if (askCount > 0 && now - lastAskMillis < ASK_GAP_DAYS * MILLIS_PER_DAY) return false
        // a 1ª vez vale na 3ª vitória ou depois dela (se o momento passou com popup na tela,
        // a vitória seguinte tenta de novo); os pedidos seguintes só ao subir de patente
        return rankedUp || (askCount == 0 && totalWins >= FIRST_ASK_WINS)
    }
}
