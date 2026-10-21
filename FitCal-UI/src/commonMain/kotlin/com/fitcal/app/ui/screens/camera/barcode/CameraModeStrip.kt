package com.fitcal.app.ui.screens.camera.barcode

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.fitcal.app.ui.theme.BrandTypography
import com.fitcal.app.ui.theme.REDUCED_MOTION_ENABLED
import com.fitcal.app.ui.theme.TextColor

/**
 * One entry in the camera's mode strip. The strip is data-driven so a new mode is one more
 * entry in the list the camera screen builds (for example `CameraModeOption("Describe", false, onDescribe)`).
 */
data class CameraModeOption(
    val label: String,
    val selected: Boolean,
    val onSelect: () -> Unit,
)

/** Pill-shaped segmented control over the camera preview: "Scan food · Barcode · Describe". */
@Composable
fun CameraModeStrip(
    options: List<CameraModeOption>,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .background(Color.Black.copy(alpha = 0.55f), RoundedCornerShape(50))
            .padding(4.dp)
            .selectableGroup(),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        options.forEach { option ->
            val background by animateColorAsState(
                targetValue = if (option.selected) Color.White else Color.Transparent,
                animationSpec = tween(if (REDUCED_MOTION_ENABLED) 0 else 180),
                label = "modeBackground",
            )
            val content by animateColorAsState(
                targetValue = if (option.selected) TextColor else Color.White,
                animationSpec = tween(if (REDUCED_MOTION_ENABLED) 0 else 180),
                label = "modeContent",
            )
            Box(
                modifier = Modifier
                    .heightIn(min = 44.dp)
                    .background(background, RoundedCornerShape(50))
                    .selectable(
                        selected = option.selected,
                        role = Role.Tab,
                        onClick = { if (!option.selected) option.onSelect() },
                    )
                    .padding(horizontal = 18.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = option.label,
                    style = BrandTypography.BodySmall,
                    fontWeight = if (option.selected) FontWeight.SemiBold else FontWeight.Medium,
                    color = content,
                )
            }
        }
    }
}
