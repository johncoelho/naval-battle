package br.com.navalbattle.data

import br.com.navalbattle.game.Side
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancel
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * #160: sessão que não renova leva a AUTH_EXPIRED ("entre de novo"), nunca a "Sem ligação".
 * Cloud falso (a interface [OnlineRoomApi]) e corrotinas sem despachante: as falhas terminam na hora.
 */
class OnlineLinkAuthTest {
    private val old = Session("u1", "a@b.c", "Almirante", "access-old", "refresh-old")
    private val fresh = Session("u1", "a@b.c", "Almirante", "access-new", "refresh-new")

    private val room = OnlineMatch(
        id = "m1", hostId = "u1", guestId = null, hostName = "Almirante", guestName = null, mode = "CLASSIC",
        status = "waiting", isQuickMatch = false, inviteCode = "ABC123", invitedId = null, ranked = false
    )

    private val expired = CloudResult.Fail("JWT expired", expired = true)
    private val offline = CloudResult.Fail("sem rede", network = true)

    /** Servidor falso: o token velho dá 401 ([stale]); o novo passa. Conta as chamadas por sessão. */
    private class FakeRooms(
        private val stale: CloudResult.Fail?,
        private val findCode: CloudResult<OnlineMatch?> = CloudResult.Ok(null)
    ) : OnlineRoomApi {
        var room: OnlineMatch? = null
        val tokensSeen = mutableListOf<String>()

        private fun <T> gate(session: Session, ok: () -> CloudResult<T>): CloudResult<T> {
            tokensSeen += session.accessToken
            return if (session.accessToken == "access-old" && stale != null) stale else ok()
        }

        override suspend fun createOnlineMatch(
            session: Session, mode: String, quick: Boolean, inviteCode: String?, hostName: String,
            invitedId: String?, ranked: Boolean
        ) = gate(session) { CloudResult.Ok(room!!) }

        override suspend fun findQuickMatch(session: Session, mode: String, ranked: Boolean) =
            gate(session) { CloudResult.Ok(null) }

        override suspend fun findMatchByCode(session: Session, code: String) = gate(session) { findCode }
        override suspend fun joinOnlineMatch(session: Session, matchId: String, guestName: String) =
            gate(session) { CloudResult.Ok(room) }

        override suspend fun getOnlineMatch(session: Session, matchId: String) = gate(session) { CloudResult.Ok(room) }
        override suspend fun closeOnlineMatch(session: Session, matchId: String, status: String) =
            gate(session) { CloudResult.Ok(Unit) }

        override suspend fun sendOnlineMessage(session: Session, matchId: String, body: String, clientSeq: Int) =
            gate(session) { CloudResult.Ok(Unit) }

        override suspend fun onlineHeartbeat(session: Session, matchId: String) = gate(session) { CloudResult.Ok(null) }
        override suspend fun pollOnlineMessages(session: Session, matchId: String, afterId: Long) =
            gate(session) { CloudResult.Ok(emptyList<OnlineMessage>()) }
    }

    private class Run(val link: OnlineLink, val scope: CoroutineScope, val rooms: FakeRooms) {
        val states = mutableListOf<LinkState>()
        val codes = mutableListOf<String>()
        var renews = 0
        fun onState(state: LinkState, @Suppress("UNUSED_PARAMETER") side: Side) { states += state }
        fun finish() { link.close(); scope.cancel() }
    }

    private fun run(rooms: FakeRooms, refresh: CloudResult<Session>): Run {
        val scope = CoroutineScope(Dispatchers.Unconfined)
        lateinit var holder: Run
        val link = OnlineLink(rooms, scope) { holder.renews++; refresh }
        holder = Run(link, scope, rooms)
        return holder
    }

    private fun create(r: Run) = r.link.createRoom(old, "CLASSIC", onState = r::onState, onCode = { r.codes += it }, onLine = {})
    private fun quick(r: Run) = r.link.quickMatch(old, "CLASSIC", ranked = false, onState = r::onState, onLine = {})
    private fun joinCode(r: Run) = r.link.joinByCode(old, "abc123", onState = r::onState, onLine = {})

    @Test
    fun salaComRefreshRecusadoViraAuthExpired() {
        val r = run(FakeRooms(expired), CloudResult.Fail("Invalid Refresh Token", code = "refresh_token_not_found"))
        create(r)
        assertEquals(LinkState.AUTH_EXPIRED, r.states.last())
        assertFalse(LinkState.FAILED in r.states)
        assertEquals(1, r.renews)
        assertTrue(r.codes.isEmpty())
        r.finish()
    }

    @Test
    fun partidaRapidaComRefreshRecusadoViraAuthExpired() {
        val r = run(FakeRooms(expired), CloudResult.Fail("Invalid Refresh Token"))
        quick(r)
        assertEquals(LinkState.AUTH_EXPIRED, r.states.last())
        assertFalse(LinkState.FAILED in r.states)
        r.finish()
    }

    @Test
    fun entrarPorCodigoComRefreshRecusadoViraAuthExpired() {
        val r = run(FakeRooms(expired), CloudResult.Fail("Invalid Refresh Token"))
        joinCode(r)
        assertEquals(LinkState.AUTH_EXPIRED, r.states.last())
        assertFalse(LinkState.FAILED in r.states)
        r.finish()
    }

    @Test
    fun refreshQueFuncionaRepeteAChamadaEFicaHospedando() {
        val rooms = FakeRooms(expired).also { it.room = room }
        val r = run(rooms, CloudResult.Ok(fresh))
        create(r)
        assertEquals(listOf(LinkState.HOSTING), r.states)
        assertEquals(listOf("ABC123"), r.codes)
        assertEquals(1, r.renews)
        assertEquals("access-old", rooms.tokensSeen.first())
        assertTrue(rooms.tokensSeen.drop(1).all { it == "access-new" })
        r.finish()
    }

    @Test
    fun falhaDeRedeContinuaFailed() {
        val r = run(FakeRooms(offline), CloudResult.Ok(fresh))
        create(r)
        assertEquals(LinkState.FAILED, r.states.last())
        assertEquals(0, r.renews)  // sem 401 não há o que renovar
        r.finish()

        val q = run(FakeRooms(offline), CloudResult.Ok(fresh))
        joinCode(q)
        assertEquals(LinkState.FAILED, q.states.last())
        q.finish()
    }

    @Test
    fun semRedeNoRefreshNaoViraSessaoMorta() {
        val r = run(FakeRooms(expired), CloudResult.Fail("sem rede", network = true))
        create(r)
        assertEquals(LinkState.FAILED, r.states.last())
        assertFalse(LinkState.AUTH_EXPIRED in r.states)
        r.finish()
    }

    @Test
    fun avisoDeSessaoMortaDisparaUmaVezPorRefreshToken() {
        val gate = SessionExpiryGate()
        assertTrue(gate.report("refresh-old"))
        assertFalse(gate.report("refresh-old"))
        assertFalse(gate.report("refresh-old"))
        // sessão nova (login refeito) traz refresh novo: rearma sozinho
        assertTrue(gate.report("refresh-new"))
        assertFalse(gate.report("refresh-new"))
    }
}
