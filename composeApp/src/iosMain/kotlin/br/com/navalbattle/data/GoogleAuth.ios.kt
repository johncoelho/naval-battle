package br.com.navalbattle.data

/**
 * Login com Google ainda não está portado para o iOS (precisa do SDK GoogleSignIn via
 * Swift Package Manager e de uma janela para apresentar a tela de conta — ver
 * docs/BUILD.md). O cadastro por e-mail continua funcionando normalmente.
 */
actual class GoogleAuth actual constructor() {
    actual suspend fun signIn(): GoogleAuthResult =
        GoogleAuthResult.Fail("Login com Google ainda não está disponível nesta versão para iPhone.")
}
