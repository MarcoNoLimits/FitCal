package com.fitcal.app.ui.screens.camera.barcode

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.fitcal.app.ui.theme.BorderColor
import com.fitcal.app.ui.theme.BrandTypography
import com.fitcal.app.ui.theme.CardBackground
import com.fitcal.app.ui.theme.MutedTextColor
import com.fitcal.app.ui.theme.PrimaryAccent
import com.fitcal.app.ui.theme.REDUCED_MOTION_ENABLED
import com.fitcal.app.ui.theme.RadiusL
import com.fitcal.app.ui.theme.RadiusM
import com.fitcal.app.ui.theme.RadiusS
import com.fitcal.app.ui.theme.TextColor

private val FrameWidth = 280.dp
private val FrameHeight = 160.dp

/**
 * Barcode viewfinder drawn over the live preview: a framing window with a sweeping scan line,
 * a hint, and a "Looking up…" chip while Open Food Facts answers.
 */
@Composable
fun BarcodeViewfinder(
    lookingUpCode: String?,
    modifier: Modifier = Modifier,
) {
    Box(modifier = modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Box(
                modifier = Modifier
                    .size(FrameWidth, FrameHeight)
                    .border(2.dp, Color.White.copy(alpha = 0.9f), RoundedCornerShape(RadiusS)),
            ) {
                if (lookingUpCode == null && !REDUCED_MOTION_ENABLED) ScanLine()
            }
            Surface(
                color = Color.Black.copy(alpha = 0.6f),
                shape = RoundedCornerShape(50),
                modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
                ) {
                    if (lookingUpCode != null) {
                        CircularProgressIndicator(color = Color.White, strokeWidth = 2.dp, modifier = Modifier.size(16.dp))
                    }
                    Text(
                        text = if (lookingUpCode != null) "Looking up product…" else "Point at the barcode on the pack",
                        style = BrandTypography.BodySmall,
                        color = Color.White,
                    )
                }
            }
        }
    }
}

@Composable
private fun ScanLine() {
    val transition = rememberInfiniteTransition(label = "barcodeScanLine")
    val progress by transition.animateFloat(
        initialValue = 0.08f,
        targetValue = 0.92f,
        animationSpec = infiniteRepeatable(tween(1400), RepeatMode.Reverse),
        label = "scanLineProgress",
    )
    Box(
        modifier = Modifier
            .offset(y = FrameHeight * progress)
            .padding(horizontal = 12.dp)
            .fillMaxWidth()
            .height(2.dp)
            .background(PrimaryAccent),
    )
}

/**
 * Shown when the barcode is unknown, has no nutrition facts, or the lookup failed.
 * Offers the photo path and manual entry, so the user is never stuck.
 */
@Composable
fun BarcodeProblemCard(
    state: BarcodeScanState,
    onTakePhoto: () -> Unit,
    onAddManually: () -> Unit,
    onScanAgain: () -> Unit,
    onClose: () -> Unit,
) {
    val (title, body) = when (state) {
        is BarcodeScanState.NotFound ->
            if (state.productName != null) {
                "No nutrition facts yet" to "We found ${state.productName}, but it has no nutrition facts in Open Food Facts yet."
            } else {
                "Product not found" to "This barcode isn't in Open Food Facts yet."
            }
        is BarcodeScanState.Failed ->
            "Couldn't look it up" to "Check your connection and try again."
        else -> return
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.7f))
            .padding(24.dp),
        contentAlignment = Alignment.Center,
    ) {
        Card(
            colors = CardDefaults.cardColors(containerColor = CardBackground),
            shape = RoundedCornerShape(RadiusL),
            modifier = Modifier
                .fillMaxWidth()
                .border(1.dp, BorderColor, RoundedCornerShape(RadiusL)),
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.padding(24.dp).semantics { liveRegion = LiveRegionMode.Polite },
            ) {
                Text(
                    text = title,
                    style = BrandTypography.SectionTitle,
                    color = TextColor,
                    textAlign = TextAlign.Center,
                )
                Text(
                    text = body,
                    style = BrandTypography.BodySmall,
                    color = MutedTextColor,
                    textAlign = TextAlign.Center,
                )
                Box(modifier = Modifier.height(4.dp))

                if (state is BarcodeScanState.Failed) {
                    Button(
                        onClick = onScanAgain,
                        colors = ButtonDefaults.buttonColors(containerColor = PrimaryAccent),
                        shape = RoundedCornerShape(RadiusM),
                        modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp),
                    ) {
                        Text("Try again", color = Color.White, fontWeight = FontWeight.Bold)
                    }
                    OutlinedButton(
                        onClick = onTakePhoto,
                        shape = RoundedCornerShape(RadiusM),
                        border = BorderStroke(1.dp, BorderColor),
                        modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp),
                    ) {
                        Text("Take a photo instead", color = TextColor)
                    }
                } else {
                    Button(
                        onClick = onTakePhoto,
                        colors = ButtonDefaults.buttonColors(containerColor = PrimaryAccent),
                        shape = RoundedCornerShape(RadiusM),
                        modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp),
                    ) {
                        Text("Take a photo instead", color = Color.White, fontWeight = FontWeight.Bold)
                    }
                    OutlinedButton(
                        onClick = onAddManually,
                        shape = RoundedCornerShape(RadiusM),
                        border = BorderStroke(1.dp, PrimaryAccent),
                        modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp),
                    ) {
                        Text("Add it manually", color = PrimaryAccent, fontWeight = FontWeight.SemiBold)
                    }
                }

                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (state !is BarcodeScanState.Failed) {
                        TextButton(onClick = onScanAgain, modifier = Modifier.heightIn(min = 44.dp)) {
                            Text("Scan another", color = TextColor, style = BrandTypography.BodySmall)
                        }
                        Box(modifier = Modifier.width(4.dp))
                    }
                    TextButton(onClick = onClose, modifier = Modifier.heightIn(min = 44.dp)) {
                        Text("Cancel", color = MutedTextColor, style = BrandTypography.BodySmall)
                    }
                }
            }
        }
    }
}
