package br.com.navalbattle.data

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** Contadores de funil (#137): vocabulario fechado igual ao catalogo do servidor e instrumentacao que nunca derruba o jogo. */
class FunnelTest {
    private val pattern = Regex("^[a-z_]{1,32}$")

    @Test
    fun codigosNoPadraoDoServidor() {
        FunnelAction.entries.forEach {
            assertTrue(pattern.matches(it.event), "evento fora do padrao: ${it.event}")
            assertTrue(pattern.matches(it.origin), "origem fora do padrao: ${it.origin}")
        }
    }

    @Test
    fun paresUnicos() {
        val pares = FunnelAction.entries.map { it.event to it.origin }
        assertEquals(pares.size, pares.toSet().size)
    }

    @Test
    fun catalogoDaZeroQuarentaEOito() {
        // o catalogo semeado em supabase/funnel.sql: evento novo = migracao nova + linha aqui
        val porEvento = FunnelAction.entries.groupBy({ it.event }, { it.origin })
        assertEquals(
            mapOf(
                "store_viewed" to listOf("menu", "hook", "popup", "outro"),
                "invite_shared" to listOf("referral_card", "hook", "settings_store"),
                "first_match_end" to listOf("bot_win", "bot_loss", "bot_abandon", "human_win", "human_loss", "human_abandon"),
                "hook_shown" to listOf("win", "loss"),
                "hook_rematch" to listOf("win", "loss"),
                "hook_invite" to listOf("win", "loss"),
                "hook_challenge" to listOf("win", "loss"),
                "review_requested" to listOf("win", "rankup"),
                "push_opened" to listOf("new_version", "quick_match_invite", "daily_invite", "friend_online", "match_invite", "outro")
            ),
            porEvento
        )
    }

    @Test
    fun fimDaPrimeiraPartidaPeloResultado() {
        assertEquals(FunnelAction.FIRST_MATCH_BOT_WIN, FunnelAction.firstMatchEnd(vsBot = true, won = true, abandoned = false))
        assertEquals(FunnelAction.FIRST_MATCH_BOT_LOSS, FunnelAction.firstMatchEnd(vsBot = true, won = false, abandoned = false))
        assertEquals(FunnelAction.FIRST_MATCH_BOT_ABANDON, FunnelAction.firstMatchEnd(vsBot = true, won = false, abandoned = true))
        assertEquals(FunnelAction.FIRST_MATCH_HUMAN_WIN, FunnelAction.firstMatchEnd(vsBot = false, won = true, abandoned = false))
        assertEquals(FunnelAction.FIRST_MATCH_HUMAN_LOSS, FunnelAction.firstMatchEnd(vsBot = false, won = false, abandoned = false))
        assertEquals(FunnelAction.FIRST_MATCH_HUMAN_ABANDON, FunnelAction.firstMatchEnd(vsBot = false, won = false, abandoned = true))
        // quem abandona nunca conta como vitoria, mesmo que o chamador mande won = true
        assertEquals(FunnelAction.FIRST_MATCH_BOT_ABANDON, FunnelAction.firstMatchEnd(vsBot = true, won = true, abandoned = true))
    }

    @Test
    fun pushDesconhecidoViraOutro() {
        assertEquals(FunnelAction.PUSH_OPENED_NEW_VERSION, FunnelAction.pushOpened("new_version"))
        assertEquals(FunnelAction.PUSH_OPENED_FRIEND_ONLINE, FunnelAction.pushOpened("friend_online"))
        assertEquals(FunnelAction.PUSH_OPENED_MATCH_INVITE, FunnelAction.pushOpened("match_invite"))
        assertEquals(FunnelAction.PUSH_OPENED_QUICK_MATCH_INVITE, FunnelAction.pushOpened("quick_match_found"))
        assertEquals(FunnelAction.PUSH_OPENED_OTHER, FunnelAction.pushOpened("announcement"))
        assertEquals(FunnelAction.PUSH_OPENED_OTHER, FunnelAction.pushOpened(""))
        assertEquals(FunnelAction.PUSH_OPENED_OTHER, FunnelAction.pushOpened(null))
    }

    @Test
    fun semSessaoNaoFazNada() {
        var chamadas = 0
        Funnel.log(CoroutineScope(Dispatchers.Unconfined), signedIn = false) { chamadas++ }
        assertEquals(0, chamadas)
    }

    @Test
    fun semEscopoNaoFazNada() {
        var chamadas = 0
        Funnel.log(null, signedIn = true) { chamadas++ }
        assertEquals(0, chamadas)
    }

    @Test
    fun comSessaoEnvia() {
        var chamadas = 0
        Funnel.log(CoroutineScope(Dispatchers.Unconfined), signedIn = true) { chamadas++ }
        assertEquals(1, chamadas)
    }

    @Test
    fun erroDeRedeNaoSobe() {
        var tentou = false
        Funnel.log(CoroutineScope(Dispatchers.Unconfined), signedIn = true) {
            tentou = true
            error("sem rede")
        }
        assertTrue(tentou)
    }
}
