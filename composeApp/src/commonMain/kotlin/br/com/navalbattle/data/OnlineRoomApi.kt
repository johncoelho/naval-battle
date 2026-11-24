package br.com.navalbattle.data

/**
 * O que o [OnlineLink] usa da nuvem: só as chamadas das salas e das jogadas. Existe porque o
 * [CloudApi] é `expect class` (final, sem como trocar por um falso); com esta interface o
 * `commonTest` testa o caminho da sessão vencida (#160) sem rede.
 */
interface OnlineRoomApi {
    suspend fun createOnlineMatch(
        session: Session,
        mode: String,
        quick: Boolean,
        inviteCode: String?,
        hostName: String,
        invitedId: String? = null,
        ranked: Boolean = false
    ): CloudResult<OnlineMatch>

    suspend fun findQuickMatch(session: Session, mode: String, ranked: Boolean): CloudResult<OnlineMatch?>
    suspend fun findMatchByCode(session: Session, code: String): CloudResult<OnlineMatch?>
    suspend fun joinOnlineMatch(session: Session, matchId: String, guestName: String): CloudResult<OnlineMatch?>
    suspend fun getOnlineMatch(session: Session, matchId: String): CloudResult<OnlineMatch?>
    suspend fun closeOnlineMatch(session: Session, matchId: String, status: String): CloudResult<Unit>
    suspend fun sendOnlineMessage(session: Session, matchId: String, body: String, clientSeq: Int): CloudResult<Unit>
    suspend fun onlineHeartbeat(session: Session, matchId: String): CloudResult<Int?>
    suspend fun pollOnlineMessages(session: Session, matchId: String, afterId: Long): CloudResult<List<OnlineMessage>>
}

/** Liga o [OnlineRoomApi] ao [CloudApi] de verdade (só repassa). */
class CloudRoomApi(private val cloud: CloudApi) : OnlineRoomApi {
    override suspend fun createOnlineMatch(
        session: Session, mode: String, quick: Boolean, inviteCode: String?, hostName: String,
        invitedId: String?, ranked: Boolean
    ) = cloud.createOnlineMatch(session, mode, quick, inviteCode, hostName, invitedId, ranked)

    override suspend fun findQuickMatch(session: Session, mode: String, ranked: Boolean) =
        cloud.findQuickMatch(session, mode, ranked)

    override suspend fun findMatchByCode(session: Session, code: String) = cloud.findMatchByCode(session, code)

    override suspend fun joinOnlineMatch(session: Session, matchId: String, guestName: String) =
        cloud.joinOnlineMatch(session, matchId, guestName)

    override suspend fun getOnlineMatch(session: Session, matchId: String) = cloud.getOnlineMatch(session, matchId)

    override suspend fun closeOnlineMatch(session: Session, matchId: String, status: String) =
        cloud.closeOnlineMatch(session, matchId, status)

    override suspend fun sendOnlineMessage(session: Session, matchId: String, body: String, clientSeq: Int) =
        cloud.sendOnlineMessage(session, matchId, body, clientSeq)

    override suspend fun onlineHeartbeat(session: Session, matchId: String) = cloud.onlineHeartbeat(session, matchId)

    override suspend fun pollOnlineMessages(session: Session, matchId: String, afterId: Long) =
        cloud.pollOnlineMessages(session, matchId, afterId)
}

/**
 * Sessão morta avisa uma vez só (#160): [report] devolve verdadeiro na primeira vez que um refresh
 * token é dado como recusado e falso nas seguintes com o mesmo token. Sessão nova traz refresh novo,
 * então rearma sozinho: sem flag para zerar e sem laço de login.
 */
class SessionExpiryGate {
    private var deadRefresh: String? = null

    fun report(refreshToken: String): Boolean {
        if (refreshToken == deadRefresh) return false
        deadRefresh = refreshToken
        return true
    }
}
