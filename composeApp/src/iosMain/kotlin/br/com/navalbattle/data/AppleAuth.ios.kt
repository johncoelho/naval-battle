package br.com.navalbattle.data

import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.addressOf
import kotlinx.cinterop.usePinned
import kotlinx.coroutines.suspendCancellableCoroutine
import platform.AuthenticationServices.ASAuthorization
import platform.AuthenticationServices.ASAuthorizationAppleIDCredential
import platform.AuthenticationServices.ASAuthorizationAppleIDProvider
import platform.AuthenticationServices.ASAuthorizationController
import platform.AuthenticationServices.ASAuthorizationControllerDelegateProtocol
import platform.AuthenticationServices.ASAuthorizationControllerPresentationContextProvidingProtocol
import platform.AuthenticationServices.ASAuthorizationScopeEmail
import platform.AuthenticationServices.ASAuthorizationScopeFullName
import platform.AuthenticationServices.ASPresentationAnchor
import platform.Foundation.NSData
import platform.Foundation.NSError
import platform.Foundation.NSString
import platform.Foundation.NSUTF8StringEncoding
import platform.Foundation.create
import platform.Security.SecRandomCopyBytes
import platform.Security.kSecRandomDefault
import platform.UIKit.UIApplication
import platform.UIKit.UIWindow
import platform.darwin.NSObject
import br.com.navalbattle.i18n.K
import br.com.navalbattle.i18n.t
import kotlin.coroutines.resume

/** Códigos de `ASAuthorizationError` (valores fixos da Apple). */
private const val APPLE_ERROR_UNKNOWN = 1000L
private const val APPLE_ERROR_CANCELED = 1001L

/**
 * Entrar com a Apple no iPhone: `AuthenticationServices` (framework do sistema, sem SDK).
 * O pedido leva `nonce = sha256Hex(nonceCru)`; o Supabase recebe o nonce cru e confere.
 * Sem entitlement (o `.ipa` do Sideloadly vem sem) o sistema devolve erro 1000.
 */
@OptIn(ExperimentalForeignApi::class)
actual class AppleAuth actual constructor() {

    actual val supported: Boolean get() = true

    // o delegate e o controller precisam de referência forte até o callback (o delegate do
    // ObjC é fraco); sem isso o ARC os libera no meio do fluxo e o callback nunca chega
    private var flow: AppleFlow? = null

    actual suspend fun signIn(): AppleAuthResult {
        val rawNonce = randomNonce() ?: return AppleAuthResult.Fail("Não consegui gerar o código de segurança.")
        return suspendCancellableCoroutine { continuation ->
            val request = ASAuthorizationAppleIDProvider().createRequest()
            request.requestedScopes = listOf(ASAuthorizationScopeFullName, ASAuthorizationScopeEmail)
            request.nonce = sha256Hex(rawNonce.encodeToByteArray())

            var done = false
            val delegate = AppleFlow(rawNonce) { result ->
                flow = null
                if (!done) {
                    done = true
                    continuation.resume(result)
                }
            }
            val controller = ASAuthorizationController(authorizationRequests = listOf(request))
            controller.delegate = delegate
            controller.presentationContextProvider = delegate
            delegate.controller = controller
            flow = delegate
            continuation.invokeOnCancellation { flow = null }
            controller.performRequests()
        }
    }
}

/** 32 bytes de `SecRandomCopyBytes` em base64url — o nonce cru. Nulo se o sistema falhar. */
@OptIn(ExperimentalForeignApi::class)
private fun randomNonce(): String? {
    val bytes = ByteArray(32)
    val status = bytes.usePinned { pinned ->
        SecRandomCopyBytes(kSecRandomDefault, bytes.size.toULong(), pinned.addressOf(0))
    }
    return if (status == 0) base64UrlEncode(bytes) else null
}

@OptIn(ExperimentalForeignApi::class)
private fun NSData.asUtf8(): String? = NSString.create(data = this, encoding = NSUTF8StringEncoding) as String?

/** Recebe o resultado da janela da Apple e entrega um [AppleAuthResult]. */
private class AppleFlow(
    private val rawNonce: String,
    private val onResult: (AppleAuthResult) -> Unit
) : NSObject(),
    ASAuthorizationControllerDelegateProtocol,
    ASAuthorizationControllerPresentationContextProvidingProtocol {

    var controller: ASAuthorizationController? = null

    override fun authorizationController(
        controller: ASAuthorizationController,
        didCompleteWithAuthorization: ASAuthorization
    ) {
        val credential = didCompleteWithAuthorization.credential as? ASAuthorizationAppleIDCredential
        val idToken = credential?.identityToken?.asUtf8()
        if (credential == null || idToken.isNullOrBlank()) {
            onResult(AppleAuthResult.Fail("A Apple não devolveu um token de identidade."))
            return
        }
        onResult(
            AppleAuthResult.Ok(
                idToken = idToken,
                rawNonce = rawNonce,
                authorizationCode = credential.authorizationCode?.asUtf8(),
                givenName = credential.fullName?.givenName
            )
        )
    }

    override fun authorizationController(
        controller: ASAuthorizationController,
        didCompleteWithError: NSError
    ) {
        onResult(
            when (didCompleteWithError.code) {
                APPLE_ERROR_CANCELED -> AppleAuthResult.Cancelled
                // 1000: sem a entitlement (.ipa do Sideloadly)
                APPLE_ERROR_UNKNOWN -> AppleAuthResult.Fail(t(K.AUTH_APPLE_UNAVAILABLE))
                else -> AppleAuthResult.Fail(t(K.AUTH_APPLE_FAIL))
            }
        )
    }

    override fun presentationAnchorForAuthorizationController(
        controller: ASAuthorizationController
    ): ASPresentationAnchor {
        val windows = UIApplication.sharedApplication.windows.filterIsInstance<UIWindow>()
        return windows.firstOrNull { it.isKeyWindow() } ?: windows.firstOrNull() ?: UIWindow()
    }
}
