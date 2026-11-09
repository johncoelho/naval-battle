package br.com.navalbattle.game

import br.com.navalbattle.i18n.I18n
import br.com.navalbattle.i18n.K
import br.com.navalbattle.i18n.Lang
import br.com.navalbattle.i18n.t
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** #85: contagem da vitória por ausência no posicionamento online. */
class WalkoverRulesTest {

    private fun left(since: Int, away: Int? = null, line: Boolean = false, offline: Boolean = false, placement: Boolean = true) =
        WalkoverRules.secondsLeft(placement, since, away, line, offline)

    @Test
    fun contagemDescePartindoDe60() {
        assertEquals(60, left(0))
        assertEquals(45, left(15))
        assertEquals(0, left(60))
        assertEquals(0, left(90))
    }

    @Test
    fun semPresencaNenhumaConta() {
        // nulo = o adversário nunca bateu o ponto: é o caso do "aceitou e fechou o app"
        assertEquals(30, left(30, away = null))
    }

    @Test
    fun presencaAntigaNaoEhSinal() {
        // a última batida dele é anterior à entrada na sala (sala fantasma): continua sem sinal
        assertEquals(30, left(30, away = 45))
        assertFalse(WalkoverRules.hasSignal(30, 30, false))
    }

    @Test
    fun reconexaoCancelaAContagem() {
        // ele bateu o ponto depois da entrada, ou mandou qualquer linha: sem contagem
        assertNull(left(40, away = 3))
        assertNull(left(40, away = null, line = true))
    }

    @Test
    fun semConexaoNaoCulpaOOutro() {
        assertNull(left(40, offline = true))
    }

    @Test
    fun nuncaForaDoPosicionamento() {
        assertNull(left(40, placement = false))
        assertNull(left(300, placement = false))
    }

    @Test
    fun faixaSoDepoisDe15Segundos() {
        assertFalse(WalkoverRules.bannerVisible(null))
        assertFalse(WalkoverRules.bannerVisible(left(14)))
        assertTrue(WalkoverRules.bannerVisible(left(15)))
        assertTrue(WalkoverRules.bannerVisible(left(59)))
        assertTrue(WalkoverRules.bannerVisible(0))
    }

    @Test
    fun pedeAVitoriaSoQuandoZerouERespeitaOIntervalo() {
        assertFalse(WalkoverRules.shouldClaim(5, 100_000L, null))
        assertFalse(WalkoverRules.shouldClaim(null, 100_000L, null))
        assertTrue(WalkoverRules.shouldClaim(0, 100_000L, null))
        assertFalse(WalkoverRules.shouldClaim(0, 100_000L, 97_000L))
        assertTrue(WalkoverRules.shouldClaim(0, 100_000L, 95_000L))
    }

    @Test
    fun textosNasTresLinguasSemKO() {
        val chaves = listOf(K.WO_WAITING, K.WO_WAITING_SUB, K.WO_WON, K.WO_LOST, K.WO_LOST_SUB, K.WO_REFUND, K.RESULT_RANKING_WO_LIMIT)
        val original = I18n.lang
        try {
            chaves.forEach { k ->
                listOf(Lang.PT to k.pt, Lang.EN to k.en, Lang.ES to k.es).forEach { (lang, texto) ->
                    assertTrue(texto.isNotBlank(), "${k.name} sem texto em $lang")
                    assertFalse(Regex("""\bKO\b""").containsMatchIn(texto), "${k.name}/$lang usa KO")
                }
            }
            I18n.lang = Lang.PT
            assertTrue(t(K.WO_WON).contains("ausência"))
            assertTrue(t(K.WO_LOST).contains("não entrou a tempo"))
            assertNotNull(t(K.WO_WAITING_SUB, 45))
            assertTrue(t(K.WO_WAITING_SUB, 45).contains("45"))
        } finally {
            I18n.lang = original
        }
    }
}
