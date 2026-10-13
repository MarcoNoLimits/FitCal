package com.fitcal.app.ads

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.fitcal.app.ui.theme.BgColor

/**
 * A passive (reading) screen with an adaptive banner anchored at the bottom (AGENTS.md 5.5:
 * banners only on passive screens, never on Camera/Scan or the scan results flow).
 *
 * Content should apply `.padding(inner).consumeWindowInsets(inner).navigationBarsPadding()`:
 * with a banner the gesture-bar inset is already covered by it; without one (premium, or
 * [showBanner] false) the content pads for the gesture bar itself.
 */
@Composable
fun BannerScaffold(
    showBanner: Boolean = true,
    content: @Composable (PaddingValues) -> Unit,
) {
    Scaffold(
        bottomBar = {
            if (showBanner) {
                AdBanner(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(BgColor)
                        .navigationBarsPadding()
                )
            }
        },
        containerColor = BgColor,
        // Screens apply their own status-bar padding.
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        content = content,
    )
}
