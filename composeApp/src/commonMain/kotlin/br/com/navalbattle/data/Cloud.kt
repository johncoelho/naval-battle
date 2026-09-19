package br.com.navalbattle.data

/**
 * Endereço do projeto Supabase. A chave anônima é pública por natureza — quem
 * protege os dados são as políticas de RLS da tabela, não o segredo da chave.
 * Enquanto estiver em branco o jogo roda inteiro no aparelho, sem conta.
 */
object SupabaseConfig {
    const val URL = "https://cwtslesnthbenxswdcbv.supabase.co"
    const val ANON_KEY = "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9." +
        "eyJpc3MiOiJzdXBhYmFzZSIsInJlZiI6ImN3dHNsZXNudGhiZW54c3dkY2J2Iiwicm9sZSI6ImFub24i" +
        "LCJpYXQiOjE3ODkyNjc4MDksImV4cCI6MjEwNDg0MzgwOX0.nNN323ZRPocbcRVc4DPHNoWtgP2nVBjjojWIMNSZI_g"

    val isConfigured: Boolean get() = URL.isNotBlank() && ANON_KEY.isNotBlank()
}

/** Sessão autenticada devolvida pelo Supabase Auth. */
data class Session(
    val userId: String,
    val email: String,
    val username: String,
    val accessToken: String,
    val refreshToken: String
)

/** A carreira como ela viaja para a nuvem e volta. */
data class CloudProfile(
    val username: String,
    val insignia: String,
    val avatar: String,
    val langCode: String,
    val xp: Int,
    val credits: Int,
    val matches: Int,
    val wins: Int,
    val shots: Int,
    val hits: Int,
    val sunk: Int,
    val streak: Int,
    val bestStreak: Int,
    val owned: String,
    val equipped: String,
    val fleets: String,
    val fleet: String
)

/** Resultado de uma chamada à nuvem, com a mensagem já pronta para a tela. */
sealed class CloudResult<out T> {
    data class Ok<T>(val value: T) : CloudResult<T>()

    /**
     * [expired] marca a falha por sessão vencida: é o sinal para renovar o token
     * com o refresh guardado e repetir a chamada, sem incomodar o comandante.
     */
    data class Fail(val message: String, val expired: Boolean = false) : CloudResult<Nothing>()
}

/**
 * Uma sala de partida online — de amigo (com código) ou de partida rápida.
 * `guestId` nulo quer dizer que ainda não entrou ninguém.
 */
data class OnlineMatch(
    val id: String,
    val hostId: String,
    val guestId: String?,
    val hostName: String,
    val guestName: String?,
    val mode: String,
    val status: String,
    val isQuickMatch: Boolean,
    val inviteCode: String?,
    // nulo: convite aberto (partida rápida ou código); preenchido: convite mirado
    // num amigo específico — só ele pode entrar, e é ele quem recebe o banner
    val invitedId: String?,
    // ranqueada só pareia com outra ranqueada; convite de amigo é sempre casual
    val ranked: Boolean
)

/** A folha de serviço pública de um amigo — sem e-mail, sem avatar (esse é só local). */
data class FriendProfile(
    val username: String,
    val insignia: String,
    val xp: Int,
    val matches: Int,
    val wins: Int,
    val bestStreak: Int,
    val rankedRating: Int
)

/** Uma linha do placar — geral ou de temporada, a mesma forma para os dois. */
data class LeaderboardEntry(
    val userId: String,
    val username: String,
    val rating: Int,
    val matches: Int,
    val wins: Int
)

/** A temporada corrente — as 4 estações do ano, calculadas no servidor. */
data class SeasonInfo(val seasonKey: String, val name: String)

/** Posição e pontuação do próprio comandante no placar — geral ou de temporada. */
data class MyRank(val position: Long, val rating: Int)

/** Uma jogada trocada na sala — o corpo é sempre uma linha do [Protocol]. */
data class OnlineMessage(val id: Long, val senderId: String, val body: String)

/** Resultado de uma busca por nome de comandante, para mandar pedido de amizade. */
data class CommanderHit(val id: String, val username: String)

/** Pedido de amizade — pendente, aceito ou recusado. */
data class Friendship(
    val id: String,
    val requesterId: String,
    val addresseeId: String,
    val requesterUsername: String,
    val addresseeUsername: String,
    val status: String
)

/**
 * Conversa com o Supabase: contas, sincronização da carreira e o modo online
 * (salas de partida e amizades). Implementação nativa por plataforma — no
 * Android e no iOS via HTTP simples, sem biblioteca extra.
 */
expect class CloudApi() {
    suspend fun signUp(email: String, password: String, username: String): CloudResult<Session>
    suspend fun signIn(email: String, password: String): CloudResult<Session>

    /** Troca o token de identidade do Google (ver [GoogleAuth]) por uma sessão do jogo. */
    suspend fun signInWithGoogle(idToken: String): CloudResult<Session>
    suspend fun refresh(refreshToken: String): CloudResult<Session>
    suspend fun loadProfile(session: Session): CloudResult<CloudProfile?>
    suspend fun saveProfile(session: Session, profile: CloudProfile): CloudResult<Unit>

    /**
     * Troca a senha de quem já está logado. O Supabase aceita a troca com só o token
     * de sessão válido, mas exigimos a senha atual antes de chamar isto — reautenticando
     * com [signIn] — para ninguém trocar a senha de uma sessão esquecida aberta.
     */
    suspend fun updatePassword(session: Session, newPassword: String): CloudResult<Unit>

    /** Manda o e-mail de "esqueci minha senha" — link de recuperação do Supabase Auth. */
    suspend fun sendPasswordReset(email: String): CloudResult<Unit>

    // ---------------- modo online ----------------

    /**
     * Cria uma sala — de amigo (com [inviteCode]), de partida rápida, ou um convite
     * mirado num amigo específico ([invitedId]) que só ele pode aceitar.
     */
    suspend fun createOnlineMatch(
        session: Session,
        mode: String,
        quick: Boolean,
        inviteCode: String?,
        hostName: String,
        invitedId: String? = null,
        ranked: Boolean = false
    ): CloudResult<OnlineMatch>

    /** Procura uma sala de partida rápida aberta por outra pessoa — do mesmo tipo (ranqueada ou não). */
    suspend fun findQuickMatch(session: Session, mode: String, ranked: Boolean): CloudResult<OnlineMatch?>

    /** Procura a sala de um código de convite, se ainda estiver esperando alguém. */
    suspend fun findMatchByCode(session: Session, code: String): CloudResult<OnlineMatch?>

    /**
     * Convite mirado ainda esperando resposta, se algum amigo mandou um pra este
     * comandante — usado pelo banner "fulano te convidou" fora da tela Online.
     */
    suspend fun findPendingInvite(session: Session): CloudResult<OnlineMatch?>

    /** Recusa um convite mirado sem entrar na sala — o anfitrião para de esperar. */
    suspend fun declineOnlineInvite(session: Session, matchId: String): CloudResult<Unit>

    /**
     * Tenta entrar numa sala como convidado. Devolve nulo quando outra pessoa
     * entrou primeiro (a escrita só grava se a sala ainda estiver vazia).
     */
    suspend fun joinOnlineMatch(session: Session, matchId: String, guestName: String): CloudResult<OnlineMatch?>

    /** Consulta o estado atual da sala — usado para saber se o convidado já entrou. */
    suspend fun getOnlineMatch(session: Session, matchId: String): CloudResult<OnlineMatch?>

    /** Marca a sala como encerrada (partida terminou ou alguém abandonou). */
    suspend fun closeOnlineMatch(session: Session, matchId: String, status: String): CloudResult<Unit>

    /** Grava uma jogada na sala — o corpo é uma linha do [Protocol]. */
    suspend fun sendOnlineMessage(session: Session, matchId: String, body: String): CloudResult<Unit>

    /** Traz as jogadas novas da sala, mais recentes que [afterId]. */
    suspend fun pollOnlineMessages(session: Session, matchId: String, afterId: Long): CloudResult<List<OnlineMessage>>

    // ---------------- amigos ----------------

    /** Procura comandantes pelo início do nome, para mandar pedido de amizade. */
    suspend fun searchCommander(session: Session, query: String): CloudResult<List<CommanderHit>>

    suspend fun sendFriendRequest(
        session: Session,
        addresseeId: String,
        myUsername: String,
        theirUsername: String
    ): CloudResult<Unit>

    suspend fun respondFriendRequest(session: Session, friendshipId: String, accept: Boolean): CloudResult<Unit>

    suspend fun removeFriendship(session: Session, friendshipId: String): CloudResult<Unit>

    /** Todas as amizades do comandante — pendentes e aceitas, dos dois lados. */
    suspend fun listFriendships(session: Session): CloudResult<List<Friendship>>

    /** Folha de serviço pública de um amigo (exige amizade aceita) — para a tela de perfil dele. */
    suspend fun friendProfile(session: Session, friendId: String): CloudResult<FriendProfile?>

    // ---------------- ranqueada e temporadas ----------------

    /** A temporada corrente (uma das 4 estações do ano), calculada no servidor. */
    suspend fun currentSeason(session: Session): CloudResult<SeasonInfo>

    /** Placar geral (histórico completo, nunca zera). */
    suspend fun leaderboardOverall(session: Session, limit: Int = 50): CloudResult<List<LeaderboardEntry>>

    /** Placar da temporada corrente (reinicia sozinho a cada nova estação). */
    suspend fun leaderboardSeason(session: Session, limit: Int = 50): CloudResult<List<LeaderboardEntry>>

    /** Posição e pontos do próprio comandante — geral (season=false) ou da temporada (season=true). */
    suspend fun myRank(session: Session, season: Boolean): CloudResult<MyRank?>

    /** Fecha o resultado de uma partida ranqueada (soma/subtrai pontos) — idempotente por lado. */
    suspend fun recordRankedResult(session: Session, matchId: String, won: Boolean): CloudResult<Unit>
}
