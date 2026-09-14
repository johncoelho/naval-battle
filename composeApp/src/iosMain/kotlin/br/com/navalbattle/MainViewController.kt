package br.com.navalbattle

import androidx.compose.ui.window.ComposeUIViewController

/** Ponto de entrada chamado pelo `iosApp` (Swift) — a mesma tela Compose do Android. */
fun MainViewController() = ComposeUIViewController { App() }
