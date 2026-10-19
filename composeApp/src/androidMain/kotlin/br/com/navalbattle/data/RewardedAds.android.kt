package br.com.navalbattle.data

import android.app.Activity
import br.com.navalbattle.ActivityHolder
import br.com.navalbattle.BuildConfig
import br.com.navalbattle.game.AdShowOutcome
import com.google.android.gms.ads.AdError
import com.google.android.gms.ads.AdRequest
import com.google.android.gms.ads.FullScreenContentCallback
import com.google.android.gms.ads.LoadAdError
import com.google.android.gms.ads.MobileAds
import com.google.android.gms.ads.rewarded.RewardedAd
import com.google.android.gms.ads.rewarded.RewardedAdLoadCallback
import com.google.android.gms.ads.rewarded.ServerSideVerificationOptions
import com.google.android.ump.ConsentInformation
import com.google.android.ump.ConsentRequestParameters
import com.google.android.ump.UserMessagingPlatform
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.coroutines.resume

/**
 * AdMob (Google Mobile Ads) com o consentimento do UMP (#29; stack aprovada pelo John em 18/10).
 * Tudo começa no primeiro toque: nada de `MobileAds.initialize` nem formulário na abertura.
 * O prêmio não sai daqui: o Google confirma no servidor (SSV) com o [customData] = vale.
 */
actual class RewardedAds actual constructor() {

    actual val supported: Boolean get() = true

    // build de debug e a prévia .preview (que é um debug com outro applicationId)
    actual val buildUsesTestAds: Boolean
        get() = BuildConfig.DEBUG || BuildConfig.APPLICATION_ID.endsWith(".preview")

    actual suspend fun show(adUnitId: String, userId: String, customData: String): AdShowOutcome =
        withContext(Dispatchers.Main) {
            val activity = ActivityHolder.current ?: return@withContext AdShowOutcome.UNAVAILABLE
            if (!consent(activity)) return@withContext AdShowOutcome.UNAVAILABLE
            startSdk(activity)
            val ad = withTimeoutOrNull(LOAD_TIMEOUT_MS) { load(activity, adUnitId) }
                ?: return@withContext AdShowOutcome.UNAVAILABLE
            ad.setServerSideVerificationOptions(
                ServerSideVerificationOptions.Builder().setUserId(userId).setCustomData(customData).build()
            )
            present(activity, ad)
        }

    actual suspend fun openPrivacyOptions(): Boolean = withContext(Dispatchers.Main) {
        val activity = ActivityHolder.current ?: return@withContext false
        val info = UserMessagingPlatform.getConsentInformation(activity)
        if (!updateConsentInfo(activity, info)) return@withContext false
        if (info.privacyOptionsRequirementStatus != ConsentInformation.PrivacyOptionsRequirementStatus.REQUIRED) {
            return@withContext false
        }
        suspendCancellableCoroutine { cont ->
            UserMessagingPlatform.showPrivacyOptionsForm(activity) { if (cont.isActive) cont.resume(true) }
        }
    }

    /** Atualiza o consentimento e mostra o formulário se a região pedir. Verdadeiro = pode pedir anúncio. */
    private suspend fun consent(activity: Activity): Boolean {
        val info = UserMessagingPlatform.getConsentInformation(activity)
        if (!updateConsentInfo(activity, info)) return info.canRequestAds()
        suspendCancellableCoroutine { cont ->
            UserMessagingPlatform.loadAndShowConsentFormIfRequired(activity) { if (cont.isActive) cont.resume(Unit) }
        }
        return info.canRequestAds()
    }

    private suspend fun updateConsentInfo(activity: Activity, info: ConsentInformation): Boolean =
        suspendCancellableCoroutine { cont ->
            info.requestConsentInfoUpdate(
                activity,
                ConsentRequestParameters.Builder().build(),
                { if (cont.isActive) cont.resume(true) },
                { if (cont.isActive) cont.resume(false) }
            )
        }

    private fun startSdk(activity: Activity) {
        if (started) return
        started = true
        MobileAds.initialize(activity.applicationContext) {}
    }

    private suspend fun load(activity: Activity, adUnitId: String): RewardedAd? =
        suspendCancellableCoroutine { cont ->
            RewardedAd.load(activity, adUnitId, AdRequest.Builder().build(), object : RewardedAdLoadCallback() {
                override fun onAdLoaded(ad: RewardedAd) {
                    if (cont.isActive) cont.resume(ad)
                }

                override fun onAdFailedToLoad(error: LoadAdError) {
                    if (cont.isActive) cont.resume(null)
                }
            })
        }

    private suspend fun present(activity: Activity, ad: RewardedAd): AdShowOutcome =
        suspendCancellableCoroutine { cont ->
            var earned = false
            ad.fullScreenContentCallback = object : FullScreenContentCallback() {
                override fun onAdDismissedFullScreenContent() {
                    if (cont.isActive) cont.resume(if (earned) AdShowOutcome.EARNED else AdShowOutcome.CLOSED_EARLY)
                }

                override fun onAdFailedToShowFullScreenContent(error: AdError) {
                    if (cont.isActive) cont.resume(AdShowOutcome.UNAVAILABLE)
                }
            }
            ad.show(activity) { earned = true }
        }

    private companion object {
        const val LOAD_TIMEOUT_MS = 20_000L
        var started = false
    }
}
