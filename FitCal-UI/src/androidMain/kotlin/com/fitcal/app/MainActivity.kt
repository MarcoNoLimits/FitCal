package com.fitcal.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.fitcal.app.ads.AndroidAdManager
import com.fitcal.app.privacy.PrivacyConsent
import com.fitcal.app.ui.launch.LaunchAnimationHost
import com.google.android.gms.ads.MobileAds
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.launch
import java.lang.ref.WeakReference

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        appContext = applicationContext
        initPrivacyConsentStore()
        AndroidAdManager.currentActivityRef = WeakReference(this)
        // Our own phones always get test ads. Set before any composable can build an AdView.
        MobileAds.setRequestConfiguration(
            MobileAds.getRequestConfiguration().toBuilder()
                .setTestDeviceIds(com.fitcal.app.ads.AdConfig.ANDROID_TEST_DEVICE_IDS)
                .build()
        )
        // Health Connect's permission screen must be registered before the activity starts.
        com.fitcal.app.health.HealthConnectBridge.launcher = registerForActivityResult(
            androidx.health.connect.client.PermissionController.createRequestPermissionResultContract()
        ) { granted -> com.fitcal.app.health.HealthConnectBridge.onPermissionsResult(granted) }
        // Opened from the home-screen widget's Scan button (not on a config-change recreation).
        if (savedInstanceState == null) handleLaunchIntent(intent)

        setContent {
            LaunchAnimationHost {
                App()
            }
        }

        // ── Ad initialization is DEFERRED until consent is resolved. ──
        // Previously MobileAds initialized and preloadAds() ran before (or
        // entirely without) a CMP result, which violates GDPR/ePrivacy Art. 5(3),
        // Google Play's EU consent policy, and ATT. Order matters legally.
        lifecycleScope.launch {
            // 1. Collect the OS/store consent signal FIRST.
            val platformGranted = requestPlatformAdConsent()

            // 2. AND it with the user's explicit in-app choice. Denying either wins.
            val personalized = platformGranted && PrivacyConsent.isAdsPersonalizationEnabled()

            if (!PrivacyConsent.canRequestAds()) {
                // No privacy acceptance at all: never initialize ad SDKs.
                android.util.Log.i("FitCal_Privacy", "Privacy not accepted — skipping ad init")
                return@launch
            }

            // 3. Only now initialize the ad SDK.
            MobileAds.initialize(this@MainActivity) {
                AndroidAdManager.adsInitialized.value = true
                if (!personalized) {
                    // fitCalAdRequest() adds npa=1 to every request in this case.
                    android.util.Log.i("FitCal_Privacy", "Ads limited to non-personalized")
                }
                getPlatformAdManager().preloadAds()
            }
        }
    }

    override fun onNewIntent(intent: android.content.Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleLaunchIntent(intent)
    }

    private fun handleLaunchIntent(intent: android.content.Intent?) {
        if (intent?.getStringExtra(com.fitcal.app.widget.TodayWidgetProvider.EXTRA_LAUNCH_ACTION) ==
            com.fitcal.app.widget.TodayWidgetProvider.ACTION_SCAN
        ) {
            PendingLaunchAction.post(LaunchAction.SCAN)
            // Consumed: a later recreation of this activity must not open the camera again.
            intent.removeExtra(com.fitcal.app.widget.TodayWidgetProvider.EXTRA_LAUNCH_ACTION)
        }
    }

    override fun onResume() {
        super.onResume()
        AndroidAdManager.currentActivityRef = WeakReference(this)
    }

    override fun onDestroy() {
        super.onDestroy()
        if (AndroidAdManager.currentActivityRef?.get() == this) {
            AndroidAdManager.currentActivityRef = null
        }
    }
}

