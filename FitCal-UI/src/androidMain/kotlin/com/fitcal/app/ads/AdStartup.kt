package com.fitcal.app.ads

import android.util.Log
import androidx.activity.ComponentActivity
import androidx.lifecycle.lifecycleScope
import com.fitcal.app.getPlatformAdManager
import com.fitcal.app.privacy.PrivacyConsent
import com.fitcal.app.requestPlatformAdConsent
import com.google.android.gms.ads.MobileAds
import kotlinx.coroutines.launch

/**
 * Starts the ad SDK once consent allows it.
 *
 * Order matters legally: the UMP/TCF consent signal is collected first, then the user's in-app
 * privacy decision is checked, and only then is MobileAds initialized (GDPR/ePrivacy Art. 5(3),
 * Google Play's EU consent policy).
 *
 * Runs at activity start and again when the in-app privacy screen is accepted. Before
 * 2026-11-10 it only ran at activity start, so on a first launch (privacy not yet accepted) the
 * SDK was never initialized and a new user saw no ads for their whole first session.
 *
 * Idempotent: once the SDK is initialized, later calls do nothing; a call while one is already
 * resolving consent is dropped.
 */
internal object AdStartup {
    private const val TAG = "FitCal_Privacy"

    @Volatile private var started = false
    @Volatile private var inFlight = false

    fun start(activity: ComponentActivity) {
        if (started || inFlight) return
        inFlight = true
        activity.lifecycleScope.launch {
            try {
                // 1. Collect the OS/store consent signal FIRST.
                val platformGranted = requestPlatformAdConsent()

                // 2. No in-app privacy acceptance yet: never initialize ad SDKs. A later call
                //    (from the privacy screen) retries.
                if (!PrivacyConsent.canRequestAds()) {
                    Log.i(TAG, "Privacy not accepted — skipping ad init")
                    return@launch
                }

                // 3. AND the platform signal with the user's explicit in-app choice.
                val personalized = platformGranted && PrivacyConsent.isAdsPersonalizationEnabled()

                // 4. Only now initialize the ad SDK.
                started = true
                MobileAds.initialize(activity.applicationContext) {
                    AndroidAdManager.adsInitialized.value = true
                    if (!personalized) {
                        // fitCalAdRequest() adds npa=1 to every request in this case.
                        Log.i(TAG, "Ads limited to non-personalized")
                    }
                    getPlatformAdManager().preloadAds()
                }
            } finally {
                inFlight = false
            }
        }
    }

    /** Called from shared code after the in-app privacy decision is saved. */
    fun startFromCurrentActivity() {
        val activity = AndroidAdManager.currentActivityRef?.get() as? ComponentActivity
        if (activity == null || activity.isFinishing || activity.isDestroyed) {
            Log.w(TAG, "No activity to start ads from; they start on the next launch")
            return
        }
        start(activity)
    }
}
