package br.com.navalbattle.data

import android.app.Activity
import android.os.Build
import android.os.Bundle
import br.com.navalbattle.ActivityHolder
import br.com.navalbattle.BuildConfig
import br.com.navalbattle.game.AdAudience
import br.com.navalbattle.game.AdRating
import br.com.navalbattle.game.AdRules
import br.com.navalbattle.game.AdShowOutcome
import com.google.ads.mediation.admob.AdMobAdapter
import com.google.android.gms.ads.AdError
import com.google.android.gms.ads.AdRequest
import com.google.android.gms.ads.FullScreenContentCallback
import com.google.android.gms.ads.LoadAdError
import com.google.android.gms.ads.MobileAds
import com.google.android.gms.ads.RequestConfiguration
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

    actual val runsOnEmulator: Boolean
        get() = AdRules.isEmulatorBuild(
            fingerprint = Build.FINGERPRINT,
            model = Build.MODEL,
            hardware = Build.HARDWARE,
            product = Build.PRODUCT,
            manufacturer = Build.MANUFACTURER
        )

    actual suspend fun show(adUnitId: String, userId: String, customData: String, audience: AdAudience): AdShowOutcome =
        withContext(Dispatchers.Main) {
            val activity = ActivityHolder.current ?: return@withContext AdShowOutcome.UNAVAILABLE
            // a marca de idade vale antes de tudo: UMP, inicialização do SDK e cada carregamento
            applyAudience(audience)
            if (!consent(activity, audience)) return@withContext AdShowOutcome.UNAVAILABLE
            applyAudience(audience)
            startSdk(activity)
            val ad = withTimeoutOrNull(LOAD_TIMEOUT_MS) { load(activity, adUnitId, audience) }
                ?: return@withContext AdShowOutcome.UNAVAILABLE
            ad.setServerSideVerificationOptions(
                ServerSideVerificationOptions.Builder().setUserId(userId).setCustomData(customData).build()
            )
            present(activity, ad)
        }

    actual suspend fun openPrivacyOptions(audience: AdAudience): Boolean = withContext(Dispatchers.Main) {
        val activity = ActivityHolder.current ?: return@withContext false
        val info = UserMessagingPlatform.getConsentInformation(activity)
        if (!updateConsentInfo(activity, info, audience)) return@withContext false
        if (info.privacyOptionsRequirementStatus != ConsentInformation.PrivacyOptionsRequirementStatus.REQUIRED) {
            return@withContext false
        }
        suspendCancellableCoroutine { cont ->
            UserMessagingPlatform.showPrivacyOptionsForm(activity) { if (cont.isActive) cont.resume(true) }
        }
    }

    /** Atualiza o consentimento e mostra o formulário se a região pedir. Verdadeiro = pode pedir anúncio. */
    private suspend fun consent(activity: Activity, audience: AdAudience): Boolean {
        val info = UserMessagingPlatform.getConsentInformation(activity)
        if (!updateConsentInfo(activity, info, audience)) return info.canRequestAds()
        suspendCancellableCoroutine { cont ->
            UserMessagingPlatform.loadAndShowConsentFormIfRequired(activity) { if (cont.isActive) cont.resume(Unit) }
        }
        return info.canRequestAds()
    }

    private suspend fun updateConsentInfo(activity: Activity, info: ConsentInformation, audience: AdAudience): Boolean =
        suspendCancellableCoroutine { cont ->
            info.requestConsentInfoUpdate(
                activity,
                // menor: o UMP não mostra formulário (mesma marca do pedido de anúncio, #49)
                ConsentRequestParameters.Builder().setTagForUnderAgeOfConsent(audience.underAgeOfConsent).build(),
                { if (cont.isActive) cont.resume(true) },
                { if (cont.isActive) cont.resume(false) }
            )
        }

    private fun startSdk(activity: Activity) {
        if (started) return
        started = true
        MobileAds.initialize(activity.applicationContext) {}
    }

    /**
     * TFUA, TFCD e classificação máxima (#49). A configuração do SDK é global e fica de um
     * anúncio para o outro, então adulto volta explicitamente aos valores sem marca.
     */
    private fun applyAudience(audience: AdAudience) {
        MobileAds.setRequestConfiguration(
            RequestConfiguration.Builder()
                .setTagForUnderAgeOfConsent(
                    if (audience.underAgeOfConsent) RequestConfiguration.TAG_FOR_UNDER_AGE_OF_CONSENT_TRUE
                    else RequestConfiguration.TAG_FOR_UNDER_AGE_OF_CONSENT_UNSPECIFIED
                )
                .setTagForChildDirectedTreatment(
                    if (audience.childDirected) RequestConfiguration.TAG_FOR_CHILD_DIRECTED_TREATMENT_TRUE
                    else RequestConfiguration.TAG_FOR_CHILD_DIRECTED_TREATMENT_UNSPECIFIED
                )
                .setMaxAdContentRating(
                    when (audience.maxRating) {
                        AdRating.G -> RequestConfiguration.MAX_AD_CONTENT_RATING_G
                        AdRating.PG -> RequestConfiguration.MAX_AD_CONTENT_RATING_PG
                        AdRating.NONE -> RequestConfiguration.MAX_AD_CONTENT_RATING_UNSPECIFIED
                    }
                )
                .build()
        )
    }

    /** Não personalizado: `npa=1` (no Brasil o UMP não tem formulário que desligue a personalização). */
    private fun buildRequest(audience: AdAudience): AdRequest {
        val builder = AdRequest.Builder()
        if (!audience.personalized) {
            builder.addNetworkExtrasBundle(AdMobAdapter::class.java, Bundle().apply { putString("npa", "1") })
        }
        return builder.build()
    }

    private suspend fun load(activity: Activity, adUnitId: String, audience: AdAudience): RewardedAd? =
        suspendCancellableCoroutine { cont ->
            applyAudience(audience)
            RewardedAd.load(activity, adUnitId, buildRequest(audience), object : RewardedAdLoadCallback() {
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
