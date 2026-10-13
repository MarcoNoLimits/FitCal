package com.fitcal.app.ads

import android.os.Bundle
import com.fitcal.app.privacy.PrivacyConsent
import com.google.ads.mediation.admob.AdMobAdapter
import com.google.android.gms.ads.AdRequest

/**
 * Every FitCal ad request. When the user has "Personalized ads" off (the default), the request
 * carries Google's non-personalized flag (npa=1), so the in-app choice applies everywhere, not
 * only where the UMP/TCF consent signal does (EEA/UK).
 */
internal fun fitCalAdRequest(): AdRequest {
    val builder = AdRequest.Builder()
    if (!PrivacyConsent.isAdsPersonalizationEnabled()) {
        builder.addNetworkExtrasBundle(AdMobAdapter::class.java, Bundle().apply { putString("npa", "1") })
    }
    return builder.build()
}
