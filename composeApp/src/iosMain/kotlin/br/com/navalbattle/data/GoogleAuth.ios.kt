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

private const val BASE64_URL_ALPHABET = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789-_"

/** Base64url sem padding (RFC 4648 §5) — escrito à mão para não depender de versão do stdlib. */
private fun base64UrlEncode(bytes: ByteArray): String = buildString {
    var i = 0
    while (i < bytes.size) {
        val b0 = bytes[i].toInt() and 0xFF
        val b1 = if (i + 1 < bytes.size) bytes[i + 1].toInt() and 0xFF else 0
        val b2 = if (i + 2 < bytes.size) bytes[i + 2].toInt() and 0xFF else 0
        val triple = (b0 shl 16) or (b1 shl 8) or b2
        append(BASE64_URL_ALPHABET[(triple shr 18) and 0x3F])
        append(BASE64_URL_ALPHABET[(triple shr 12) and 0x3F])
        if (i + 1 < bytes.size) append(BASE64_URL_ALPHABET[(triple shr 6) and 0x3F])
        if (i + 2 < bytes.size) append(BASE64_URL_ALPHABET[triple and 0x3F])
        i += 3
    }
}

/** SHA-256 puro Kotlin (RFC 6234) — só usado para o `code_challenge` do PKCE; evita cinterop com CommonCrypto. */
private val SHA256_K = uintArrayOf(
    0x428a2f98u, 0x71374491u, 0xb5c0fbcfu, 0xe9b5dba5u, 0x3956c25bu, 0x59f111f1u, 0x923f82a4u, 0xab1c5ed5u,
    0xd807aa98u, 0x12835b01u, 0x243185beu, 0x550c7dc3u, 0x72be5d74u, 0x80deb1feu, 0x9bdc06a7u, 0xc19bf174u,
    0xe49b69c1u, 0xefbe4786u, 0x0fc19dc6u, 0x240ca1ccu, 0x2de92c6fu, 0x4a7484aau, 0x5cb0a9dcu, 0x76f988dau,
    0x983e5152u, 0xa831c66du, 0xb00327c8u, 0xbf597fc7u, 0xc6e00bf3u, 0xd5a79147u, 0x06ca6351u, 0x14292967u,
    0x27b70a85u, 0x2e1b2138u, 0x4d2c6dfcu, 0x53380d13u, 0x650a7354u, 0x766a0abbu, 0x81c2c92eu, 0x92722c85u,
    0xa2bfe8a1u, 0xa81a664bu, 0xc24b8b70u, 0xc76c51a3u, 0xd192e819u, 0xd6990624u, 0xf40e3585u, 0x106aa070u,
    0x19a4c116u, 0x1e376c08u, 0x2748774cu, 0x34b0bcb5u, 0x391c0cb3u, 0x4ed8aa4au, 0x5b9cca4fu, 0x682e6ff3u,
    0x748f82eeu, 0x78a5636fu, 0x84c87814u, 0x8cc70208u, 0x90befffau, 0xa4506cebu, 0xbef9a3f7u, 0xc67178f2u
)

private fun sha256(message: ByteArray): ByteArray {
    val h = uintArrayOf(
        0x6a09e667u, 0xbb67ae85u, 0x3c6ef372u, 0xa54ff53au,
        0x510e527fu, 0x9b05688cu, 0x1f83d9abu, 0x5be0cd19u
    )

    val bitLength = message.size.toULong() * 8uL
    val paddingZeros = ((56 - (message.size + 1) % 64) + 64) % 64
    val padded = ByteArray(message.size + 1 + paddingZeros + 8)
    message.copyInto(padded)
    padded[message.size] = 0x80.toByte()
    for (i in 0 until 8) {
        padded[padded.size - 1 - i] = ((bitLength shr (8 * i)) and 0xFFuL).toByte()
    }

    val w = IntArray(64)
    var chunkStart = 0
    while (chunkStart < padded.size) {
        for (i in 0 until 16) {
            val base = chunkStart + i * 4
            w[i] = ((padded[base].toInt() and 0xFF) shl 24) or
                ((padded[base + 1].toInt() and 0xFF) shl 16) or
                ((padded[base + 2].toInt() and 0xFF) shl 8) or
                (padded[base + 3].toInt() and 0xFF)
        }
        for (i in 16 until 64) {
            val w15 = w[i - 15].toUInt()
            val w2 = w[i - 2].toUInt()
            val s0 = w15.rotateRight(7) xor w15.rotateRight(18) xor (w15 shr 3)
            val s1 = w2.rotateRight(17) xor w2.rotateRight(19) xor (w2 shr 10)
            w[i] = (w[i - 16].toUInt() + s0 + w[i - 7].toUInt() + s1).toInt()
        }

        var a = h[0]; var b = h[1]; var c = h[2]; var d = h[3]
        var e = h[4]; var f = h[5]; var g = h[6]; var hh = h[7]

        for (i in 0 until 64) {
            val s1 = e.rotateRight(6) xor e.rotateRight(11) xor e.rotateRight(25)
            val ch = (e and f) xor (e.inv() and g)
            val temp1 = hh + s1 + ch + SHA256_K[i] + w[i].toUInt()
            val s0 = a.rotateRight(2) xor a.rotateRight(13) xor a.rotateRight(22)
            val maj = (a and b) xor (a and c) xor (b and c)
            val temp2 = s0 + maj

            hh = g; g = f; f = e; e = d + temp1
            d = c; c = b; b = a; a = temp1 + temp2
        }

        h[0] += a; h[1] += b; h[2] += c; h[3] += d
        h[4] += e; h[5] += f; h[6] += g; h[7] += hh

        chunkStart += 64
    }

    val result = ByteArray(32)
    for (i in 0 until 8) {
        result[i * 4] = (h[i] shr 24).toByte()
        result[i * 4 + 1] = (h[i] shr 16).toByte()
        result[i * 4 + 2] = (h[i] shr 8).toByte()
        result[i * 4 + 3] = h[i].toByte()
    }
    return result
}

/** Devolve a janela principal, onde o Safari embutido do login aparece por cima. */
private class PresentationContextProvider : NSObject(), ASWebAuthenticationPresentationContextProvidingProtocol {
    override fun presentationAnchorForWebAuthenticationSession(session: ASWebAuthenticationSession): ASPresentationAnchor {
        val windows = UIApplication.sharedApplication.windows.filterIsInstance<UIWindow>()
        return windows.firstOrNull { it.isKeyWindow() } ?: windows.firstOrNull() ?: UIWindow()
    }
}
