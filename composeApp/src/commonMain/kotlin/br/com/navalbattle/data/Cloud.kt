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
 * Conversa com o Supabase: contas e sincronização da carreira. Implementação
 * nativa por plataforma — no Android via HTTP simples, sem biblioteca extra.
 */
expect class CloudApi() {
    suspend fun signUp(email: String, password: String, username: String): CloudResult<Session>
    suspend fun signIn(email: String, password: String): CloudResult<Session>
    suspend fun refresh(refreshToken: String): CloudResult<Session>
    suspend fun loadProfile(session: Session): CloudResult<CloudProfile?>
    suspend fun saveProfile(session: Session, profile: CloudProfile): CloudResult<Unit>
}
