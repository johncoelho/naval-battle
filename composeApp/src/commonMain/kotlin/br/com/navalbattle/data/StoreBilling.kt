package br.com.navalbattle.data

/** Produto vendido na Google Play: o preço é o que a própria Play devolve (já formatado). */
data class StoreProduct(val productId: String, val formattedPrice: String)

/** Compra que a Play já confirmou (estado comprada), ainda sem crédito no servidor. */
data class PurchasedToken(val productId: String, val token: String)

/** Resultado de uma tentativa de compra na folha da Play. */
sealed class BuyOutcome {
    /** Comprada: o servidor confere o [token] e credita. */
    data class Purchased(val productId: String, val token: String) : BuyOutcome()

    /** Fechou a folha da Play sem comprar: nada muda e nenhum erro aparece. */
    data object Cancelled : BuyOutcome()

    /** Pagamento pendente (PIX, boleto): a Play avisa quando confirmar; não credita agora. */
    data object Pending : BuyOutcome()

    /** Não foi possível abrir ou concluir a compra (sem Play, sem produto, erro da Play). */
    data class Failed(val reason: String) : BuyOutcome()
}

/**
 * Compras de dobrões pela Google Play (#15). O aparelho só fala com a Play (lista
 * produtos, abre a folha, lista o que está comprado); quem decide o crédito é o
 * servidor (`dobroes-verify`). Implementação real só no Android; no iPhone a loja
 * fica fechada até a parte StoreKit (issue própria).
 */
expect class StoreBilling() {
    /** Falso onde não há loja de compras (iPhone): a aba Dobrões fica fechada. */
    val available: Boolean

    /** Produtos ativos na Play (lista vazia = indisponível). */
    suspend fun products(): List<StoreProduct>

    /**
     * Abre a folha de compra do [productId]. [accountId] (id do comandante) vai como
     * obfuscatedAccountId: a compra fica amarrada à conta.
     */
    suspend fun buy(productId: String, accountId: String): BuyOutcome

    /** Compras já confirmadas pela Play que ainda não foram conferidas no servidor. */
    suspend fun purchasedTokens(): List<PurchasedToken>
}
