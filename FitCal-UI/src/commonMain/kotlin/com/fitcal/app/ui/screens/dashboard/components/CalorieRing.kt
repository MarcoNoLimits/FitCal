package com.fitcal.app.ui.screens.dashboard.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.fitcal.app.plan.daysBetween
import com.fitcal.app.ui.theme.*
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.roundToInt

private const val RING_ANIMATION_MS = 700
private const val RING_SCALE_WINDOW_DAYS = 30

/**
 * What a full ring means when the user has no calorie target: their highest daily total in
 * the last [RING_SCALE_WINDOW_DAYS] days up to [today], so other days fill in proportion.
 * 0 when nothing was logged in that window (the ring stays empty).
 */
fun ringScaleWithoutTarget(dailyTotals: Map<String, Int>, today: String): Int =
    dailyTotals
        .filter { (date, _) -> (daysBetween(date, today) ?: -1) in 0 until RING_SCALE_WINDOW_DAYS }
        .values.maxOrNull() ?: 0

/** Share of the ring to fill: against the goal when there is one, otherwise against [scaleKcal]. */
fun ringFraction(consumed: Int, goal: Int, scaleKcal: Int): Float {
    val reference = if (goal > 0) goal else scaleKcal
    return if (reference > 0) (consumed.toFloat() / reference).coerceIn(0f, 1f) else 0f
}

/**
 * Calorie ring. When [consumed] changes (switching days, logging a meal) the arc fills or
 * empties to the new value and the number counts to it, so moving between days reads as a
 * change rather than a jump.
 */
@Composable
fun CircularCalorieProgressRing(
    consumed: Int,
    goal: Int,
    modifier: Modifier = Modifier,
    scaleKcal: Int = 0,
) {
    val hasGoal = goal > 0
    val fraction = remember { Animatable(0f) }
    val shownKcal = remember { Animatable(0f) }
    LaunchedEffect(consumed, goal, scaleKcal) {
        val spec = tween<Float>(if (REDUCED_MOTION_ENABLED) 0 else RING_ANIMATION_MS, easing = FastOutSlowInEasing)
        // Both run together; the number and the arc land at the same moment.
        coroutineScope {
            launch { fraction.animateTo(ringFraction(consumed, goal, scaleKcal), spec) }
            launch { shownKcal.animateTo(consumed.toFloat(), spec) }
        }
    }

    val shownConsumed = shownKcal.value.roundToInt()
    val remaining = goal - shownConsumed
    val isOver = hasGoal && remaining < 0
    val absRemaining = abs(remaining)
    val sweepAngle = fraction.value * 360f

    // F1.1: DangerColor / PrimaryAccent replace Color(0xFFEF4444) / Color(0xFF10B981)
    val primaryColor = if (isOver) DangerColor else PrimaryAccent

    Box(
        contentAlignment = Alignment.Center,
        modifier = modifier.size(140.dp)
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            // F1.1: SurfaceTint replaces Color(0xFFF1F5F9)
            drawCircle(
                color = SurfaceTint,
                style = Stroke(width = 10.dp.toPx(), cap = StrokeCap.Round)
            )
            drawArc(
                color = primaryColor,
                startAngle = -90f,
                sweepAngle = sweepAngle,
                useCenter = false,
                style = Stroke(width = 10.dp.toPx(), cap = StrokeCap.Round)
            )
        }

        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            // F2.1: KpiNumeral (16sp Bold Mono) replaces inline 24.sp
            Text(
                text = if (hasGoal) "$absRemaining" else "$shownConsumed",
                style = BrandTypography.KpiNumeral,
                color = if (isOver) DangerColor else TextColor // F1.1
            )
            // F2.1: Eyebrow (10sp) replaces 10.sp inline
            Text(
                text = when {
                    !hasGoal -> "kcal eaten"
                    isOver -> "kcal over"
                    else -> "kcal left"
                },
                style = BrandTypography.Eyebrow,
                fontWeight = FontWeight.Medium,
                color = if (isOver) DangerColor.copy(alpha = 0.8f) else MutedTextColor // F1.1
            )
        }
    }
}
