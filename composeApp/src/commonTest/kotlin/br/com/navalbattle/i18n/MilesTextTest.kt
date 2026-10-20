package br.com.navalbattle.i18n

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Issue #33: o popup de milhas mostrava "COMPRAR %1 MILHAS" (a função de uma substituição só trocava
 * %s) e "Você tem 1 milhas" (sem singular). Estes testes seguram a formatação nos três idiomas.
 */
class MilesTextTest {

    /** Roda o bloco em cada idioma e volta ao português no fim, mesmo se falhar. */
    private fun emCadaIdioma(bloco: (Lang) -> Unit) {
        val original = I18n.lang
        try {
            Lang.entries.forEach { lang ->
                I18n.lang = lang
                bloco(lang)
            }
        } finally {
            I18n.lang = original
        }
    }

    @Test
    fun compraMostraONumeroDoPacoteNosTresIdiomas() {
        emCadaIdioma { lang ->
            val texto = t(K.MILES_BUY, 5)
            assertFalse(texto.contains("%"), "$lang ainda tem placeholder: $texto")
            assertTrue(texto.split(" ").contains("5"), "$lang sem o 5: $texto")
        }
        I18n.lang = Lang.PT
        assertEquals("Comprar 5 milhas", t(K.MILES_BUY, 5))
        I18n.lang = Lang.EN
        assertEquals("Buy 5 miles", t(K.MILES_BUY, 5))
        I18n.lang = Lang.ES
        assertEquals("Comprar 5 millas", t(K.MILES_BUY, 5))
    }

    @Test
    fun saldoDeUmaMilhaUsaSingularNosTresIdiomas() {
        emCadaIdioma { lang ->
            val texto = tMilesBalance(1)
            assertFalse(texto.contains("%"), "$lang ainda tem placeholder: $texto")
            assertFalse(texto.contains("milhas") || texto.contains("miles") || texto.contains("millas"),
                "$lang com plural para 1: $texto")
        }
        I18n.lang = Lang.PT
        assertEquals("Você tem 1 milha", tMilesBalance(1))
        I18n.lang = Lang.EN
        assertEquals("You have 1 mile", tMilesBalance(1))
        I18n.lang = Lang.ES
        assertEquals("Tienes 1 milla", tMilesBalance(1))
    }

    @Test
    fun saldoDeZeroOuVariosUsaPlural() {
        emCadaIdioma { lang ->
            assertEquals(t(K.MILES_BALANCE, 0), tMilesBalance(0), "$lang: zero deve ser plural")
            assertEquals(t(K.MILES_BALANCE, 2), tMilesBalance(2), "$lang: dois deve ser plural")
        }
        I18n.lang = Lang.PT
        assertEquals("Você tem 0 milhas", tMilesBalance(0))
        assertEquals("Você tem 12 milhas", tMilesBalance(12))
    }
}
