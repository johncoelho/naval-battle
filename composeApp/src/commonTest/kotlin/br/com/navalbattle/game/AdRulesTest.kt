package br.com.navalbattle.game

import br.com.navalbattle.i18n.K
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Anúncio premiado (#29): quando o botão de dobrar a vitória aparece, quanto ele pede, qual
 * unidade de anúncio vale e qual aviso aparece. Os limites do dia (6 anúncios, 375 dobrões, por
 * lugar) moram no servidor e estão no ensaio `supabase/tests/ads_ensaio.sql`.
 */
class AdRulesTest {

    private fun win(
        opponent: Opponent = Opponent.ONLINE,
        victory: Boolean = true,
        credits: Int = 130,
        noCombat: Boolean = false,
        absence: Boolean = false,
        forfeit: Boolean = false
    ) = AdRules.winEligible(opponent, victory, credits, noCombat, absence, forfeit)

    @Test
    fun vitoriaOnlineComDobroesVale() = assertTrue(win())

    @Test
    fun contraIaNuncaDobraPoisNaoHaDobroes() {
        // #34: contra a IA são zero dobrões em qualquer resultado, então não há o que dobrar
        assertFalse(win(opponent = Opponent.AI))
        assertFalse(win(opponent = Opponent.AI, credits = 130))
    }

    @Test
    fun vitoriaOnlineDentroDoLimiteVale() = assertTrue(win(opponent = Opponent.ONLINE))

    @Test
    fun derrotaNaoVale() = assertFalse(win(victory = false))

    @Test
    fun doisJogadoresERedeLocalNaoValem() {
        assertFalse(win(opponent = Opponent.LOCAL))
        assertFalse(win(opponent = Opponent.LAN))
    }

    @Test
    fun vitoriaSemDobroesNaoVale() {
        // online acima do limite de 3 por adversário não rende dobrões (award nulo = 0)
        assertFalse(win(opponent = Opponent.ONLINE, credits = 0))
    }

    @Test
    fun semCombateAusenciaOuAbandonoNaoValem() {
        assertFalse(win(opponent = Opponent.ONLINE, noCombat = true))
        assertFalse(win(opponent = Opponent.ONLINE, absence = true))
        assertFalse(win(opponent = Opponent.ONLINE, forfeit = true))
    }

    @Test
    fun valorCortaNoTeto() {
        assertEquals(130, AdRules.winAmount(130, 150))
        assertEquals(150, AdRules.winAmount(205, 150))
        // o teto do servidor já desconta o que sobrou dos 375 do dia
        assertEquals(20, AdRules.winAmount(130, 20))
        assertEquals(0, AdRules.winAmount(130, 0))
    }

    @Test
    fun referenciaDaVitoria() {
        assertEquals("online:sala-1", AdRules.winRef("sala-1", "abc"))
        assertEquals("ai:abc", AdRules.winRef(null, "abc"))
    }

    @Test
    fun unidadeDeTesteEmDebugEContaDeTeste() {
        // debug e .preview: sempre exemplo, mesmo em emulador e conta comum
        assertEquals(AdRules.TEST_REWARDED_UNIT, AdRules.unitFor(AdPlace.MILE, buildTest = true, serverTest = false, emulator = false))
        assertEquals(AdRules.TEST_REWARDED_UNIT, AdRules.unitFor(AdPlace.MILE, buildTest = true, serverTest = true, emulator = true))
        // um bloco real por lugar, todos diferentes
        assertEquals(4, AdRules.REAL_UNITS.values.toSet().size)
    }

    @Test
    fun contaComumRecebeBlocoRealEmQualquerAparelho() {
        // produção ('true'): bloco real do lugar, emulador ou não
        assertEquals(
            "ca-app-pub-4691964664970287/4873589041",
            AdRules.unitFor(AdPlace.MILE, buildTest = false, serverTest = false, emulator = false)
        )
        assertEquals(
            "ca-app-pub-4691964664970287/4873589041",
            AdRules.unitFor(AdPlace.MILE, buildTest = false, serverTest = false, emulator = true)
        )
    }

    @Test
    fun contaDeTesteSoGanhaBlocoRealEmEmulador() {
        // QA01/QA02 no emulador: bloco real de cada lugar (SSV dispara)
        for (lugar in AdPlace.values()) {
            assertEquals(AdRules.REAL_UNITS.getValue(lugar), AdRules.unitFor(lugar, buildTest = false, serverTest = true, emulator = true))
        }
        // aparelho físico com conta de teste: exemplo, nunca o bloco real
        for (lugar in AdPlace.values()) {
            assertEquals(AdRules.TEST_REWARDED_UNIT, AdRules.unitFor(lugar, buildTest = false, serverTest = true, emulator = false))
        }
    }

    @Test
    fun sinaisDeEmuladorDoAndroid() {
        // emuladores comuns
        assertTrue(AdRules.isEmulatorBuild("google/sdk_gphone64_arm64/emu64a:14/UE1A/eng:userdebug/test-keys", "sdk_gphone64_arm64", "ranchu", "sdk_gphone64_arm64", "Google"))
        assertTrue(AdRules.isEmulatorBuild("generic/sdk_phone_x86/generic_x86:11/RSR1/eng:userdebug", "Android SDK built for x86", "goldfish", "sdk_phone_x86", "Google"))
        assertTrue(AdRules.isEmulatorBuild("unknown", "Emulator", "goldfish", "vbox86p", "Genymotion"))
        // Genymotion só pelo fabricante
        assertTrue(AdRules.isEmulatorBuild("samsung/x/y:14/z", "SM-S911B", "qcom", "x", "Genymotion"))
        // aparelhos comuns
        assertFalse(AdRules.isEmulatorBuild("google/cheetah/cheetah:14/AP1A/123:user/release-keys", "Pixel 7 Pro", "cheetah", "cheetah", "Google"))
        assertFalse(AdRules.isEmulatorBuild("samsung/dm1qxxx/dm1q:14/UP1A/S911BXXU1:user/release-keys", "SM-S911B", "qcom", "dm1qxxx", "samsung"))
    }

    @Test
    fun avisoPorDesfecho() {
        assertNull(AdRules.notice(AdShowOutcome.EARNED, "applied"))
        assertEquals(K.AD_DELAYED, AdRules.notice(AdShowOutcome.EARNED, "pending"))
        assertEquals(K.AD_CLOSED_EARLY, AdRules.notice(AdShowOutcome.CLOSED_EARLY, null))
        assertEquals(K.AD_UNAVAILABLE, AdRules.notice(AdShowOutcome.UNAVAILABLE, null))
    }

    @Test
    fun avisoQuandoOServidorRecusaOVale() {
        assertNull(AdRules.startNotice("ok"))
        assertEquals(K.AD_LIMIT, AdRules.startNotice("limit"))
        assertEquals(K.AD_UNAVAILABLE, AdRules.startNotice("off"))
        assertEquals(K.AD_UNAVAILABLE, AdRules.startNotice("not_eligible"))
    }

    @Test
    fun vitoriaPorAusenciaFicaMarcadaNaPartida() {
        val m = Match(GameMode.CLASSIC, Opponent.ONLINE)
        assertFalse(m.wonByAbsence)
        m.abandon(m.mySide)
        assertTrue(m.wonByAbsence)
    }

    @Test
    fun cadaPartidaTemIdProprio() {
        val a = Match(GameMode.CLASSIC)
        val b = Match(GameMode.CLASSIC)
        assertEquals(32, a.localId.length)
        assertTrue(a.localId != b.localId)
    }
}
