package br.com.navalbattle.data

import android.content.Context
import br.com.navalbattle.ActivityHolder
import br.com.navalbattle.audio.AudioContextHolder
import br.com.navalbattle.game.DoubloonPack
import com.android.billingclient.api.BillingClient
import com.android.billingclient.api.BillingClientStateListener
import com.android.billingclient.api.BillingFlowParams
import com.android.billingclient.api.BillingResult
import com.android.billingclient.api.PendingPurchasesParams
import com.android.billingclient.api.ProductDetails
import com.android.billingclient.api.Purchase
import com.android.billingclient.api.QueryProductDetailsParams
import com.android.billingclient.api.QueryPurchasesParams
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume

/**
 * Google Play Billing (#15). Só lista produtos, abre a folha de compra e lê o que já
 * está comprado. Consumir e creditar é com o servidor (Edge `dobroes-verify`).
 */
actual class StoreBilling actual constructor() {

    actual val available: Boolean get() = true

    private var client: BillingClient? = null
    private var pendingBuy: CompletableDeferred<BuyOutcome>? = null

    // resposta da folha de compra: chega depois de launchBillingFlow, na thread principal
    private val listener = com.android.billingclient.api.PurchasesUpdatedListener { result, purchases ->
        val waiting = pendingBuy ?: return@PurchasesUpdatedListener
        pendingBuy = null
        val outcome = when (result.responseCode) {
            BillingClient.BillingResponseCode.OK -> {
                val purchase = purchases?.firstOrNull()
                when {
                    purchase == null -> BuyOutcome.Failed("sem compra na resposta da Play")
                    purchase.purchaseState == Purchase.PurchaseState.PURCHASED ->
                        BuyOutcome.Purchased(purchase.products.first(), purchase.purchaseToken)
                    purchase.purchaseState == Purchase.PurchaseState.PENDING -> BuyOutcome.Pending
                    else -> BuyOutcome.Failed("estado de compra desconhecido")
                }
            }
            BillingClient.BillingResponseCode.USER_CANCELED -> BuyOutcome.Cancelled
            else -> BuyOutcome.Failed("Play recusou a compra (${result.responseCode})")
        }
        waiting.complete(outcome)
    }

    /** Cliente conectado, ou nulo quando não há contexto ou a Play não responde. */
    private suspend fun ready(): BillingClient? {
        val context: Context = ActivityHolder.current?.applicationContext
            ?: runCatching { AudioContextHolder.appContext.applicationContext }.getOrNull()
            ?: return null
        val c = client ?: BillingClient.newBuilder(context)
            .setListener(listener)
            .enablePendingPurchases(PendingPurchasesParams.newBuilder().enableOneTimeProducts().build())
            .build()
            .also { client = it }
        if (c.isReady) return c
        return suspendCancellableCoroutine { cont ->
            c.startConnection(object : BillingClientStateListener {
                override fun onBillingSetupFinished(result: BillingResult) {
                    if (cont.isActive) {
                        cont.resume(if (result.responseCode == BillingClient.BillingResponseCode.OK) c else null)
                    }
                }

                // a próxima chamada reconecta
                override fun onBillingServiceDisconnected() {}
            })
        }
    }

    private suspend fun detailsOf(c: BillingClient, ids: List<String>): List<ProductDetails> =
        suspendCancellableCoroutine { cont ->
            val params = QueryProductDetailsParams.newBuilder()
                .setProductList(
                    ids.map {
                        QueryProductDetailsParams.Product.newBuilder()
                            .setProductId(it)
                            .setProductType(BillingClient.ProductType.INAPP)
                            .build()
                    }
                )
                .build()
            c.queryProductDetailsAsync(params) { result, queryResult ->
                val list = if (result.responseCode == BillingClient.BillingResponseCode.OK) {
                    queryResult.productDetailsList
                } else {
                    emptyList()
                }
                if (cont.isActive) cont.resume(list)
            }
        }

    actual suspend fun products(): List<StoreProduct> {
        val c = ready() ?: return emptyList()
        return detailsOf(c, DoubloonPack.entries.map { it.code }).mapNotNull { d ->
            d.oneTimePurchaseOfferDetails?.let { StoreProduct(d.productId, it.formattedPrice) }
        }
    }

    actual suspend fun buy(productId: String, accountId: String): BuyOutcome {
        val activity = ActivityHolder.current ?: return BuyOutcome.Failed("sem tela para abrir a compra")
        val c = ready() ?: return BuyOutcome.Failed("loja indisponível")
        val details = detailsOf(c, listOf(productId)).firstOrNull()
            ?: return BuyOutcome.Failed("produto não cadastrado na Play")
        val flow = BillingFlowParams.newBuilder()
            .setProductDetailsParamsList(
                listOf(BillingFlowParams.ProductDetailsParams.newBuilder().setProductDetails(details).build())
            )
            .setObfuscatedAccountId(accountId)
            .build()
        val answer = CompletableDeferred<BuyOutcome>()
        pendingBuy?.complete(BuyOutcome.Cancelled)
        pendingBuy = answer
        val launched = c.launchBillingFlow(activity, flow)
        if (launched.responseCode != BillingClient.BillingResponseCode.OK) {
            pendingBuy = null
            return if (launched.responseCode == BillingClient.BillingResponseCode.USER_CANCELED) {
                BuyOutcome.Cancelled
            } else {
                // já comprado e não consumido: o recibo volta por purchasedTokens()
                BuyOutcome.Failed("Play não abriu a compra (${launched.responseCode})")
            }
        }
        return answer.await()
    }

    actual suspend fun purchasedTokens(): List<PurchasedToken> {
        val c = ready() ?: return emptyList()
        val params = QueryPurchasesParams.newBuilder().setProductType(BillingClient.ProductType.INAPP).build()
        return suspendCancellableCoroutine { cont ->
            c.queryPurchasesAsync(params) { result, purchases ->
                val list = if (result.responseCode == BillingClient.BillingResponseCode.OK) {
                    purchases
                        .filter { it.purchaseState == Purchase.PurchaseState.PURCHASED }
                        .flatMap { p -> p.products.map { PurchasedToken(it, p.purchaseToken) } }
                } else {
                    emptyList()
                }
                if (cont.isActive) cont.resume(list)
            }
        }
    }
}
