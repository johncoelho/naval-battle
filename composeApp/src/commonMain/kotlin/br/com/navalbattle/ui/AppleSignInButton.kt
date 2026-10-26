package br.com.navalbattle.ui

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

/**
 * Botão oficial "Entrar com a Apple" (HIG, exigência da App Store 4.8). No iPhone é o
 * `ASAuthorizationAppleIDButton` do sistema; no Android não existe (o chamador só o mostra
 * quando `AppleAuth.supported`). Exceção registrada no design system.
 */
@Composable
expect fun AppleSignInButton(modifier: Modifier, onClick: () -> Unit)
