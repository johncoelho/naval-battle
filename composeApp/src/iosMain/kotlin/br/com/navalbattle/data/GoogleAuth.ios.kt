package br.com.navalbattle.data

import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.coroutines.suspendCancellableCoroutine
import platform.AuthenticationServices.ASPresentationAnchor
import platform.AuthenticationServices.ASWebAuthenticationPresentationContextProvidingProtocol
import platform.AuthenticationServices.ASWebAuthenticationSession
import platform.Foundation.NSURL
import platform.UIKit.UIApplication
import platform.UIKit.UIWindow
import platform.darwin.NSObject
import kotlin.coroutines.resume

/**
 * Login com Google no iOS — sem Credential Manager (exclusivo Android) nem SDK
 * GoogleSignIn-iOS (evita depender de Swift Package Manager), faz o próprio fluxo
 * OAuth "implícito" (`response_type=id_token`) direto contra o `accounts.google.com`
 * via `ASWebAuthenticationSession`, nativo do iOS desde a versão 12. O retorno chega
 * pela URL de redirecionamento com o token no fragmento (`#id_token=...`), capturado
 * pelo esquema de URL "reverso" do Client ID iOS, registrado no `Info.plist`
 * (`CFBundleURLTypes`).
 *
 * O Client ID iOS é DIFERENTE do Web (`GoogleAuthConfig.WEB_CLIENT_ID`, usado só pelo
 * Android): precisa estar cadastrado como tipo "iOS" no Google Cloud Console (Bundle ID
 * `br.com.navalbattle`) e também na lista de "Client IDs" do provedor Google no
 * Supabase — junto dos outros, separados por vírgula — senão o token sai com uma
 * audiência que o Supabase rejeita.
 */
@OptIn(ExperimentalForeignApi::class)
actual class GoogleAuth actual constructor() {

    actual suspend fun signIn(): GoogleAuthResult {
        if (GoogleAuthConfig.IOS_CLIENT_ID.isBlank()) {
            return GoogleAuthResult.Fail("Login com Google ainda não configurado para iPhone.")
        }

        val scheme = reversedClientIdScheme(GoogleAuthConfig.IOS_CLIENT_ID)
        val redirectUri = "$scheme:/oauth2redirect"
        val authUrl = NSURL(string = buildAuthUrl(redirectUri))
            ?: return GoogleAuthResult.Fail("Não consegui montar o link de login do Google.")

        return suspendCancellableCoroutine { continuation ->
            val provider = PresentationContextProvider()
            val session = ASWebAuthenticationSession(
                uRL = authUrl,
                callbackURLScheme = scheme,
                completionHandler = { callbackUrl, error ->
                    val result = if (callbackUrl == null) {
                        val cancelled = error?.localizedDescription?.contains("cancel", ignoreCase = true) == true
                        if (cancelled) GoogleAuthResult.Cancelled
                        else GoogleAuthResult.Fail(error?.localizedDescription ?: "Login com Google cancelado.")
                    } else {
                        val idToken = extractIdToken(callbackUrl.fragment)
                        if (idToken != null) GoogleAuthResult.Ok(idToken)
                        else GoogleAuthResult.Fail("O Google não devolveu um token de identidade.")
                    }
                    continuation.resume(result)
                }
            )
            session.presentationContextProvider = provider
            session.prefersEphemeralWebBrowserSession = true
            // segura as duas referências até o completionHandler disparar, senão o ARC
            // libera a sessão no meio do fluxo e o callback nunca chega
            continuation.invokeOnCancellation { session.cancel() }
            if (!session.start()) {
                continuation.resume(GoogleAuthResult.Fail("Não consegui abrir a tela do Google agora."))
            }
        }
    }
}

/** `123-abc.apps.googleusercontent.com` → `com.googleusercontent.apps.123-abc` (convenção do Google para o esquema de retorno no iOS). */
private fun reversedClientIdScheme(clientId: String): String {
    val prefix = clientId.substringBefore(".apps.googleusercontent.com")
    return "com.googleusercontent.apps.$prefix"
}

private fun buildAuthUrl(redirectUri: String): String = buildString {
    append("https://accounts.google.com/o/oauth2/v2/auth")
    append("?client_id=").append(urlEncode(GoogleAuthConfig.IOS_CLIENT_ID))
    append("&redirect_uri=").append(urlEncode(redirectUri))
    append("&response_type=id_token")
    append("&scope=").append(urlEncode("openid email profile"))
    append("&prompt=select_account")
}

/** O Google devolve o token no FRAGMENTO da URL (`#id_token=...&...`), não na query. */
private fun extractIdToken(fragment: String?): String? {
    if (fragment.isNullOrBlank()) return null
    return fragment.split("&")
        .mapNotNull { pair ->
            val parts = pair.split("=", limit = 2)
            if (parts.size == 2) parts[0] to parts[1] else null
        }
        .firstOrNull { it.first == "id_token" }
        ?.second
}

private val HEX_DIGITS = "0123456789ABCDEF"

/** Percent-encoding escrito à mão — evita depender de `NSString.stringByAddingPercentEncoding`. */
private fun urlEncode(value: String): String = buildString {
    for (byte in value.encodeToByteArray()) {
        val v = byte.toInt() and 0xFF
        val c = v.toChar()
        if (c.isLetterOrDigit() || c == '-' || c == '.' || c == '_' || c == '~') {
            append(c)
        } else {
            append('%')
            append(HEX_DIGITS[v ushr 4])
            append(HEX_DIGITS[v and 0xF])
        }
    }
}

/** Devolve a janela principal, onde o Safari embutido do login aparece por cima. */
private class PresentationContextProvider : NSObject(), ASWebAuthenticationPresentationContextProvidingProtocol {
    override fun presentationAnchorForWebAuthenticationSession(session: ASWebAuthenticationSession): ASPresentationAnchor {
        val windows = UIApplication.sharedApplication.windows.filterIsInstance<UIWindow>()
        return windows.firstOrNull { it.isKeyWindow() } ?: windows.firstOrNull() ?: UIWindow()
    }
}
