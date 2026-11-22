package br.com.navalbattle.game

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** Gancho do fim da 1a partida contra o adversario virtual (#135). */
class FirstMatchHookTest {
    private fun show(
        careerMatches: Int = 0, seen: Boolean = false, vsBot: Boolean = true, abandoned: Boolean = false, test: Boolean = false
    ) = FirstMatchHook.shouldShow(careerMatches, seen, vsBot, abandoned, test)

    @Test fun zeroPartidasESemMarcaMostra() = assertTrue(show())

    @Test fun marcaExistenteNaoMostra() = assertFalse(show(seen = true))

    @Test fun umaPartidaConcluidaNaoMostra() = assertFalse(show(careerMatches = 1))

    @Test fun abandonoNaoMostra() = assertFalse(show(abandoned = true))

    @Test fun contraHumanoNaoMostra() = assertFalse(show(vsBot = false))

    @Test fun aparelhoDeTesteNaoMostra() = assertFalse(show(test = true))

    @Test
    fun ordemNaVitoriaEDesafioRevancheConvite() {
        assertEquals(
            listOf(HookAction.CHALLENGE, HookAction.REMATCH, HookAction.INVITE),
            FirstMatchHook.actions(victory = true, signedIn = true, ios = false)
        )
    }

    @Test
    fun ordemNaDerrotaERevancheDesafioConvite() {
        assertEquals(
            listOf(HookAction.REMATCH, HookAction.CHALLENGE, HookAction.INVITE),
            FirstMatchHook.actions(victory = false, signedIn = true, ios = false)
        )
    }

    @Test
    fun convidadoSemContaPerdeODesafioMasMantemRevancheEConviteDaLoja() {
        assertEquals(
            listOf(HookAction.REMATCH, HookAction.INVITE),
            FirstMatchHook.actions(victory = true, signedIn = false, ios = false)
        )
    }

    @Test
    fun iphoneSoTemRevancheEConviteComConta() {
        assertEquals(
            listOf(HookAction.REMATCH, HookAction.INVITE),
            FirstMatchHook.actions(victory = true, signedIn = true, ios = true)
        )
        assertEquals(
            listOf(HookAction.REMATCH, HookAction.INVITE),
            FirstMatchHook.actions(victory = false, signedIn = true, ios = true)
        )
    }

    @Test
    fun iphoneSemContaSoTemRevanche() {
        assertEquals(listOf(HookAction.REMATCH), FirstMatchHook.actions(victory = true, signedIn = false, ios = true))
    }
}
