package br.com.navalbattle

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/** #19: o toque no push "amigo online" abre a tela de Amigos; os outros só abrem o jogo. */
class PushKindTest {
    @Test
    fun amigoOnlineAbreAmigos() {
        assertEquals(Screen.FRIENDS, screenForPushKind("friend_online"))
    }

    @Test
    fun outrosAvisosSoAbremOJogo() {
        assertNull(screenForPushKind("match_invite"))
        assertNull(screenForPushKind("friend_request"))
        assertNull(screenForPushKind(""))
    }
}
