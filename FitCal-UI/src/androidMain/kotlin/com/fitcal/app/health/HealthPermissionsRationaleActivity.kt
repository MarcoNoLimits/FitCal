package com.fitcal.app.health

import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.os.Bundle

/**
 * Health Connect requires a screen explaining how FitCal uses health data. It opens the privacy
 * policy's Health Connect section in the browser and closes itself.
 */
class HealthPermissionsRationaleActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        try {
            startActivity(
                Intent(Intent.ACTION_VIEW, Uri.parse("https://fitcal.tecaa.xyz/privacy.html#health-connect"))
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            )
        } catch (_: Exception) {
            // No browser installed.
        }
        finish()
    }
}
