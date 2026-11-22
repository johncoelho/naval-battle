package br.com.navalbattle.data

import platform.Foundation.NSBundle
import platform.StoreKit.SKStoreReviewController
import platform.UIKit.UISceneActivationStateForegroundActive
import platform.UIKit.UIApplication
import platform.UIKit.UIWindowScene

/**
 * Pedido de avaliação do sistema (#136): `SKStoreReviewController`, só o diálogo oficial (sem botão próprio, sem
 * recompensa, sem filtro de satisfação; o sistema decide se mostra e limita a 3 por ano). Sem biblioteca nova.
 *
 * Dormente até a App Store: no TestFlight o diálogo não registra avaliação, então [supported] só é verdadeiro
 * quando o recibo do app é o de produção (`receipt`); no TestFlight, no simulador e nas contas de teste da loja
 * o recibo é `sandboxReceipt` (ou não existe) e o iPhone continua sem pedir, como era antes desta versão.
 */
actual object StoreReview {
    actual val supported: Boolean by lazy { NSBundle.mainBundle.appStoreReceiptURL?.lastPathComponent == "receipt" }

    actual fun request(onDone: () -> Unit) {
        try {
            val scene = UIApplication.sharedApplication.connectedScenes
                .firstOrNull { it is UIWindowScene && it.activationState == UISceneActivationStateForegroundActive } as? UIWindowScene
            if (scene != null) SKStoreReviewController.requestReviewInScene(scene)
        } catch (_: Throwable) {
            // sem cena ativa ou sem permissão do sistema: só segue
        }
        onDone()
    }
}
