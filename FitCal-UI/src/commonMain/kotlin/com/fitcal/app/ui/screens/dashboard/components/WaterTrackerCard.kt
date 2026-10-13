package com.fitcal.app.ui.screens.dashboard.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.fitcal.app.ui.components.PressableBox
import com.fitcal.app.ui.theme.*

const val WATER_GOAL_ML = 2000
const val WATER_GLASS_ML = 250
private const val GLASSES = WATER_GOAL_ML / WATER_GLASS_ML

/** 1250 → "1,250". */
fun formatMl(ml: Int): String = ml.toString().reversed().chunked(3).joinToString(",").reversed()

/** Tapping glass [index] (1-based): fill up to it, or, if it's the last full one, empty it. */
fun waterAfterGlassTap(currentMl: Int, index: Int): Int {
    val upTo = index * WATER_GLASS_ML
    return if (currentMl >= upTo && currentMl < upTo + WATER_GLASS_ML) upTo - WATER_GLASS_ML else upTo
}

@Composable
fun WaterTrackerCard(
    waterLogged: Int,
    onWaterChanged: (Int) -> Unit
) {
    val goalReached = waterLogged >= WATER_GOAL_ML
    val progress by animateFloatAsState((waterLogged.toFloat() / WATER_GOAL_ML).coerceIn(0f, 1f))

    Card(
        colors = CardDefaults.cardColors(containerColor = CardBackground),
        shape = RoundedCornerShape(RadiusL), // F1.2: was 24.dp → RadiusL
        modifier = Modifier
            .fillMaxWidth()
            .shadow(2.dp, RoundedCornerShape(RadiusL))
            .border(1.dp, BorderColor, RoundedCornerShape(RadiusL))
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    // Muted label + small dot, matching the Protein/Carbs/Fats cards.
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text(text = "WATER", style = BrandTypography.Eyebrow, color = MutedTextColor)
                        Box(modifier = Modifier.size(5.dp).background(WaterAccent, CircleShape))
                    }
                    Row(verticalAlignment = Alignment.Bottom) {
                        Text(
                            text = "${formatMl(waterLogged)} ml",
                            style = BrandTypography.SectionTitle,
                            fontWeight = FontWeight.Bold,
                            color = TextColor
                        )
                        Text(
                            text = " / ${formatMl(WATER_GOAL_ML)} ml",
                            style = BrandTypography.BodySmall,
                            color = MutedTextColor
                        )
                    }
                    Text(
                        text = when {
                            waterLogged > WATER_GOAL_ML -> "✓ Goal reached · ${formatMl(waterLogged - WATER_GOAL_ML)} ml extra"
                            goalReached -> "✓ Goal reached for today"
                            else -> "${formatMl(WATER_GOAL_ML - waterLogged)} ml to go"
                        },
                        style = BrandTypography.BodySmall,
                        fontWeight = if (goalReached) FontWeight.Medium else FontWeight.Normal,
                        color = if (goalReached) TextColor else MutedTextColor
                    )
                }

                // −250 / +250: explicit amounts, so neither reads as "close" or "clear all".
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    WaterStepButton(
                        label = "−",
                        description = "Remove $WATER_GLASS_ML ml of water",
                        enabled = waterLogged >= WATER_GLASS_ML,
                        onClick = { onWaterChanged(waterLogged - WATER_GLASS_ML) },
                    )
                    WaterStepButton(
                        label = "+$WATER_GLASS_ML ml",
                        description = "Add $WATER_GLASS_ML ml of water",
                        enabled = true,
                        onClick = { onWaterChanged(waterLogged + WATER_GLASS_ML) },
                    )
                }
            }

            // Progress toward the daily goal
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(4.dp)
                    .clip(CircleShape)
                    .background(SurfaceTint)
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth(progress)
                        .fillMaxHeight()
                        .clip(CircleShape)
                        .background(WaterAccent)
                )
            }

            // One glass per 250 ml. Glasses share the width equally so all 8 always fit.
            Row(modifier = Modifier.fillMaxWidth()) {
                for (i in 1..GLASSES) {
                    val full = waterLogged >= i * WATER_GLASS_ML
                    val fill by animateFloatAsState(if (full) 1f else 0f)
                    PressableBox(
                        onTap = { onWaterChanged(waterAfterGlassTap(waterLogged, i)) },
                        modifier = Modifier
                            .weight(1f)
                            .height(48.dp) // F0.3: tall tap target; width is 1/8 of the card
                            .semantics {
                                role = Role.Button
                                contentDescription = if (full) "Glass $i, full. Tap to set ${formatMl(waterAfterGlassTap(waterLogged, i))} ml"
                                else "Glass $i, empty. Tap to set ${formatMl(i * WATER_GLASS_ML)} ml"
                            }
                    ) {
                        WaterGlass(fill = fill, modifier = Modifier.align(Alignment.Center).size(width = 22.dp, height = 30.dp))
                    }
                }
            }
        }
    }
}

@Composable
private fun WaterStepButton(label: String, description: String, enabled: Boolean, onClick: () -> Unit) {
    val shape = RoundedCornerShape(RadiusS)
    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .heightIn(min = 44.dp)
            .widthIn(min = 44.dp)
            .clip(shape)
            .background(CardBackground, shape)
            .border(1.dp, BorderColor, shape)
            .semantics { contentDescription = description }
    ) {
        TextButton(
            onClick = onClick,
            enabled = enabled,
            contentPadding = PaddingValues(horizontal = 12.dp),
            modifier = Modifier.heightIn(min = 44.dp)
        ) {
            Text(
                text = label,
                style = BrandTypography.SectionTitle,
                fontWeight = FontWeight.Bold,
                color = if (enabled) TextColor else BorderColor
            )
        }
    }
}

/** A tumbler outline; [fill] (0–1) is how full of water it is. */
@Composable
private fun WaterGlass(fill: Float, modifier: Modifier = Modifier) {
    Canvas(modifier = modifier) {
        val w = size.width
        val h = size.height
        val inset = w * 0.14f
        val glass = Path().apply {
            moveTo(0f, 0f)
            lineTo(w, 0f)
            lineTo(w - inset, h)
            lineTo(inset, h)
            close()
        }
        if (fill > 0f) {
            clipPath(glass) {
                val top = h - (h * 0.8f * fill)
                drawRect(color = WaterSoft, topLeft = Offset(0f, top), size = androidx.compose.ui.geometry.Size(w, h - top))
                // Thin water line gives the fill definition without a solid block of colour.
                drawLine(
                    color = WaterAccent.copy(alpha = 0.7f * fill),
                    start = Offset(0f, top),
                    end = Offset(w, top),
                    strokeWidth = 1.5.dp.toPx()
                )
            }
        }
        drawPath(
            path = glass,
            color = if (fill > 0.5f) WaterAccent.copy(alpha = 0.8f) else BorderColor,
            style = Stroke(width = 1.5.dp.toPx(), cap = StrokeCap.Round)
        )
    }
}
