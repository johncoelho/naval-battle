package br.com.navalbattle

import androidx.compose.ui.window.ComposeUIViewController
import br.com.navalbattle.audio.AppForeground
import platform.Foundation.NSNotificationCenter
import platform.Foundation.NSOperationQueue
import platform.UIKit.UIApplicationDidBecomeActiveNotification
import platform.UIKit.UIApplicationWillResignActiveNotification

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
    .also { watchForeground() }

/**
 * Primeiro/segundo plano no iPhone, o equivalente ao `onResume`/`onPause` do Android:
 * sem isso [AppForeground.active] ficava sempre verdadeiro e nada que depende da volta
 * ao app rodava de novo (temporada, badges, feedback, Diário de bordo, versão nova).
 */
private var foregroundWatched = false

private fun watchForeground() {
    if (foregroundWatched) return
    foregroundWatched = true
    val center = NSNotificationCenter.defaultCenter
    center.addObserverForName(UIApplicationDidBecomeActiveNotification, null, NSOperationQueue.mainQueue) { _ ->
        AppForeground.active = true
    }
    center.addObserverForName(UIApplicationWillResignActiveNotification, null, NSOperationQueue.mainQueue) { _ ->
        AppForeground.active = false
    }
}
