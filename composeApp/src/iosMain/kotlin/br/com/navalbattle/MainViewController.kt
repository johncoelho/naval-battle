package br.com.navalbattle

import androidx.compose.ui.window.ComposeUIViewController

/**
 * Ponto de entrada chamado pelo `iosApp` (Swift) — a mesma tela Compose do Android.
 *
 * `enforceStrictPlistSanityCheck = false`: sem isso, o próprio Compose Multiplatform
 * derruba o app de propósito (`PlistSanityCheck`) se achar o `Info.plist` fora do que
 * ele espera — foi exatamente essa checagem que causava o "abre o splash e fecha
 * sozinho" no primeiro teste real num iPhone (confirmado pelo log de crash: exceção
 * lançada dentro de `androidx.compose.ui.uikit.PlistSanityCheck`, não no nosso código).
 * Um projeto Xcode montado à mão, sem o assistente do Xcode, dificilmente bate 100%
 * com o que essa checagem espera — vários projetos Compose Multiplatform reais
 * desativam a mesma flag pelo mesmo motivo.
 */
fun MainViewController() = ComposeUIViewController(configure = { enforceStrictPlistSanityCheck = false }) { App() }
