package com.blueorbit.ruler.core.ads

import android.app.Activity
import android.content.Context
import android.os.SystemClock
import android.util.Log
import com.blueorbit.ruler.BuildConfig
import com.google.android.gms.ads.AdError
import com.google.android.gms.ads.AdRequest
import com.google.android.gms.ads.FullScreenContentCallback
import com.google.android.gms.ads.LoadAdError
import com.google.android.gms.ads.MobileAds
import com.google.android.gms.ads.interstitial.InterstitialAd
import com.google.android.gms.ads.interstitial.InterstitialAdLoadCallback
import com.google.android.ump.ConsentInformation
import com.google.android.ump.ConsentRequestParameters
import com.google.android.ump.UserMessagingPlatform
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.util.concurrent.atomic.AtomicBoolean

/** The app's one ad, behind an interface so screens and tests never touch the SDK. */
interface Ads {
    /** True when the law requires a "Privacy choices" entry (EEA/UK/US states); Settings shows it. */
    val privacyOptionsRequired: StateFlow<Boolean>

    /** Gathers consent (the form only appears where required) and, if ads are allowed, preloads one. */
    fun start(activity: Activity)

    /** A natural break (a tab switch). Shows the interstitial only if [AdPolicy] allows it. */
    fun onNaturalBreak(activity: Activity)

    /** Opens Google's consent form again so the user can change their choice. */
    fun showPrivacyOptions(activity: Activity)
}

/** For Pro-only builds and tests. */
object NoAds : Ads {
    override val privacyOptionsRequired: StateFlow<Boolean> = MutableStateFlow(false)
    override fun start(activity: Activity) {}
    override fun onNaturalBreak(activity: Activity) {}
    override fun showPrivacyOptions(activity: Activity) {}
}

/**
 * AdMob interstitial plus Google's User Messaging Platform for consent.
 *
 * Order matters and follows Google's integration guide: consent information is refreshed on every
 * start, the form is shown only where required, and the Mobile Ads SDK is initialised and asked for an
 * ad only once `canRequestAds()` is true. Pro users never reach [start] (the caller checks), so they
 * see neither the consent form nor an ad.
 *
 * The ad unit comes from `BuildConfig.ADMOB_INTERSTITIAL_ID`; until the owner sets real IDs the build
 * uses Google's published test IDs (see app/build.gradle.kts and HUMAN_CHECKS.md).
 */
class AdMobAds(
    private val context: Context,
    private val isPro: () -> Boolean,
    private val isFirstSession: () -> Boolean,
    private val clock: () -> Long = { SystemClock.elapsedRealtime() }
) : Ads {

    private val consent: ConsentInformation = UserMessagingPlatform.getConsentInformation(context)
    private val sdkStarted = AtomicBoolean(false)
    private val scope = CoroutineScope(Dispatchers.Main)
    private val launchedAt = clock()

    private var interstitial: InterstitialAd? = null
    private var loading = false
    private var lastShownAt: Long? = null

    private val _privacyOptionsRequired = MutableStateFlow(false)
    override val privacyOptionsRequired: StateFlow<Boolean> = _privacyOptionsRequired.asStateFlow()

    override fun start(activity: Activity) {
        if (isPro() || isFirstSession()) return
        consent.requestConsentInfoUpdate(
            activity,
            ConsentRequestParameters.Builder().build(),
            {
                UserMessagingPlatform.loadAndShowConsentFormIfRequired(activity) { formError ->
                    if (formError != null) Log.w(TAG, "consent form: ${formError.message}")
                    _privacyOptionsRequired.value = consent.privacyOptionsRequirementStatus ==
                        ConsentInformation.PrivacyOptionsRequirementStatus.REQUIRED
                    startSdkIfAllowed()
                }
            },
            { error -> Log.w(TAG, "consent info: ${error.message}") }
        )
        // Consent from a previous session already allows ads: start without waiting for the update.
        startSdkIfAllowed()
    }

    private fun startSdkIfAllowed() {
        if (!consent.canRequestAds() || isPro()) return
        if (sdkStarted.getAndSet(true)) {
            preload()
            return
        }
        // Google recommends initialising off the main thread; the ad request itself goes back to main.
        CoroutineScope(Dispatchers.IO).launch {
            MobileAds.initialize(context) {}
            scope.launch { preload() }
        }
    }

    private fun preload() {
        if (interstitial != null || loading || isPro()) return
        loading = true
        InterstitialAd.load(
            context,
            BuildConfig.ADMOB_INTERSTITIAL_ID,
            AdRequest.Builder().build(),
            object : InterstitialAdLoadCallback() {
                override fun onAdLoaded(ad: InterstitialAd) {
                    loading = false
                    interstitial = ad
                }

                override fun onAdFailedToLoad(error: LoadAdError) {
                    loading = false
                    Log.w(TAG, "interstitial load failed: ${error.code} ${error.message}")
                }
            }
        )
    }

    override fun onNaturalBreak(activity: Activity) {
        val ad = interstitial
        val allowed = AdPolicy.mayShow(
            nowMs = clock(),
            launchedAtMs = launchedAt,
            lastShownAtMs = lastShownAt,
            firstSession = isFirstSession(),
            pro = isPro(),
            canRequestAds = consent.canRequestAds(),
            adLoaded = ad != null
        )
        if (!allowed || ad == null) return
        ad.fullScreenContentCallback = object : FullScreenContentCallback() {
            override fun onAdDismissedFullScreenContent() {
                interstitial = null
                preload()
            }

            override fun onAdFailedToShowFullScreenContent(error: AdError) {
                interstitial = null
                preload()
            }
        }
        lastShownAt = clock()
        interstitial = null
        ad.show(activity)
    }

    override fun showPrivacyOptions(activity: Activity) {
        UserMessagingPlatform.showPrivacyOptionsForm(activity) { error ->
            if (error != null) Log.w(TAG, "privacy options: ${error.message}")
            startSdkIfAllowed()
        }
    }

    private companion object {
        const val TAG = "RulerAds"
    }
}
