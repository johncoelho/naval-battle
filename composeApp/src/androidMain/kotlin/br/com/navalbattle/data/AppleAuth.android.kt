package br.com.navalbattle.data

/** Android: sem Entrar com a Apple (a 4.8 vale só para a App Store). O botão some por [supported]. */
actual class AppleAuth actual constructor() {
    actual val supported: Boolean get() = false

    actual suspend fun signIn(): AppleAuthResult =
        AppleAuthResult.Fail("Entrar com a Apple não está disponível neste aparelho.")
}
