package br.com.navalbattle.data

/**
 * iPhone: sem loja de dobrões ainda (StoreKit é issue própria, #15 parte h). A aba
 * Dobrões fica fechada: nenhum produto, nenhuma compra.
 */
actual class StoreBilling actual constructor() {
    actual val available: Boolean get() = false

    actual suspend fun products(): List<StoreProduct> = emptyList()

    actual suspend fun buy(productId: String, accountId: String): BuyOutcome =
        BuyOutcome.Failed("loja indisponível no iPhone")

    actual suspend fun purchasedTokens(): List<PurchasedToken> = emptyList()
}
