package br.com.navalbattle.data

import br.com.navalbattle.game.DoubloonPack

/**
 * iPhone: compra de dobrões pelo StoreKit 2 (#65), por uma ponte Swift ([StoreKitBridge]). O aparelho só
 * lista produtos, abre a folha e lê o que ficou sem finalizar; quem credita é o servidor
 * (`dobroes-verify-ios`) e a transação só é finalizada depois ([settle]). [available] só é verdadeiro com a
 * ponte registrada; a vitrine só abre também com `real_billing_ios_enabled` ligada no servidor.
 */
actual class StoreBilling actual constructor() {
    private val flow = StoreKitFlow(NativeStoreKitPort)

    actual val available: Boolean get() = StoreKitBridge.native != null

    actual suspend fun products(): List<StoreProduct> = flow.products(DoubloonPack.entries.map { it.code })

    actual suspend fun buy(productId: String, accountId: String): BuyOutcome = flow.buy(productId, accountId)

    actual suspend fun purchasedTokens(): List<PurchasedToken> = flow.unfinished()

    actual suspend fun settle(token: String) = flow.settle(token)
}
