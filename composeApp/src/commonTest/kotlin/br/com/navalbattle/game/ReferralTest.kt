package br.com.navalbattle.game

import br.com.navalbattle.i18n.K
import br.com.navalbattle.i18n.Lang
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** Convide um amigo (#44): forma do código, texto do compartilhamento e leitura do que o servidor devolve. */
class ReferralTest {

    @Test
    fun normalizaMaiusculaEspacoEHifen() {
        assertEquals("ABC234", normalizeReferralCode(" abc-234 "))
        assertEquals("ABC234", normalizeReferralCode("a b c 2 3 4"))
    }

    @Test
    fun formaInvalidaNaoVaiAARede() {
        assertTrue(isValidReferralCodeShape("abc 234"))
        assertFalse(isValidReferralCodeShape("ABC23"))      // curto
        assertFalse(isValidReferralCodeShape("ABC2345"))    // longo
        assertFalse(isValidReferralCodeShape("ABC0O1"))     // 0, O e 1 não existem no alfabeto
        assertFalse(isValidReferralCodeShape("ABCIII"))     // I também não
        assertFalse(isValidReferralCodeShape(""))
    }

    @Test
    fun textoDoCompartilhamentoTemCodigoELinkNasTresLinguas() {
        for (lang in Lang.entries) {
            val text = referralShareText("K7MQ2X", lang)
            assertTrue("K7MQ2X" in text, "código em ${lang.code}")
            assertTrue("play.google.com/store/apps/details?id=aigamesfactory.navalbattleclassic" in text, "link em ${lang.code}")
            assertFalse("%1" in text || "%2" in text, "marcador sobrando em ${lang.code}")
        }
    }

    @Test
    fun statusDoServidorViraDesfecho() {
        assertEquals(ReferralOutcome.OK, referralOutcome(ReferralRedeem("ok", "Ana")))
        assertEquals(ReferralOutcome.INVALID, referralOutcome(ReferralRedeem("invalid", "")))
        assertEquals(ReferralOutcome.SELF, referralOutcome(ReferralRedeem("self", "")))
        assertEquals(ReferralOutcome.ALREADY, referralOutcome(ReferralRedeem("already", "")))
        assertEquals(ReferralOutcome.TOO_OLD, referralOutcome(ReferralRedeem("too_old", "")))
        assertEquals(ReferralOutcome.NOT_ELIGIBLE, referralOutcome(ReferralRedeem("not_eligible", "")))
        assertEquals(ReferralOutcome.DISABLED, referralOutcome(ReferralRedeem("disabled", "")))
        assertEquals(ReferralOutcome.TOO_MANY, referralOutcome(ReferralRedeem("too_many", "")))
        assertEquals(ReferralOutcome.OFFLINE, referralOutcome(null))
        assertEquals(ReferralOutcome.OFFLINE, referralOutcome(ReferralRedeem("outra_coisa", "")))
    }

    @Test
    fun cadaFalhaTemMensagemPropria() {
        assertEquals(K.REFERRAL_ERR_INVALID, referralFailureText(ReferralOutcome.INVALID))
        assertEquals(K.REFERRAL_ERR_SELF, referralFailureText(ReferralOutcome.SELF))
        assertEquals(K.REFERRAL_ERR_ALREADY, referralFailureText(ReferralOutcome.ALREADY))
        assertEquals(K.REFERRAL_ERR_TOO_OLD, referralFailureText(ReferralOutcome.TOO_OLD))
        assertEquals(K.REFERRAL_ERR_OFFLINE, referralFailureText(ReferralOutcome.OFFLINE))
    }

    @Test
    fun somaSoOsPremiosNaoVistos() {
        val list = listOf(
            ReferralInvite("a", "paid", 100, unseen = true),
            ReferralInvite("b", "paid", 100, unseen = false),
            ReferralInvite("c", "over_limit", 0, unseen = false),
            ReferralInvite("d", "waiting", 0, unseen = false)
        )
        assertEquals(100, referralUnseenTotal(list))
        assertEquals(2, referralPaidCount(list))
    }

    @Test
    fun valoresMostradosBatemComOSeedDoServidor() {
        assertEquals(100, REFERRAL_SHOWN_DOUBLOONS)
        assertEquals(10, REFERRAL_SHOWN_TOTAL_LIMIT)
    }
}
