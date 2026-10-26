package br.com.navalbattle.game

import br.com.navalbattle.i18n.K
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** Provocações (#58): código desconhecido não vira texto na tela (antes o `code` aparecia cru). */
class TauntTest {

    @Test
    fun knownPhrasesMapToText() {
        assertEquals(K.TAUNT_FIRE, tauntPhraseOf("FIRE"))
        assertEquals(K.TAUNT_GG, tauntPhraseOf("GG"))
        assertEquals(K.TAUNT_INCOMING, tauntPhraseOf("INCOMING"))
    }

    @Test
    fun unknownCodeIsNotText() {
        assertNull(tauntPhraseOf("texto livre de um cliente alterado"))
        assertNull(tauntPhraseOf(""))
        assertNull(tauntPhraseOf("fire")) // código é exato
    }

    @Test
    fun onlyEmojisAndPhrasesAreKnown() {
        TAUNT_EMOJIS.forEach { assertTrue(isKnownTaunt(it)) }
        TAUNT_PHRASES.forEach { assertTrue(isKnownTaunt(it.first)) }
        assertFalse(isKnownTaunt("Voce e um lixo"))
        assertFalse(isKnownTaunt("🍆"))
        assertFalse(isKnownTaunt(""))
    }
}
