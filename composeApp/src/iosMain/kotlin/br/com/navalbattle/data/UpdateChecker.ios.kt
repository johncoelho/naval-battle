package br.com.navalbattle.data

import platform.Foundation.NSURL
import platform.UIKit.UIActivityViewController
import platform.UIKit.UIApplication

// A novidade do iPhone vem só da tabela app_releases (ios_live = a versão já está na App Store);
// consultar a Apple de dentro do app seria um serviço novo. O botão abre a página do jogo na loja.

actual suspend fun checkUpdateAvailable(): Boolean? = null

actual fun openStoreListing() {
    val (app, web) = appStoreUrls()
    val appUrl = NSURL.URLWithString(app)
    val webUrl = NSURL.URLWithString(web)
    val ui = UIApplication.sharedApplication
    if (appUrl == null) {
        webUrl?.let { ui.openURL(it, options = emptyMap<Any?, Any>(), completionHandler = null) }
        return
    }
    // sem App Store (simulador), cai no endereço web, que abre no Safari
    ui.openURL(appUrl, options = emptyMap<Any?, Any>()) { ok ->
        if (!ok && webUrl != null) ui.openURL(webUrl, options = emptyMap<Any?, Any>(), completionHandler = null)
    }
}

actual fun shareStoreListing() {}

actual fun shareText(text: String) {
    val root = UIApplication.sharedApplication.keyWindow?.rootViewController ?: return
    val sheet = UIActivityViewController(activityItems = listOf(text), applicationActivities = null)
    root.presentViewController(sheet, animated = true, completion = null)
}
