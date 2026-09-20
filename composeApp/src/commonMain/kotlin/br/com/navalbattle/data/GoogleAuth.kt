package br.com.navalbattle.data

/**
 * Client ID "Web application" criado no Google Cloud Console — o mesmo cadastrado
 * como provedor Google no painel do Supabase (Authentication → Sign In / Providers).
 * Em branco, o botão de entrar com o Google fica desativado. Ver docs/BUILD.md para
 * o passo a passo completo de como gerar esse valor.
 */
object GoogleAuthConfig {
    const val WEB_CLIENT_ID =
        "66774326611-010ggjho4008i5duqnau05br5oq1ubbo.apps.googleusercontent.com"

    /**
     * Client ID "iOS" criado no Google Cloud Console (Bundle ID `br.com.navalbattle`) —
     * usado só pelo iOS, que não tem Credential Manager e por isso faz o próprio fluxo
     * OAuth via `ASWebAuthenticationSession` (ver `GoogleAuth.ios.kt`). Precisa estar
     * cadastrado também na lista de "Client IDs" do provedor Google no Supabase, junto
     * com o Web e o Android — senão o token de identidade sai com uma audiência que o
     * Supabase não reconhece e a troca por sessão falha.
     */
    const val IOS_CLIENT_ID =
        "66774326611-rql2s9av51hpgvr0hq7jtclee4a1rcag.apps.googleusercontent.com"

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
