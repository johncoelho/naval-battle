package br.com.navalbattle.data

import platform.Foundation.NSURL
import platform.UIKit.UIApplication

// O iPhone instala o .ipa pelo site (sem App Store ainda): não há loja para perguntar,
// então a novidade vem só da tabela app_releases e o botão leva à página do jogo.
private const val IOS_DOWNLOAD_URL = "https://johncoelho.github.io/naval-battle/#ios"

actual suspend fun checkUpdateAvailable(): Boolean? = null

actual fun openStoreListing() {
    val url = NSURL.URLWithString(IOS_DOWNLOAD_URL) ?: return
    UIApplication.sharedApplication.openURL(url, options = emptyMap<Any?, Any>(), completionHandler = null)
}

actual fun shareStoreListing() {}
