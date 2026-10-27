package br.com.navalbattle.ui

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.UIKitView
import kotlinx.cinterop.ExperimentalForeignApi
import platform.AuthenticationServices.ASAuthorizationAppleIDButton
import platform.AuthenticationServices.ASAuthorizationAppleIDButtonStyle
import platform.AuthenticationServices.ASAuthorizationAppleIDButtonTypeSignIn
import platform.UIKit.UIControlEventTouchUpInside
import platform.darwin.NSObject
import platform.objc.sel_registerName
import kotlinx.cinterop.BetaInteropApi
import kotlinx.cinterop.ObjCAction

/** Alvo do toque: o botão chama `fire` pelo seletor. */
private class AppleButtonTarget(private val onTap: () -> Unit) : NSObject() {
    @OptIn(BetaInteropApi::class)
    @ObjCAction
    fun fire() = onTap()
}

/** Botão oficial da Apple, tipo Sign in, estilo White (fundo noturno), 48 dp e largura cheia. */
@OptIn(ExperimentalForeignApi::class)
@Composable
actual fun AppleSignInButton(modifier: Modifier, onClick: () -> Unit) {
    val latest = rememberUpdatedState(onClick)
    val target = androidx.compose.runtime.remember { AppleButtonTarget { latest.value() } }
    UIKitView(
        factory = {
            val button = ASAuthorizationAppleIDButton(
                authorizationButtonType = ASAuthorizationAppleIDButtonTypeSignIn,
                authorizationButtonStyle = ASAuthorizationAppleIDButtonStyle.ASAuthorizationAppleIDButtonStyleWhite
            )
            button.cornerRadius = 0.0
            button.addTarget(target, sel_registerName("fire"), UIControlEventTouchUpInside)
            button
        },
        modifier = modifier.fillMaxWidth().height(48.dp)
    )
}
