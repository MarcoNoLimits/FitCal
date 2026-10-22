package com.fitcal.app.ui.screens.dashboard.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.fitcal.app.plan.formatKg
import com.fitcal.app.plan.formatPlanDate
import com.fitcal.app.ui.components.FitCalTextField
import com.fitcal.app.ui.theme.*

@Composable
private fun PlanCardFrame(content: @Composable ColumnScope.() -> Unit) {
    Card(
        colors = CardDefaults.cardColors(containerColor = InfoPanel),
        shape = RoundedCornerShape(RadiusL),
        modifier = Modifier
            .fillMaxWidth()
            .border(1.dp, BorderColor, RoundedCornerShape(RadiusL))
    ) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp), content = content)
    }
}

/** Optional invitation to set up a plan; dismissing it hides it for good. */
@Composable
fun PlanPromptCard(onSetUp: () -> Unit, onDismiss: () -> Unit) {
    PlanCardFrame {
        Row(verticalAlignment = Alignment.Top) {
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(text = "WANT DAILY TARGETS MADE FOR YOU?", style = BrandTypography.Eyebrow, color = PrimaryAccent)
                Text(
                    text = "Optional. Tell FitCal your goal and pace and it suggests calories and macros. Takes about a minute.",
                    style = BrandTypography.BodySmall,
                    color = TextColor
                )
            }
            IconButton(onClick = onDismiss, modifier = Modifier.size(44.dp)) {
                Icon(Icons.Default.Close, contentDescription = "Don't show again", tint = MutedTextColor, modifier = Modifier.size(18.dp))
            }
        }
        Button(
            onClick = onSetUp,
            colors = ButtonDefaults.buttonColors(containerColor = PrimaryAccent),
            shape = RoundedCornerShape(RadiusM),
            modifier = Modifier.heightIn(min = 44.dp)
        ) {
            Text("Set Up a Plan", style = BrandTypography.CardTitle, color = Color.White)
        }
    }
}

/** The optional two-weekly check-in: today's weight → recalculated targets. */
@Composable
fun PlanCheckInCard(
    currentWeightKg: Float,
    projectedDate: String?,
    onUpdate: (Float) -> Unit,
    onNotNow: () -> Unit,
    onWhyFluctuates: () -> Unit = {},
) {
    var weightStr by remember { mutableStateOf(formatKg(currentWeightKg)) }
    val weight = weightStr.replace(',', '.').toFloatOrNull()
    val valid = weight != null && weight in 30f..300f
    PlanCardFrame {
        Text(text = "PLAN CHECK-IN", style = BrandTypography.Eyebrow, color = PrimaryAccent)
        Text(
            text = "What do you weigh today? FitCal updates your targets to match." +
                (projectedDate?.let { " Current estimate: around ${formatPlanDate(it)}." } ?: ""),
            style = BrandTypography.BodySmall,
            color = TextColor
        )
        TextButton(onClick = onWhyFluctuates, contentPadding = PaddingValues(0.dp)) {
            Text("Why does my weight jump around?", color = PrimaryAccent, style = BrandTypography.BodySmall, fontWeight = FontWeight.SemiBold)
        }
        FitCalTextField(
            value = weightStr,
            onValueChange = { weightStr = it },
            label = "Weight today",
            unit = "kg",
            isError = weightStr.isNotBlank() && !valid,
            modifier = Modifier.fillMaxWidth()
        )
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Button(
                onClick = { if (valid) onUpdate(weight!!) },
                enabled = valid,
                colors = ButtonDefaults.buttonColors(containerColor = PrimaryAccent),
                shape = RoundedCornerShape(RadiusM),
                modifier = Modifier.weight(1f).heightIn(min = 44.dp)
            ) {
                Text("Update Targets", style = BrandTypography.CardTitle, color = Color.White)
            }
            OutlinedButton(
                onClick = onNotNow,
                shape = RoundedCornerShape(RadiusM),
                border = BorderStroke(1.dp, BorderColor),
                modifier = Modifier.weight(1f).heightIn(min = 44.dp)
            ) {
                Text("Not Now", style = BrandTypography.CardTitle, color = TextColor)
            }
        }
    }
}

/** Shown once the target weight is reached; the user decides what happens next. */
@Composable
fun PlanTargetReachedCard(
    targetWeightKg: Float,
    onMaintain: () -> Unit,
    onNewTarget: () -> Unit,
    onEndPlan: () -> Unit,
) {
    PlanCardFrame {
        Text(text = "TARGET REACHED", style = BrandTypography.Eyebrow, color = PrimaryAccent)
        Text(
            text = "You reached ${formatKg(targetWeightKg)} kg. What next?",
            style = BrandTypography.SectionTitle,
            fontWeight = FontWeight.Bold,
            color = TextColor
        )
        Button(
            onClick = onMaintain,
            colors = ButtonDefaults.buttonColors(containerColor = PrimaryAccent),
            shape = RoundedCornerShape(RadiusM),
            modifier = Modifier.fillMaxWidth().heightIn(min = 44.dp)
        ) {
            Text("Switch to Maintaining", style = BrandTypography.CardTitle, color = Color.White)
        }
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            OutlinedButton(
                onClick = onNewTarget,
                shape = RoundedCornerShape(RadiusM),
                border = BorderStroke(1.dp, BorderColor),
                modifier = Modifier.weight(1f).heightIn(min = 44.dp)
            ) {
                Text("New Target", style = BrandTypography.CardTitle, color = TextColor)
            }
            OutlinedButton(
                onClick = onEndPlan,
                shape = RoundedCornerShape(RadiusM),
                border = BorderStroke(1.dp, BorderColor),
                modifier = Modifier.weight(1f).heightIn(min = 44.dp)
            ) {
                Text("End Plan", style = BrandTypography.CardTitle, color = TextColor)
            }
        }
    }
}

/**
 * Adjusting target: a suggested change to the daily calories, with the reason. Nothing changes
 * unless the user taps Accept (owner decision 2026-10-23).
 */
@Composable
fun CalorieSuggestionCard(
    currentCalories: Int,
    suggestion: com.fitcal.app.plan.CalorieSuggestion,
    onAccept: () -> Unit,
    onNotNow: () -> Unit,
    onLearnMore: () -> Unit = {},
) {
    PlanCardFrame {
        Text(text = "PLAN SUGGESTION", style = BrandTypography.Eyebrow, color = PrimaryAccent)
        Text(
            text = "$currentCalories → ${suggestion.newCalories} kcal a day",
            style = BrandTypography.SectionTitle,
            fontWeight = FontWeight.Bold,
            color = TextColor
        )
        Text(text = suggestion.reason, style = BrandTypography.BodySmall, color = TextColor)
        Text(
            text = "Your protein and fat stay the same; carbs change with it. Nothing changes unless you accept.",
            style = BrandTypography.BodySmall,
            color = MutedTextColor
        )
        TextButton(onClick = onLearnMore, contentPadding = PaddingValues(0.dp)) {
            Text("Why does my weight jump around?", color = PrimaryAccent, style = BrandTypography.BodySmall, fontWeight = FontWeight.SemiBold)
        }
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Button(
                onClick = onAccept,
                colors = ButtonDefaults.buttonColors(containerColor = PrimaryAccent),
                shape = RoundedCornerShape(RadiusM),
                modifier = Modifier.weight(1f).heightIn(min = 44.dp)
            ) {
                Text("Accept", style = BrandTypography.CardTitle, color = Color.White)
            }
            OutlinedButton(
                onClick = onNotNow,
                shape = RoundedCornerShape(RadiusM),
                border = BorderStroke(1.dp, BorderColor),
                modifier = Modifier.weight(1f).heightIn(min = 44.dp)
            ) {
                Text("Not Now", style = BrandTypography.CardTitle, color = TextColor)
            }
        }
    }
}

/** First open of a new week: how the last Monday–Sunday went. Dismissed for good with ✕. */
@Composable
fun WeeklySummaryCard(
    week: com.fitcal.app.ui.screens.progress.SummaryWeek,
    summary: com.fitcal.app.ui.screens.progress.WeekSummary,
    onSeeProgress: () -> Unit,
    onDismiss: () -> Unit,
) {
    PlanCardFrame {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = "YOUR WEEK · ${formatPlanDate(week.monday)} – ${formatPlanDate(week.sunday)}",
                style = BrandTypography.Eyebrow,
                color = PrimaryAccent,
                modifier = Modifier.weight(1f)
            )
            IconButton(onClick = onDismiss, modifier = Modifier.size(32.dp)) {
                Icon(Icons.Default.Close, contentDescription = "Dismiss weekly summary", tint = MutedTextColor, modifier = Modifier.size(18.dp))
            }
        }
        Text(
            text = "${summary.loggedDays} of 7 days logged · ${summary.meals} meal${if (summary.meals == 1) "" else "s"}",
            style = BrandTypography.SectionTitle,
            fontWeight = FontWeight.Bold,
            color = TextColor
        )
        val avg = summary.averageCalories
        val previous = summary.previousAverageCalories
        Text(
            text = when {
                previous == null -> "Average $avg kcal a day."
                avg == previous -> "Average $avg kcal a day, the same as the week before."
                avg > previous -> "Average $avg kcal a day, ${avg - previous} more than the week before."
                else -> "Average $avg kcal a day, ${previous - avg} less than the week before."
            },
            style = BrandTypography.BodySmall,
            color = TextColor
        )
        summary.daysWithinCalorieTarget?.let { within ->
            Text(
                text = "Within your calorie target on $within of ${summary.loggedDays} logged day${if (summary.loggedDays == 1) "" else "s"}.",
                style = BrandTypography.BodySmall,
                color = MutedTextColor
            )
        }
        TextButton(onClick = onSeeProgress, contentPadding = PaddingValues(0.dp)) {
            Text("See your progress", color = PrimaryAccent, style = BrandTypography.BodySmall, fontWeight = FontWeight.SemiBold)
        }
    }
}

