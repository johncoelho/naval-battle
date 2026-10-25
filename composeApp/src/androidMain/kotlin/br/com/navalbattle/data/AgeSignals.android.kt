package br.com.navalbattle.data

import br.com.navalbattle.ActivityHolder
import br.com.navalbattle.game.AgeBand
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
 * publicidade, perfilamento ou analytics: aqui ele só vira [AgeBand] (que escolhe o tipo de
 * anúncio, cumprimento da lei) e as idades, o `installId` e a origem são descartados.
 * Qualquer falha (loja ausente, app fora da Play como o `.preview`, rede, sem Activity, espera
 * de 5 s) cai em [AgeBand.UNKNOWN]. Sem `kotlinx-coroutines-play-services`: o [Task] é ligado à
 * corrotina à mão.
 */
actual class AgeSignals actual constructor() {
    actual val supported: Boolean get() = true

    actual suspend fun read(): AgeBand = withContext(Dispatchers.Main) {
        val activity = ActivityHolder.current ?: return@withContext AgeBand.UNKNOWN
        runCatching {
            withTimeoutOrNull(TIMEOUT_MS) {
                val manager = AgeFake.manager() ?: AgeSignalsManagerFactory.create(activity.applicationContext)
                readWith(manager, activity)
            } ?: AgeBand.UNKNOWN
        }.getOrDefault(AgeBand.UNKNOWN)
    }

    private suspend fun readWith(manager: AgeSignalsManager, activity: android.app.Activity): AgeBand {
        val access = await(
            manager.requestAgeSignalsAccess(AgeSignalsAccessRequest.builder().setActivity(activity).build())
        ) ?: return AgeBand.UNKNOWN
        val status = access.ageSignalsStatus()
        if (status != AgeRules.STATUS_SHARED) return AgeRules.band(status, null, null, error = false)
        val result = await(manager.checkAgeSignals(AgeSignalsRequest.builder().build()))
            ?: return AgeBand.UNKNOWN
        return AgeRules.band(status, result.ageLower(), result.ageUpper(), error = false)
    }

    /** Resultado do [Task] ou nulo se falhou (AgeSignalsException de qualquer código, cancelamento). */
    private suspend fun <T> await(task: Task<T>): T? = suspendCancellableCoroutine { cont ->
        task.addOnSuccessListener { if (cont.isActive) cont.resume(it) }
        task.addOnFailureListener { if (cont.isActive) cont.resume(null) }
        task.addOnCanceledListener { if (cont.isActive) cont.resume(null) }
    }

    private companion object {
        const val TIMEOUT_MS = 5_000L
    }
}
