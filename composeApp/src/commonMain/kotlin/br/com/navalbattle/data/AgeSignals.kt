package br.com.navalbattle.data

import br.com.navalbattle.game.AgeBand

/**
 * Faixa etária da loja (Play Age Signals API, #49; stack aprovada pelo John em 25/10). Só o
 * Android lê; a resposta vira [AgeBand] e a idade em si morre aqui: não vai para o [Cloud], para
 * a rede de anúncios, para o log nem para o Prefs. Só pode decidir o tipo de anúncio.
 *
 * Regra do John (#29): nada na abertura do app. O pedido de acesso pode abrir o aviso da Play,
 * então só roda no primeiro toque num botão de anúncio.
 */
expect class AgeSignals() {
    /** Falso no iPhone (Declared Age Range da Apple fica para a App Store, issue própria). */
    val supported: Boolean

    /** Pede acesso e lê a faixa. Nunca lança: sem Activity, erro ou 5 s de espera → [AgeBand.UNKNOWN]. */
    suspend fun read(): AgeBand
}
