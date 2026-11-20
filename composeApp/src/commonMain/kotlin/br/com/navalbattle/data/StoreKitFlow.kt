package br.com.navalbattle.data

/**
 * Item que a ponte nativa do StoreKit 2 devolve (#65). [state]: `purchased` | `cancelled` | `pending` |
 * `failed` na compra; `purchased` nas transações não finalizadas; `product` na lista de produtos.
 * [signed] é o JWS da transação (o servidor só lê o id e reconsulta a Apple); [detail] guarda o preço
 * local do produto ou a mensagem de falha.
 */
class StoreKitItem(
    val state: String,
    val productId: String,
    val transactionId: String,
    val signed: String,
    val detail: String
)

/** O que o Swift (StoreKit 2) sabe fazer; nenhuma regra de negócio passa por aqui. */
interface StoreKitPort {
    /** Produtos com o preço local da App Store (lista vazia = indisponível). */
    suspend fun products(ids: List<String>): List<StoreProduct>

    /** Abre a folha de compra com `appAccountToken = accountToken` (o UUID da conta). */
    suspend fun purchase(productId: String, accountToken: String): StoreKitItem

    /** Transações compradas e ainda não finalizadas (queda no meio, Ask to Buy aprovado depois). */
    suspend fun unfinished(): List<StoreKitItem>

    /** `transaction.finish()`: só depois de o servidor creditar. */
    suspend fun finish(transactionId: String): Boolean
}

/**
 * Fluxo de compra do iPhone (#65), puro para testar com ponte falsa. Mapeia o resultado do StoreKit para
 * [BuyOutcome] e guarda o id da transação de cada token (JWS) para finalizar depois. Quem confere e
 * credita é o servidor ([verifyThenSettle]); a transação só é finalizada com `finish=true` dele.
 */
class StoreKitFlow(private val port: StoreKitPort) {
    private val txByToken = mutableMapOf<String, String>()

    suspend fun products(ids: List<String>): List<StoreProduct> = port.products(ids)

    suspend fun buy(productId: String, accountId: String): BuyOutcome {
        val item = port.purchase(productId, accountId)
        return when (item.state) {
            "purchased" ->
                if (item.signed.isBlank() || item.transactionId.isBlank()) BuyOutcome.Failed("compra sem transação")
                else {
                    txByToken[item.signed] = item.transactionId
                    BuyOutcome.Purchased(item.productId.ifBlank { productId }, item.signed)
                }
            "cancelled" -> BuyOutcome.Cancelled
            "pending" -> BuyOutcome.Pending
            else -> BuyOutcome.Failed(item.detail.ifBlank { "App Store recusou a compra" })
        }
    }

    suspend fun unfinished(): List<PurchasedToken> =
        port.unfinished()
            .filter { it.state == "purchased" && it.signed.isNotBlank() && it.transactionId.isNotBlank() }
            .map {
                txByToken[it.signed] = it.transactionId
                PurchasedToken(it.productId, it.signed)
            }

    /** Finaliza a transação do [token] (só chamada depois do crédito no servidor). */
    suspend fun settle(token: String) {
        val id = txByToken[token] ?: return
        if (port.finish(id)) txByToken.remove(token)
    }
}

/**
 * Confere a compra no servidor e só então a finaliza: [settle] roda apenas com `finish = true` na
 * resposta (crédito feito, já creditado antes ou recusa definitiva). Falha de rede, `billing_off` e
 * qualquer erro deixam a transação aberta: a App Store a devolve e o app tenta de novo.
 */
suspend fun verifyThenSettle(
    token: String,
    verify: suspend () -> CloudResult<DobroesVerifyResult>,
    settle: suspend (String) -> Unit
): CloudResult<DobroesVerifyResult> {
    val result = verify()
    if (result is CloudResult.Ok && result.value.finish) settle(token)
    return result
}
