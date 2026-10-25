package br.com.navalbattle.data

import br.com.navalbattle.ActivityHolder
import br.com.navalbattle.game.AgeReading
import br.com.navalbattle.game.AgeRules
import com.google.android.gms.tasks.Task
import com.google.android.play.agesignals.AgeSignalsAccessRequest
import com.google.android.play.agesignals.AgeSignalsManager
import com.google.android.play.agesignals.AgeSignalsManagerFactory
import com.google.android.play.agesignals.AgeSignalsRequest
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.coroutines.resume

/**
 * Play Age Signals 0.0.4 (beta, #49). `requestAgeSignalsAccess(Activity)` pode abrir o aviso da
 * Play; com SHARED, `checkAgeSignals()` devolve a faixa. Os termos da API proíbem usar o dado em
 * publicidade, perfilamento ou analytics: aqui ele só vira [AgeReading] (que escolhe o tipo de
 * anúncio, cumprimento da lei) e as idades, o `installId` e a origem são descartados.
 * O pedido de acesso espera o jogador no aviso da Play (60 s); só `checkAgeSignals` tem 5 s.
 * Qualquer falha (loja ausente, app fora da Play como o `.preview`, rede, sem Activity, tempo
 * esgotado) vira [AgeReading.FAILED]. Sem `kotlinx-coroutines-play-services`: o [Task] é ligado
 * à corrotina à mão.
 */
actual class AgeSignals actual constructor() {
    actual val supported: Boolean get() = true

    actual suspend fun read(): AgeReading = withContext(Dispatchers.Main) {
        val activity = ActivityHolder.current ?: return@withContext AgeReading.FAILED
        runCatching { readWith(AgeFake.manager() ?: AgeSignalsManagerFactory.create(activity.applicationContext), activity) }
            .getOrDefault(AgeReading.FAILED)
    }

    private suspend fun readWith(manager: AgeSignalsManager, activity: android.app.Activity): AgeReading {
        // o pedido de acesso abre o aviso da Play e espera o jogador: limite largo, para o anúncio
        // nunca abrir por cima do aviso. Só a consulta da faixa tem os 5 s.
        val access = withTimeoutOrNull(ACCESS_TIMEOUT_MS) {
            await(manager.requestAgeSignalsAccess(AgeSignalsAccessRequest.builder().setActivity(activity).build()))
        } ?: return AgeReading.FAILED
        val status = access.ageSignalsStatus()
        if (status != AgeRules.STATUS_SHARED) return AgeRules.reading(status, null, null, error = false)
        val result = withTimeoutOrNull(CHECK_TIMEOUT_MS) { await(manager.checkAgeSignals(AgeSignalsRequest.builder().build())) }
            ?: return AgeReading.FAILED
        return AgeRules.reading(status, result.ageLower(), result.ageUpper(), error = false)
    }

    /** Resultado do [Task] ou nulo se falhou (AgeSignalsException de qualquer código, cancelamento). */
    private suspend fun <T> await(task: Task<T>): T? = suspendCancellableCoroutine { cont ->
        task.addOnSuccessListener { if (cont.isActive) cont.resume(it) }
        task.addOnFailureListener { if (cont.isActive) cont.resume(null) }
        task.addOnCanceledListener { if (cont.isActive) cont.resume(null) }
    }

    private companion object {
        const val ACCESS_TIMEOUT_MS = 60_000L
        const val CHECK_TIMEOUT_MS = 5_000L
    }
}
