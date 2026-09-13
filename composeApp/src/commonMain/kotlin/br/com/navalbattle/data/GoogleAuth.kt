package br.com.navalbattle.data

/**
 * Client ID "Web application" criado no Google Cloud Console — o mesmo cadastrado
 * como provedor Google no painel do Supabase (Authentication → Sign In / Providers).
 * Em branco, o botão de entrar com o Google fica desativado. Ver docs/BUILD.md para
 * o passo a passo completo de como gerar esse valor.
 */
object GoogleAuthConfig {
    const val WEB_CLIENT_ID = ""

    val isConfigured: Boolean get() = WEB_CLIENT_ID.isNotBlank()
}

/** Resultado de pedir ao sistema operacional uma conta Google para entrar. */
sealed class GoogleAuthResult {
    data class Ok(val idToken: String) : GoogleAuthResult()
    data class Fail(val message: String) : GoogleAuthResult()

    /** O comandante fechou a caixa de seleção de conta sem escolher nenhuma. */
    data object Cancelled : GoogleAuthResult()
}

/**
 * Pede uma conta Google ao sistema (Credential Manager) e devolve o token de
 * identidade — quem troca esse token por uma sessão de verdade é [CloudApi].
 */
expect class GoogleAuth() {
    suspend fun signIn(): GoogleAuthResult
}
