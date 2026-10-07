package br.com.navalbattle.ui

import androidx.compose.runtime.Composable

/** Botão/gesto "voltar" do sistema (Android). No iPhone não há equivalente, então não faz nada. */
@Composable
expect fun SystemBackHandler(enabled: Boolean = true, onBack: () -> Unit)
