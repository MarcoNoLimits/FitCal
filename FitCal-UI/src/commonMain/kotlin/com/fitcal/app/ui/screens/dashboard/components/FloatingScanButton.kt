package com.fitcal.app.ui.screens.dashboard.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.fitcal.app.ui.theme.*

/** Size of the scan button while it floats at the bottom of the dashboard. */
val DockedScanButtonSize = 60.dp

/** Gap between the docked scan button and the banner (or screen edge) below it. */
val ScanDockMargin = 16.dp

/** Where the floating scan button sits, as the y of its centre in the dashboard's coordinates. */
data class ScanButtonPlacement(val centerY: Float, val landed: Boolean)

/**
 * The scan button docks at [dockCenterY] near the bottom of the screen. When the scan
 * card's shutter slot scrolls up to that point, the button lands in the slot and moves
 * with it, so there is only ever one scan button on screen. Once the slot scrolls above
 * [topLimitY] the button returns to the dock.
 */
fun placeScanButton(slotCenterY: Float?, dockCenterY: Float, topLimitY: Float): ScanButtonPlacement =
    if (slotCenterY != null && slotCenterY >= topLimitY && slotCenterY <= dockCenterY) {
        ScanButtonPlacement(slotCenterY, landed = true)
    } else {
        ScanButtonPlacement(dockCenterY, landed = false)
    }

/**
 * Shutter-style scan button: white ring, dark outline, emerald core, the same look as the
 * capture button on the camera screen. Shows the scans left while docked; the scan card
 * shows them once it has landed.
 */
@Composable
fun FloatingScanButton(
    landed: Boolean,
    scansRemaining: Int,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val landedScale = ScanShutterSize.value / DockedScanButtonSize.value
    val scale by animateFloatAsState(
        targetValue = if (landed) landedScale else 1f,
        animationSpec = if (REDUCED_MOTION_ENABLED) spring(stiffness = 10_000f) else spring(dampingRatio = 0.6f, stiffness = 500f),
        label = "scanButtonScale"
    )
    val badgeAlpha by animateFloatAsState(if (landed) 0f else 1f, label = "scanBadgeAlpha")

    Box(
        modifier = modifier
            .size(DockedScanButtonSize)
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            }
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .shadow(if (landed) 0.dp else 10.dp, CircleShape)
                .background(Color.White, CircleShape)
                .border(4.dp, TextColor, CircleShape)
                .clickable(role = Role.Button, onClick = onClick)
                .semantics { contentDescription = "Scan a meal. ${scansLeftLabel(scansRemaining)}" }
                .padding(4.dp)
        ) {
            Box(Modifier.fillMaxSize().background(PrimaryAccent, CircleShape))
        }

        if (badgeAlpha > 0f && scansRemaining < UNLIMITED_SCANS) {
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .offset(x = 4.dp, y = (-4).dp)
                    .graphicsLayer { alpha = badgeAlpha }
                    .size(22.dp)
                    .background(TextColor, CircleShape)
                    .border(2.dp, Color.White, CircleShape)
            ) {
                Text(
                    text = scansRemaining.toString(),
                    color = Color.White,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}
