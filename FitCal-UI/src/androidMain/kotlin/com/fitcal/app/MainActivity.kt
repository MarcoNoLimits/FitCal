package com.fitcal.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.fitcal.app.ads.AdStartup
import com.fitcal.app.ads.AndroidAdManager
import com.fitcal.app.ui.launch.LaunchAnimationHost
import com.google.android.gms.ads.MobileAds
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
        // Consent signal first, then the in-app privacy decision, then MobileAds (see AdStartup).
        // On a first launch the privacy screen is not accepted yet, so this does nothing and
        // App() starts ads again once the user accepts (startAdsIfAllowed).
        AdStartup.start(this)
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

