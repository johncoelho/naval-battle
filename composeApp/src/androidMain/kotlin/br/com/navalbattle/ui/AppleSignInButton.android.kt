package br.com.navalbattle.ui

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

/** Android não tem Entrar com a Apple: nada é desenhado. */
@Composable
actual fun AppleSignInButton(modifier: Modifier, onClick: () -> Unit) {}
