package br.com.navalbattle.data

import br.com.navalbattle.game.AgeBand
import br.com.navalbattle.game.AgeReading

/** iPhone: sem anúncio e sem leitura de idade por enquanto (Declared Age Range na App Store). */
actual class AgeSignals actual constructor() {
    actual val supported: Boolean get() = false

    actual suspend fun read(): AgeReading = AgeReading(AgeBand.UNKNOWN, answered = true)
}
