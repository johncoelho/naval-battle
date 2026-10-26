package br.com.navalbattle.game

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Filtro de nome de guerra (#58). As listas de nomes abaixo são as MESMAS do ensaio SQL
 * (`supabase/tests/safety_ensaio.sql`): se uma lado mudar, o outro muda junto.
 */
class NameFilterTest {

    private val offensive = listOf(
        "Caralho", "CARÁLHO", "c4r4lh0", "c.a.r.a.l.h.o", "caaaralhooo", "Fuuuck", "f-u-c-k-e-r",
        "Sh1ttyPlayer", "FilhodaPuta", "M3rd4", "p0rr4", "Puta", "xX_Hitler_Xx", "NaZiSta", "m1erd@", "Bitch", "N1gg3r", "c u z a o"
    )

    private val fine = listOf(
        "Cuca", "Escudo", "Computador", "Enviado", "Nigeria", "Therapist", "Capitao Silva", "Scunthorpe",
        "Assassino", "Cassino", "Pirata", "Almirante", "Notario", "Evergarden", "Skipper"
    )

    @Test
    fun variationsAreOffensive() {
        offensive.forEach { assertTrue(NameFilter.isOffensive(it), "deveria reprovar: $it") }
    }

    @Test
    fun legitimateNamesPass() {
        fine.forEach { assertFalse(NameFilter.isOffensive(it), "deveria passar: $it") }
    }

    @Test
    fun accentLeetSeparatorAndRepetitionNormalize() {
        assertEquals("caralho", NameFilter.normalize("CARÁLHO"))
        assertEquals("caralho", NameFilter.normalize("c4r4lh0"))
        assertEquals("caralho", NameFilter.normalize("c.a.r.a.l.h.o"))
        assertEquals("caralho", NameFilter.normalize("caaaralhooo"))
        assertEquals(listOf("f", "u", "c", "k"), NameFilter.tokens("f u c k"))
    }

    @Test
    fun shortTermOnlyAsWholeWord() {
        // "puta" tem 4 letras: reprova o nome igual a ele, mas não quem só o contém por acaso
        assertTrue(NameFilter.isOffensive("Puta"))
        assertTrue(NameFilter.isOffensive("a puta b"))
        assertFalse(NameFilter.isOffensive("Computador"))
        assertFalse(NameFilter.isOffensive("Disputas"))
        assertFalse(NameFilter.isOffensive("Cuca"))
        assertFalse(NameFilter.isOffensive("Escudo"))
    }

    @Test
    fun allowListRemovesExceptionsBeforeSearching() {
        assertFalse(NameFilter.isOffensive("Enviado Nigeria"))
        assertTrue(NameFilter.isOffensive("Enviado Caralho"))
    }

    @Test
    fun oneSampleByLanguage() {
        assertTrue(NameFilter.isOffensive("Merda")) // pt
        assertTrue(NameFilter.isOffensive("Asshole")) // en
        assertTrue(NameFilter.isOffensive("Mierda")) // es
    }

    @Test
    fun lengthLimitsAreThreeToEighteen() {
        assertEquals(NameFilter.Check.INVALID, NameFilter.validate("ab"))
        assertEquals(NameFilter.Check.INVALID, NameFilter.validate("  ab  "))
        assertEquals(NameFilter.Check.OK, NameFilter.validate("abc"))
        assertEquals(NameFilter.Check.OK, NameFilter.validate("a".repeat(18)))
        assertEquals(NameFilter.Check.INVALID, NameFilter.validate("a".repeat(19)))
        assertEquals(NameFilter.Check.OFFENSIVE, NameFilter.validate("Caralho"))
        assertEquals(NameFilter.Check.OK, NameFilter.validate("  Almirante  "))
    }

    @Test
    fun safeDisplayFallsBack() {
        assertEquals("Comandante", NameFilter.safeDisplay("Caralho", "Comandante"))
        assertEquals("Comandante", NameFilter.safeDisplay("   ", "Comandante"))
        assertEquals("Almirante", NameFilter.safeDisplay("Almirante", "Comandante"))
        assertEquals("Comandante", displayOther("f.u.c.k.e.r", "Comandante"))
        assertEquals("Capitao", displayOther("Capitao", "Comandante"))
    }

    @Test
    fun everyTermIsCaughtAndNoneIsAllowed() {
        // cada termo da lista reprova a si mesmo, e nenhuma exceção reprova
        NameFilter.TERMS.forEach { (term, _) -> assertTrue(NameFilter.isOffensive(term), "termo: $term") }
        NameFilter.ALLOW.forEach { assertFalse(NameFilter.isOffensive(it), "exceção: $it") }
    }
}
