package com.fitcal.app.ads

import android.app.Activity
import android.content.Context
import android.util.Log
import com.fitcal.app.telemetry.AdRevenuePayload
import com.fitcal.app.telemetry.AdTelemetryManager
import com.fitcal.app.telemetry.DiagnosticsCrashHook
import com.fitcal.shared.telemetry.TelemetryUploader
import com.google.android.gms.ads.AdError
import com.google.android.gms.ads.AdValue
import com.google.android.gms.ads.FullScreenContentCallback
import com.google.android.gms.ads.LoadAdError
import com.google.android.gms.ads.ResponseInfo
import com.google.android.gms.ads.interstitial.InterstitialAd
import com.google.android.gms.ads.interstitial.InterstitialAdLoadCallback
import com.google.android.gms.ads.rewarded.RewardedAd
import com.google.android.gms.ads.rewarded.RewardedAdLoadCallback
import com.google.android.gms.ads.rewarded.ServerSideVerificationOptions
import java.lang.ref.WeakReference
import kotlinx.coroutines.flow.MutableStateFlow

class AndroidAdManager(
    /**
     * The signed-in Supabase user (guests included). Sent to AdMob with each rewarded view so
     * its server-side verification callback (GET /v1/reward/ssv) can credit the right user.
     * SupabaseAuthService keeps TelemetryUploader.userIdProvider on the current user.
     * Declared first so `AndroidAdManager { context }` still binds the trailing lambda to
     * contextProvider.
     */
    private val userIdProvider: () -> String? = { TelemetryUploader.userIdProvider() },
    private val contextProvider: () -> Context
) : AdManager {

    private var interstitialAd: InterstitialAd? = null
    private var interstitialLoadedAt = 0L
    private var rewardedAd: RewardedAd? = null

    private var isInterstitialLoading = false
    private var isRewardedLoading = false

    companion object {
        private const val TAG = "FitCal_Ads"

        /** AdMob fills expire about an hour after loading; a stale one shows nothing or earns nothing. */
        private const val INTERSTITIAL_MAX_AGE_MS = 55 * 60 * 1000L

        var currentActivityRef: WeakReference<Activity>? = null

        /**
         * True once consent is resolved and MobileAds has initialized (MainActivity sets it).
         * Banners wait for it so no ad request goes out before the consent result.
         */
        val adsInitialized = MutableStateFlow(false)

        /** Forwards AdMob paid-event revenue (reported in micros) to ad telemetry. */
        fun trackPaidEvent(value: AdValue, adUnitId: String, format: String, responseInfo: ResponseInfo?) {
            AdTelemetryManager.trackAdRevenue(
                AdRevenuePayload(
                    adUnitId = adUnitId,
                    networkName = responseInfo?.loadedAdapterResponseInfo?.adSourceName ?: "AdMob",
                    revenue = value.valueMicros / 1_000_000.0,
                    format = format,
                    timestampMillis = System.currentTimeMillis()
                )
            )
        }
    }

    override fun preloadAds() {
        // Before consent and SDK init, nothing loads; MainActivity preloads once init completes.
        if (!adsInitialized.value) return
        preloadInterstitial()
        preloadRewarded()
    }

    /** Drops a preloaded interstitial that is too old to show. */
    private fun discardExpiredInterstitial() {
        if (interstitialAd != null &&
            System.currentTimeMillis() - interstitialLoadedAt > INTERSTITIAL_MAX_AGE_MS
        ) {
            Log.d(TAG, "Interstitial expired; reloading.")
            interstitialAd = null
        }
    }

    private fun preloadInterstitial() {
        discardExpiredInterstitial()
        if (interstitialAd != null || isInterstitialLoading) return
        isInterstitialLoading = true

        InterstitialAd.load(
            contextProvider(),
            AdConfig.ANDROID_INTERSTITIAL,
            fitCalAdRequest(),
            object : InterstitialAdLoadCallback() {
                override fun onAdLoaded(ad: InterstitialAd) {
                    ad.setOnPaidEventListener { value ->
                        trackPaidEvent(value, ad.adUnitId, "INTERSTITIAL", ad.responseInfo)
                    }
                    interstitialAd = ad
                    interstitialLoadedAt = System.currentTimeMillis()
                    isInterstitialLoading = false
                    Log.d(TAG, "Interstitial loaded.")
                }

                override fun onAdFailedToLoad(loadAdError: LoadAdError) {
                    interstitialAd = null
                    isInterstitialLoading = false
                    Log.w(TAG, "Interstitial failed to load: ${loadAdError.message}")
                    DiagnosticsCrashHook.logAdError("AdMob", "INTERSTITIAL", loadAdError.code.toString(), loadAdError.message)
                }
            }
        )
    }

    private fun preloadRewarded() {
        if (rewardedAd != null || isRewardedLoading) return
        isRewardedLoading = true

        RewardedAd.load(
            contextProvider(),
            AdConfig.ANDROID_REWARDED,
            fitCalAdRequest(),
            object : RewardedAdLoadCallback() {
                override fun onAdLoaded(ad: RewardedAd) {
                    ad.setOnPaidEventListener { value ->
                        trackPaidEvent(value, ad.adUnitId, "REWARDED", ad.responseInfo)
                    }
                    rewardedAd = ad
                    isRewardedLoading = false
                    Log.d(TAG, "Rewarded ad loaded.")
                }

                override fun onAdFailedToLoad(loadAdError: LoadAdError) {
                    rewardedAd = null
                    isRewardedLoading = false
                    Log.w(TAG, "Rewarded ad failed to load: ${loadAdError.message}")
                    DiagnosticsCrashHook.logAdError("AdMob", "REWARDED", loadAdError.code.toString(), loadAdError.message)
                }
            }
        )
    }

    override fun showScanProcessingAd(onFinished: () -> Unit) {
        val activity = currentActivityRef?.get()
        discardExpiredInterstitial()
        val ad = interstitialAd
        if (activity != null && !activity.isFinishing && !activity.isDestroyed && ad != null) {
            activity.runOnUiThread {
                ad.fullScreenContentCallback = object : FullScreenContentCallback() {
                    override fun onAdShowedFullScreenContent() {
                        // Starts the guest 90 s cooldown only when an ad really appeared (AGENTS.md 5.2a).
                        ScanQuotaManager.recordInterstitialShown()
                        AdTelemetryManager.trackInterstitialImpression()
                    }

                    override fun onAdDismissedFullScreenContent() {
                        interstitialAd = null
                        preloadInterstitial()
                        onFinished()
                    }

                    override fun onAdFailedToShowFullScreenContent(adError: AdError) {
                        Log.w(TAG, "Interstitial failed to show: ${adError.message}")
                        DiagnosticsCrashHook.logAdError("AdMob", "INTERSTITIAL_DISPLAY", adError.code.toString(), adError.message)
                        interstitialAd = null
                        preloadInterstitial()
                        onFinished()
                    }
                }
                ad.show(activity)
            }
        } else {
            // The scan is never blocked by a missing ad.
            Log.d(TAG, "Interstitial not ready; continuing scan.")
            preloadAds()
            onFinished()
        }
    }

    override fun showRewardedScanUnlockAd(onRewarded: () -> Unit, onDismissed: () -> Unit) {
        val activity = currentActivityRef?.get()
        val ad = rewardedAd
        if (activity != null && !activity.isFinishing && !activity.isDestroyed && ad != null) {
            activity.runOnUiThread {
                var rewardGranted = false
                applyServerSideVerification(ad)
                ad.fullScreenContentCallback = object : FullScreenContentCallback() {
                    override fun onAdShowedFullScreenContent() {
                        AdTelemetryManager.trackRewardedImpression()
                    }

                    override fun onAdDismissedFullScreenContent() {
                        rewardedAd = null
                        preloadRewarded()
                        if (rewardGranted) onRewarded()
                        onDismissed()
                    }

                    override fun onAdFailedToShowFullScreenContent(adError: AdError) {
                        Log.w(TAG, "Rewarded ad failed to show: ${adError.message}")
                        DiagnosticsCrashHook.logAdError("AdMob", "REWARDED_DISPLAY", adError.code.toString(), adError.message)
                        rewardedAd = null
                        preloadRewarded()
                        onDismissed()
                    }
                }
                ad.show(activity) { rewardGranted = true }
            }
        } else {
            // Leave the button active so the user can retry once the ad has loaded.
            Log.w(TAG, "Rewarded ad not ready; reloading.")
            if (activity != null && !activity.isFinishing && !activity.isDestroyed) {
                activity.runOnUiThread {
                    android.widget.Toast.makeText(
                        activity,
                        "Ad loading… please try again in a moment.",
                        android.widget.Toast.LENGTH_SHORT
                    ).show()
                }
            }
            preloadRewarded()
        }
    }

    /**
     * Tags the rewarded view with the Supabase user ID (as user_id and custom_data) for AdMob
     * SSV. Set right before showing, because the user can change after the ad was loaded.
     */
    private fun applyServerSideVerification(ad: RewardedAd) {
        val userId = try {
            userIdProvider()
        } catch (_: Throwable) {
            null
        }
        if (userId.isNullOrBlank()) {
            Log.w(TAG, "No user ID for rewarded SSV; the server-side callback will be ignored.")
            return
        }
        ad.setServerSideVerificationOptions(
            ServerSideVerificationOptions.Builder()
                .setUserId(userId)
                .setCustomData(userId)
                .build()
        )
    }

    override fun isInterstitialReady(): Boolean {
        discardExpiredInterstitial()
        return interstitialAd != null
    }

    override fun isRewardedReady(): Boolean = rewardedAd != null
}
