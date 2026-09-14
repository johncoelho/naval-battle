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
    val inviteCode: String?
)

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

    /** Cria uma sala — de amigo (com [inviteCode]) ou de partida rápida. */
    suspend fun createOnlineMatch(
        session: Session,
        mode: String,
        quick: Boolean,
        inviteCode: String?,
        hostName: String
    ): CloudResult<OnlineMatch>

    /** Procura uma sala de partida rápida aberta por outra pessoa. */
    suspend fun findQuickMatch(session: Session, mode: String): CloudResult<OnlineMatch?>

    /** Procura a sala de um código de convite, se ainda estiver esperando alguém. */
    suspend fun findMatchByCode(session: Session, code: String): CloudResult<OnlineMatch?>

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
}
