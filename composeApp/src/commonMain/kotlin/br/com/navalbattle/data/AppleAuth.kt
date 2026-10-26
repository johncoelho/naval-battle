package br.com.navalbattle.data

/** Resultado de pedir à Apple (AuthenticationServices) uma autorização para entrar. */
sealed class AppleAuthResult {
    /**
     * [idToken] e [rawNonce] vão ao Supabase (o `nonce` cru; a Apple recebeu o sha256 dele).
     * [authorizationCode] só serve na exclusão de conta (revogação, vale 5 min).
     * [givenName] a Apple manda só na primeira autorização do app.
     */
    data class Ok(
        val idToken: String,
        val rawNonce: String,
        val authorizationCode: String?,
        val givenName: String?
    ) : AppleAuthResult()

    data class Fail(val message: String) : AppleAuthResult()

    /** O comandante fechou a janela da Apple sem confirmar. */
    data object Cancelled : AppleAuthResult()
}

/**
 * Entrar com a Apple (App Store 4.8). Contrato irmão do [GoogleAuth]: só o iPhone
 * tem implementação de verdade; no Android [supported] é falso e o botão some.
 */
expect class AppleAuth() {
    val supported: Boolean
    suspend fun signIn(): AppleAuthResult
}
