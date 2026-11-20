package br.com.navalbattle.data

import kotlinx.coroutines.CancellableContinuation
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.coroutines.resume

/**
 * O que o Swift (`iosApp/iosApp/StoreKitBridge.swift`) implementa com StoreKit 2 (#65). Cada pedido
 * leva um [requestId]; a resposta volta por [StoreKitBridge.onItem] (zero ou mais) e termina com
 * [StoreKitBridge.onDone]. Sem closures: o Kotlin exporta lambda como bloco que devolve KotlinUnit.
 */
interface StoreKitNative {
    fun requestProducts(requestId: String, ids: List<String>)
    fun requestPurchase(requestId: String, productId: String, accountToken: String)
    fun requestUnfinished(requestId: String)
    fun requestFinish(requestId: String, transactionId: String)
}

/**
 * Ponte entre o Swift e o Kotlin, no padrão do [PushBridge]: o `AppDelegate` registra [native] ao abrir
 * e o Swift responde pelos callbacks (sempre na thread principal). [NativeStoreKitPort] transforma isso em
 * `suspend` para o [StoreKitFlow].
 */
object StoreKitBridge {
    var native: StoreKitNative? = null

    private class Waiting(val cont: CancellableContinuation<List<StoreKitItem>?>) {
        val items = mutableListOf<StoreKitItem>()
    }

    private val waiting = mutableMapOf<String, Waiting>()
    private var seq = 0

    fun onItem(requestId: String, state: String, productId: String, transactionId: String, signed: String, detail: String) {
        waiting[requestId]?.items?.add(StoreKitItem(state, productId, transactionId, signed, detail))
    }

    fun onDone(requestId: String, ok: Boolean) {
        val w = waiting.remove(requestId) ?: return
        if (w.cont.isActive) w.cont.resume(if (ok) w.items else null)
    }

    /** Null = a ponte falhou ou não respondeu (sem Swift registrado, erro do StoreKit, tempo esgotado). */
    internal suspend fun request(timeoutMs: Long?, start: (StoreKitNative, String) -> Unit): List<StoreKitItem>? {
        val n = native ?: return null
        val id = "sk${++seq}"
        val run: suspend () -> List<StoreKitItem>? = {
            suspendCancellableCoroutine { cont ->
                waiting[id] = Waiting(cont)
                cont.invokeOnCancellation { waiting.remove(id) }
                start(n, id)
            }
        }
        return if (timeoutMs == null) run() else withTimeoutOrNull(timeoutMs) { run() }
    }
}

internal object NativeStoreKitPort : StoreKitPort {
    private const val QUICK_MS = 20_000L

    override suspend fun products(ids: List<String>): List<StoreProduct> =
        StoreKitBridge.request(QUICK_MS) { n, id -> n.requestProducts(id, ids) }.orEmpty()
            .filter { it.state == "product" }
            .map { StoreProduct(it.productId, it.detail) }

    // a folha de compra espera o jogador (Face ID, senha): sem tempo limite
    override suspend fun purchase(productId: String, accountToken: String): StoreKitItem =
        StoreKitBridge.request(null) { n, id -> n.requestPurchase(id, productId, accountToken) }?.firstOrNull()
            ?: StoreKitItem("failed", productId, "", "", "ponte StoreKit sem resposta")

    override suspend fun unfinished(): List<StoreKitItem> =
        StoreKitBridge.request(QUICK_MS) { n, id -> n.requestUnfinished(id) }.orEmpty()

    override suspend fun finish(transactionId: String): Boolean =
        StoreKitBridge.request(QUICK_MS) { n, id -> n.requestFinish(id, transactionId) } != null
}
