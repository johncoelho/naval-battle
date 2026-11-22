package br.com.navalbattle.game

private const val FIRST_ASK_WINS = 3
private const val FIRST_ASK_STREAK = 2
private const val MIN_CAREER_MATCHES = 2
private const val ASK_GAP_DAYS = 30
private const val LIFETIME_ASKS = 3
private const val MILLIS_PER_DAY = 86_400_000L

/**
 * Quando pedir a avaliação da loja (Play In-App Review no Android, `SKStoreReviewController` no iPhone, #45 e #136).
 * Puro: o App só lê as marcas locais e chama [shouldAsk] ao voltar ao menu.
 *
 * - Só depois de vitória, e nunca na 1ª partida (precisa de [MIN_CAREER_MATCHES] partidas concluídas: a 1ª é do
 *   gancho do fim da 1ª partida, #135). Derrota nunca.
 * - A 1ª vez vale com 2 vitórias seguidas OU 3 vitórias na carreira (o que vier primeiro) ou ao subir de patente;
 *   as seguintes só ao subir de patente.
 * - No máximo 1 pedido a cada 30 dias e 3 na vida da instalação (a Apple limita a 3 por ano e a Google aplica a cota dela).
 * - Nunca em aparelho de teste automático.
 * - Sem pergunta prévia "está gostando?": as duas lojas proíbem filtrar quem avalia.
 */
object ReviewPrompt {
    fun shouldAsk(
        totalWins: Int,
        rankedUp: Boolean,
        wonLast: Boolean,
        lastAskMillis: Long,
        askCount: Int,
        now: Long,
        isTest: Boolean,
        winStreak: Int = 0,
        careerMatches: Int = Int.MAX_VALUE
    ): Boolean {
        if (isTest || !wonLast) return false
        if (careerMatches < MIN_CAREER_MATCHES) return false
        if (askCount >= LIFETIME_ASKS) return false
        if (askCount > 0 && now - lastAskMillis < ASK_GAP_DAYS * MILLIS_PER_DAY) return false
        // a 1ª vez vale na 2ª vitória seguida, na 3ª vitória da carreira ou depois dela (se o momento passou com
        // popup na tela, a vitória seguinte tenta de novo); os pedidos seguintes só ao subir de patente
        return rankedUp || (askCount == 0 && (winStreak >= FIRST_ASK_STREAK || totalWins >= FIRST_ASK_WINS))
    }
}
