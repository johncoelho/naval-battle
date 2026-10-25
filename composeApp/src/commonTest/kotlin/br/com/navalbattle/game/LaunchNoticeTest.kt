package br.com.navalbattle.game

import br.com.navalbattle.i18n.I18n
import br.com.navalbattle.i18n.K
import br.com.navalbattle.i18n.Lang
import br.com.navalbattle.i18n.t
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** Issue #36: regra do aviso de lançamento. */
class LaunchNoticeTest {
    private fun st(enabled: Boolean = true, variant: String = "new", seen: Boolean = false) =
        LaunchNoticeStatus(enabled, variant, seen)

    @Test
    fun desligadoEscondeTudo() {
        assertEquals(LaunchNoticeDecision.Hide, launchNoticeDecision(st(enabled = false), true, false))
    }

    @Test
    fun semStatusEscondeSemTravar() {
        assertEquals(LaunchNoticeDecision.Hide, launchNoticeDecision(null, true, false))
    }

    @Test
    fun ligadoENaoVistoMostraAVariante() {
        assertEquals(LaunchNoticeDecision.Show(LaunchVariant.NEW), launchNoticeDecision(st(variant = "new"), true, false))
        assertEquals(LaunchNoticeDecision.Show(LaunchVariant.TESTER), launchNoticeDecision(st(variant = "tester"), true, false))
    }

    @Test
    fun servidorVistoEscondeMesmoSemMarcaLocal() {
        assertEquals(LaunchNoticeDecision.Hide, launchNoticeDecision(st(seen = true), true, false))
    }

    @Test
    fun marcaLocalComServidorNaoVistoSoReenviaOAck() {
        assertEquals(LaunchNoticeDecision.AckOnly, launchNoticeDecision(st(), true, true))
    }

    @Test
    fun convidadoComMarcaLocalEscondeEConvidadoSemMarcaVeBoasVindas() {
        assertEquals(LaunchNoticeDecision.Hide, launchNoticeDecision(st(), false, true))
        assertEquals(LaunchNoticeDecision.Show(LaunchVariant.NEW), launchNoticeDecision(st(variant = "tester"), false, false))
    }

    @Test
    fun varianteDesconhecidaViraNew() {
        assertEquals(LaunchNoticeDecision.Show(LaunchVariant.NEW), launchNoticeDecision(st(variant = "xyz"), true, false))
    }

    @Test
    fun marcaLocalPorContaOuConvidado() {
        assertEquals("launch_notice_seen_guest", launchNoticeKey(null))
        assertEquals("launch_notice_seen_abc", launchNoticeKey("abc"))
    }

    @Test
    fun textosNosTresIdiomas() {
        val original = I18n.lang
        try {
            Lang.entries.forEach { lang ->
                I18n.lang = lang
                listOf(K.LAUNCH_EYEBROW, K.LAUNCH_TITLE, K.LAUNCH_BODY_TESTER, K.LAUNCH_BODY_NEW, K.LAUNCH_OK)
                    .forEach { assertTrue(t(it).isNotBlank(), "$lang $it vazio") }
            }
        } finally {
            I18n.lang = original
        }
    }
}
