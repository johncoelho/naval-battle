package br.com.navalbattle.data

import br.com.navalbattle.BuildConfig
import com.google.android.play.agesignals.AgeSignalsAccessResult
import com.google.android.play.agesignals.AgeSignalsManager
import com.google.android.play.agesignals.AgeSignalsResult
import com.google.android.play.agesignals.model.AgeSignalsStatus
import com.google.android.play.agesignals.testing.FakeAgeSignalsManager

/**
 * Faixa etária falsa para o QA (#49), com o `FakeAgeSignalsManager` da própria lib. Só liga com
 * `-PageFake=<child|teen|adult|unknown>` num build de debug ou `.preview`; no release o
 * `AGE_FAKE` é sempre vazio e isto devolve nulo (o manager real).
 */
internal object AgeFake {
    fun manager(): AgeSignalsManager? {
        val mode = BuildConfig.AGE_FAKE.lowercase()
        if (mode.isEmpty()) return null
        val (status, lower, upper) = when (mode) {
            "child" -> Triple(AgeSignalsStatus.SHARED, 0, 12)
            "teen" -> Triple(AgeSignalsStatus.SHARED, 13, 15)
            "adult" -> Triple(AgeSignalsStatus.SHARED, 18, null)
            else -> Triple(AgeSignalsStatus.NOT_SHARED, null, null)
        }
        return FakeAgeSignalsManager().apply {
            setNextAgeSignalsAccessResult(AgeSignalsAccessResult.builder().setAgeSignalsStatus(status).build())
            setNextAgeSignalsResult(AgeSignalsResult.builder().setAgeLower(lower).setAgeUpper(upper).build())
        }
    }
}
