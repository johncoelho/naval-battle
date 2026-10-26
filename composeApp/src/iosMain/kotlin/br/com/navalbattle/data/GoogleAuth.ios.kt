package br.com.navalbattle.data

import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.addressOf
import kotlinx.cinterop.usePinned
import kotlinx.coroutines.suspendCancellableCoroutine
import platform.AuthenticationServices.ASPresentationAnchor
import platform.AuthenticationServices.ASWebAuthenticationPresentationContextProvidingProtocol
import platform.AuthenticationServices.ASWebAuthenticationSession
import platform.Foundation.*
import platform.UIKit.UIApplication
import platform.UIKit.UIWindow
import platform.darwin.NSObject
import kotlin.coroutines.resume
import kotlin.random.Random

/**
 * Login com Google no iOS — sem Credential Manager (exclusivo Android) nem SDK
 * GoogleSignIn-iOS (evita depender de Swift Package Manager), faz o próprio fluxo OAuth
 * **Authorization Code + PKCE** contra `accounts.google.com` via `ASWebAuthenticationSession`,
 * nativo do iOS desde a versão 12.
 *
 * O fluxo "implícito" (`response_type=id_token`) direto, tentado primeiro, o Google recusa
 * para Client ID tipo "iOS" com `Erro 400: unsupported_response_type` — só funciona para
 * clientes "Web application". Por isso o fluxo aqui é em duas etapas: (1) abre o navegador
 * pedindo um `code` (`response_type=code`, com `code_challenge`/PKCE já que o Client ID iOS
 * não tem Client Secret para provar quem está pedindo o token), e (2) troca esse `code` por
 * um `id_token` de verdade via POST em `oauth2.googleapis.com/token`, usando o `code_verifier`
 * original (nunca exposto no navegador) para provar que quem troca o código é quem o pediu.
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
        val codeVerifier = generateCodeVerifier()
        val codeChallenge = base64UrlEncode(sha256(codeVerifier.encodeToByteArray()))
        val authUrl = NSURL(string = buildAuthUrl(redirectUri, codeChallenge))
            ?: return GoogleAuthResult.Fail("Não consegui montar o link de login do Google.")

        return when (val step = awaitAuthorizationCode(authUrl, scheme)) {
            is AuthStep.Cancelled -> GoogleAuthResult.Cancelled
            is AuthStep.Fail -> GoogleAuthResult.Fail(step.message)
            is AuthStep.Code -> {
                val idToken = exchangeCodeForIdToken(step.code, codeVerifier, redirectUri)
                if (idToken != null) GoogleAuthResult.Ok(idToken)
                else GoogleAuthResult.Fail("O Google não devolveu um token de identidade.")
            }
        }
    }

    private suspend fun awaitAuthorizationCode(authUrl: NSURL, scheme: String): AuthStep =
        suspendCancellableCoroutine { continuation ->
            val provider = PresentationContextProvider()
            val session = ASWebAuthenticationSession(
                uRL = authUrl,
                callbackURLScheme = scheme,
                completionHandler = { callbackUrl, error ->
                    val result = if (callbackUrl == null) {
                        val cancelled = error?.localizedDescription?.contains("cancel", ignoreCase = true) == true
                        if (cancelled) AuthStep.Cancelled
                        else AuthStep.Fail(error?.localizedDescription ?: "Login com Google cancelado.")
                    } else {
                        val code = extractQueryParam(callbackUrl.query, "code")
                        if (code != null) AuthStep.Code(code)
                        else AuthStep.Fail("O Google não devolveu um código de autorização.")
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
                continuation.resume(AuthStep.Fail("Não consegui abrir a tela do Google agora."))
            }
        }
}

private sealed class AuthStep {
    data class Code(val code: String) : AuthStep()
    data class Fail(val message: String) : AuthStep()
    data object Cancelled : AuthStep()
}

/** `123-abc.apps.googleusercontent.com` → `com.googleusercontent.apps.123-abc` (convenção do Google para o esquema de retorno no iOS). */
private fun reversedClientIdScheme(clientId: String): String {
    val prefix = clientId.substringBefore(".apps.googleusercontent.com")
    return "com.googleusercontent.apps.$prefix"
}

private fun buildAuthUrl(redirectUri: String, codeChallenge: String): String = buildString {
    append("https://accounts.google.com/o/oauth2/v2/auth")
    append("?client_id=").append(urlEncode(GoogleAuthConfig.IOS_CLIENT_ID))
    append("&redirect_uri=").append(urlEncode(redirectUri))
    append("&response_type=code")
    append("&code_challenge=").append(urlEncode(codeChallenge))
    append("&code_challenge_method=S256")
    append("&scope=").append(urlEncode("openid email profile"))
    append("&prompt=select_account")
}

/** Troca o `code` (autorização de curta duração) pelo `id_token` de verdade — a etapa que faltava no fluxo implícito, que o Google não aceita para Client ID tipo "iOS". */
@OptIn(ExperimentalForeignApi::class)
private suspend fun exchangeCodeForIdToken(code: String, codeVerifier: String, redirectUri: String): String? =
    suspendCancellableCoroutine { continuation ->
        val url = NSURL(string = "https://oauth2.googleapis.com/token")!!
        val req = NSMutableURLRequest(uRL = url)
        req.setHTTPMethod("POST")
        req.setValue("application/x-www-form-urlencoded", forHTTPHeaderField = "Content-Type")
        val form = buildString {
            append("client_id=").append(urlEncode(GoogleAuthConfig.IOS_CLIENT_ID))
            append("&code=").append(urlEncode(code))
            append("&code_verifier=").append(urlEncode(codeVerifier))
            append("&redirect_uri=").append(urlEncode(redirectUri))
            append("&grant_type=authorization_code")
        }
        req.setHTTPBody(stringToData(form))

        val task = NSURLSession.sharedSession.dataTaskWithRequest(req) { data, response, _ ->
            val http = response as? NSHTTPURLResponse
            val statusCode = http?.statusCode?.toInt() ?: 0
            if (statusCode !in 200..299 || data == null) {
                continuation.resume(null)
                return@dataTaskWithRequest
            }
            @Suppress("UNCHECKED_CAST")
            val parsed = NSJSONSerialization.JSONObjectWithData(data, 0uL, null) as? Map<Any?, *>
            continuation.resume(parsed?.get("id_token") as? String)
        }
        continuation.invokeOnCancellation { task.cancel() }
        task.resume()
    }

/** O `code` da autorização volta na QUERY da URL de retorno (`?code=...&scope=...`), diferente do fluxo implícito que usava o fragmento. */
private fun extractQueryParam(query: String?, key: String): String? {
    if (query.isNullOrBlank()) return null
    return query.split("&")
        .mapNotNull { pair ->
            val parts = pair.split("=", limit = 2)
            if (parts.size == 2) parts[0] to parts[1] else null
        }
        .firstOrNull { it.first == key }
        ?.second
}

@OptIn(ExperimentalForeignApi::class)
private fun stringToData(text: String): NSData {
    val bytes = text.encodeToByteArray()
    if (bytes.isEmpty()) return NSData()
    return bytes.usePinned { pinned -> NSData.create(bytes = pinned.addressOf(0), length = bytes.size.toULong()) }
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

// -------------------------------------------------------------- PKCE (RFC 7636)

/** `code_verifier`: string aleatória (32 bytes, 43 chars em base64url) que só este app conhece. */
private fun generateCodeVerifier(): String {
    val bytes = ByteArray(32) { Random.nextInt(0, 256).toByte() }
    return base64UrlEncode(bytes)
}

/** Devolve a janela principal, onde o Safari embutido do login aparece por cima. */
private class PresentationContextProvider : NSObject(), ASWebAuthenticationPresentationContextProvidingProtocol {
    override fun presentationAnchorForWebAuthenticationSession(session: ASWebAuthenticationSession): ASPresentationAnchor {
        val windows = UIApplication.sharedApplication.windows.filterIsInstance<UIWindow>()
        return windows.firstOrNull { it.isKeyWindow() } ?: windows.firstOrNull() ?: UIWindow()
    }
}
