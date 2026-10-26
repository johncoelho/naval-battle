package br.com.navalbattle.data

import br.com.navalbattle.ActivityHolder
import br.com.navalbattle.BuildConfig
import com.google.android.play.core.review.ReviewManager
import com.google.android.play.core.review.ReviewManagerFactory
import com.google.android.play.core.review.testing.FakeReviewManager

/**
 * Play In-App Review 2.0.2 (#45). `requestReviewFlow` e `launchReviewFlow` são tarefas
 * assíncronas; qualquer falha (sem Activity, sem Play, fora da cota, app fora da loja) só chama
 * [onDone]. `-PreviewFake=true` (debug/.preview) troca pelo `FakeReviewManager`; o release
 * fixa `REVIEW_FAKE = false`.
 */
actual object StoreReview {
    actual val supported: Boolean get() = true

    actual fun request(onDone: () -> Unit) {
        var finished = false
        val done = { if (!finished) { finished = true; onDone() } }
        try {
            val activity = ActivityHolder.current ?: return done()
            val context = activity.applicationContext
            val manager: ReviewManager =
                if (BuildConfig.REVIEW_FAKE) FakeReviewManager(context) else ReviewManagerFactory.create(context)
            manager.requestReviewFlow().addOnCompleteListener { task ->
                if (!task.isSuccessful) return@addOnCompleteListener done()
                try {
                    manager.launchReviewFlow(activity, task.result).addOnCompleteListener { done() }
                } catch (_: Throwable) {
                    done()
                }
            }
        } catch (_: Throwable) {
            done()
        }
    }
}
