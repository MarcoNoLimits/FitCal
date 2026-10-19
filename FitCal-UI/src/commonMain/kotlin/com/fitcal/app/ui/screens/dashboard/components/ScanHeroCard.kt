package com.fitcal.app.ui.screens.dashboard.components

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.fitcal.app.ui.components.PressableBox
import com.fitcal.app.ui.theme.*

/** Diameter of the shutter slot; the floating scan button lands here at this size. */
val ScanShutterSize = 68.dp

/** What the scan card asks for, by local hour (0–23). */
fun scanPromptForHour(hour: Int): String = when (hour) {
    in 5..10 -> "Snap your breakfast"
    in 11..15 -> "Snap your lunch"
    in 18..21 -> "Snap your dinner"
    else -> "Snap a snack"
}

/** ScanQuotaManager reports Premium as 999 scans left. */
const val UNLIMITED_SCANS = 999

fun scansLeftLabel(scansRemaining: Int): String = when {
    scansRemaining >= UNLIMITED_SCANS -> "Unlimited scans"
    scansRemaining == 0 -> "No free scans left today"
    scansRemaining == 1 -> "1 scan left today"
    else -> "$scansRemaining scans left today"
}

/**
 * "Snap and log" card. The background is drawn, not a photo: no network request and no
 * live camera (which would need camera permission on the home screen). The shutter itself
 * is not drawn here. The card leaves an empty slot and reports its centre through
 * [onShutterSlotPositioned]; the dashboard's floating scan button lands in that slot.
 */
@Composable
fun ScanHeroCard(
    prompt: String,
    scansRemaining: Int,
    onScan: () -> Unit,
    onShutterSlotPositioned: (centerYInRoot: Float) -> Unit,
    modifier: Modifier = Modifier,
) {
    PressableBox(
        onTap = onScan,
        modifier = modifier
            .fillMaxWidth()
            .height(240.dp)
            .clip(RoundedCornerShape(RadiusXL))
            .background(TextColor)
            .border(4.dp, SurfaceTint, RoundedCornerShape(RadiusXL))
            .semantics { contentDescription = "$prompt. Opens the camera." }
    ) {
        PlateIllustration(Modifier.fillMaxSize())

        Column(
            modifier = Modifier
                .align(Alignment.TopStart)
                .padding(start = 24.dp, top = 22.dp, end = 120.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Box(
                modifier = Modifier
                    .background(Color.White.copy(alpha = 0.12f), CircleShape)
                    .padding(horizontal = 10.dp, vertical = 3.dp)
            ) {
                Text(
                    text = scansLeftLabel(scansRemaining),
                    color = Color.White,
                    style = BrandTypography.Micro,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 0.5.sp
                )
            }
            Text(
                text = prompt,
                color = Color.White,
                style = BrandTypography.SectionTitle,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = "Take a photo. FitCal estimates the calories and macros.",
                color = Color.White.copy(alpha = 0.7f),
                style = BrandTypography.BodySmall
            )
        }

        // Empty slot for the floating scan button: a dashed ring, visible only while
        // the button is docked elsewhere.
        Canvas(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 20.dp)
                .size(ScanShutterSize)
                .onGloballyPositioned { coords ->
                    onShutterSlotPositioned(coords.positionInRoot().y + coords.size.height / 2f)
                }
        ) {
            drawCircle(
                color = Color.White.copy(alpha = 0.35f),
                radius = size.minDimension / 2f - 2.dp.toPx(),
                style = Stroke(
                    width = 2.dp.toPx(),
                    pathEffect = PathEffect.dashPathEffect(floatArrayOf(6.dp.toPx(), 5.dp.toPx()))
                )
            )
        }
    }
}

/**
 * A plate seen from above, with three portions in the macro colours and a slow scan
 * line, on a soft emerald glow. Sits to the right so the text stays readable.
 */
@Composable
private fun PlateIllustration(modifier: Modifier) {
    val scanProgress = if (REDUCED_MOTION_ENABLED) {
        0.5f
    } else {
        val transition = rememberInfiniteTransition(label = "plateScan")
        transition.animateFloat(
            initialValue = 0f,
            targetValue = 1f,
            animationSpec = infiniteRepeatable(tween(2800, easing = LinearEasing), RepeatMode.Reverse),
            label = "plateScanLine"
        ).value
    }

    Canvas(modifier) {
        val r = 82.dp.toPx()
        val c = Offset(size.width - 52.dp.toPx(), size.height * 0.42f)

        drawCircle(
            brush = Brush.radialGradient(
                colors = listOf(PrimaryAccent.copy(alpha = 0.35f), Color.Transparent),
                center = c,
                radius = r * 1.9f
            ),
            radius = r * 1.9f,
            center = c
        )

        // Plate: rim, well, rim highlight
        drawCircle(Color.White.copy(alpha = 0.10f), radius = r, center = c)
        drawCircle(Color.White.copy(alpha = 0.06f), radius = r * 0.74f, center = c)
        drawCircle(Color.White.copy(alpha = 0.20f), radius = r, center = c, style = Stroke(1.5.dp.toPx()))

        // Portions: protein, carbs, fat
        drawCircle(ProteinColor.copy(alpha = 0.85f), radius = r * 0.27f, center = c + Offset(-r * 0.26f, -r * 0.10f))
        drawCircle(CarbsColor.copy(alpha = 0.85f), radius = r * 0.22f, center = c + Offset(r * 0.22f, r * 0.24f))
        drawCircle(FatColor.copy(alpha = 0.85f), radius = r * 0.14f, center = c + Offset(r * 0.20f, -r * 0.32f))

        // Scan line sweeping across the plate
        val y = c.y - r * 0.8f + (r * 1.6f) * scanProgress
        val halfChord = kotlin.math.sqrt((r * r - (y - c.y) * (y - c.y)).coerceAtLeast(0f))
        drawLine(
            color = PrimaryAccent.copy(alpha = 0.9f),
            start = Offset(c.x - halfChord, y),
            end = Offset(c.x + halfChord, y),
            strokeWidth = 2.dp.toPx()
        )
    }
}
