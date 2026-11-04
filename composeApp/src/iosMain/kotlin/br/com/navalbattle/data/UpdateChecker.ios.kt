package br.com.navalbattle.data

import platform.Foundation.NSURL
import platform.UIKit.UIActivityViewController
import platform.UIKit.UIApplication

// O iPhone é atualizado pelo TestFlight: não há loja para perguntar, então a novidade
// vem só da tabela app_releases e o botão leva à página do jogo (seção do iPhone).
private const val IOS_DOWNLOAD_URL = "https://johncoelho.github.io/naval-battle/#ios"

actual suspend fun checkUpdateAvailable(): Boolean? = null

actual fun openStoreListing() {
    val url = NSURL.URLWithString(IOS_DOWNLOAD_URL) ?: return
    UIApplication.sharedApplication.openURL(url, options = emptyMap<Any?, Any>(), completionHandler = null)
}

actual fun shareStoreListing() {}

actual fun shareText(text: String) {
    val root = UIApplication.sharedApplication.keyWindow?.rootViewController ?: return
    val sheet = UIActivityViewController(activityItems = listOf(text), applicationActivities = null)
    root.presentViewController(sheet, animated = true, completion = null)
}
