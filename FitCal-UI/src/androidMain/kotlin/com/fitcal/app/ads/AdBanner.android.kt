package com.fitcal.app.ads

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.fitcal.app.telemetry.AdTelemetryManager
import com.fitcal.app.telemetry.DiagnosticsCrashHook
import com.google.android.gms.ads.AdListener
import com.google.android.gms.ads.AdSize
import com.google.android.gms.ads.AdView
import com.google.android.gms.ads.LoadAdError

@Composable
actual fun AdBanner(modifier: Modifier) {
    if (com.fitcal.shared.subscription.SubscriptionManager.isPremiumUser()) {
        return
    }
    // No banner request before consent is resolved and the SDK is initialized.
    val adsInitialized by AndroidAdManager.adsInitialized.collectAsState()
    if (!adsInitialized) return
    Box(
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = 50.dp)
            .padding(vertical = 8.dp),
        contentAlignment = Alignment.Center
    ) {
        AndroidView(
            modifier = Modifier
                .clip(RoundedCornerShape(8.dp))
                .background(Color.Transparent),
            factory = { context ->
                AdView(context).apply {
                    setAdSize(AdSize.BANNER)
                    adUnitId = AdConfig.ANDROID_BANNER
                    adListener = object : AdListener() {
                        override fun onAdLoaded() {
                            AdTelemetryManager.trackBannerImpression()
                        }

                        override fun onAdFailedToLoad(loadAdError: LoadAdError) {
                            DiagnosticsCrashHook.logAdError("AdMob", "BANNER", loadAdError.code.toString(), loadAdError.message)
                        }
                    }
                    setOnPaidEventListener { value ->
                        AndroidAdManager.trackPaidEvent(value, adUnitId, "BANNER", responseInfo)
                    }
                    loadAd(fitCalAdRequest())
                }
            },
            onRelease = { adView -> adView.destroy() }
        )
    }
}
