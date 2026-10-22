package com.fitcal.app.ui.screens.progress

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.fitcal.app.ads.BannerScaffold
import com.fitcal.app.nutrition.SUGAR_GUIDELINE_G
import com.fitcal.app.ui.screens.dashboard.components.mealStreak
import com.fitcal.app.plan.TargetsMode
import com.fitcal.app.plan.formatKg
import com.fitcal.app.plan.formatPlanDate
import com.fitcal.app.plan.hasDailyTargets
import com.fitcal.app.plan.resolvedTargetsMode
import com.fitcal.app.ui.theme.BorderColor
import com.fitcal.app.ui.theme.BrandTypography
import com.fitcal.app.ui.theme.CardBackground
import com.fitcal.app.ui.theme.MutedTextColor
import com.fitcal.app.ui.theme.PrimaryAccent
import com.fitcal.app.ui.theme.ProteinColor
import com.fitcal.app.ui.theme.REDUCED_MOTION_ENABLED
import com.fitcal.app.ui.theme.RadiusL
import com.fitcal.app.ui.theme.SurfaceTint
import com.fitcal.app.ui.theme.TextColor
import com.fitcal.app.ui.theme.CarbsColor
import com.fitcal.app.ui.theme.FatColor
import com.fitcal.app.ui.theme.SugarColor
import com.fitcal.shared.model.LoggedMeal
import com.fitcal.shared.model.UserProfile
import com.fitcal.shared.model.WeightEntry
import kotlin.math.abs

private const val CHART_ANIMATION_MS = 650
private val WeightChartHeight = 180.dp
private val BarChartHeight = 96.dp
// Inset above and below the plot so the top and bottom tick labels line up with their lines.
private val PlotInset = 8.dp

/**
 * Progress: weight over the last 30 or 90 days with the plan's target, and the last 7 days of
 * calories and protein against the daily targets. Passive history, so a banner is allowed
 * (AGENTS.md 5.5).
 */
@Composable
fun ProgressScreen(
    profile: UserProfile,
    weightEntries: List<WeightEntry>,
    meals: List<LoggedMeal>,
    today: String,
    onBack: () -> Unit,
) {
    var range by rememberSaveable { mutableStateOf(ProgressRange.DAYS_30) }

    val inWindow = remember(weightEntries, today, range) { entriesInWindow(weightEntries, today, range.days) }
    val planTargetKg = if (profile.resolvedTargetsMode() == TargetsMode.PLAN) profile.plan?.targetWeightKg else null
    val hasTargets = profile.hasDailyTargets()
    val week = remember(meals, today) { dailyTotals(meals, today, 7) }
    val summary = remember(meals, today, profile) { weekSummary(meals, today, if (hasTargets) profile.calGoal else null) }
    val streak = remember(meals, today) { mealStreak(meals.map { it.date }, today) }

    BannerScaffold { inner ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(inner)
                .consumeWindowInsets(inner)
                .statusBarsPadding()
                .navigationBarsPadding(),
        ) {
            // ── Header ───────────────────────────────────────────────────────────
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 16.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = "✕",
                    fontSize = 18.sp,
                    color = MutedTextColor,
                    modifier = Modifier
                        .size(44.dp)
                        .semantics { contentDescription = "Close" }
                        .clickable(role = Role.Button, onClick = onBack)
                        .padding(12.dp),
                )
                Spacer(Modifier.width(8.dp))
                Column(Modifier.weight(1f)) {
                    Text(text = "Progress", style = BrandTypography.ScreenTitle, color = TextColor)
                    Text(
                        text = "Your week, your weight and your trends",
                        style = BrandTypography.BodySmall,
                        color = MutedTextColor,
                    )
                }
            }

            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(start = 16.dp, end = 16.dp, bottom = 32.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                WeekSummaryCard(summary, streak)

                RangeToggle(range = range, onSelect = { range = it })

                WeightCard(
                    entries = inWindow,
                    hasAnyEntry = weightEntries.isNotEmpty(),
                    targetKg = planTargetKg,
                    today = today,
                    range = range,
                )

                WeekBarsCard(
                    title = "CALORIES",
                    unit = "kcal",
                    days = week,
                    values = week.map { it.calories },
                    target = if (hasTargets) profile.calGoal else null,
                    color = PrimaryAccent,
                )

                WeekBarsCard(
                    title = "PROTEIN",
                    unit = "g",
                    days = week,
                    values = week.map { it.proteinG },
                    target = if (hasTargets) profile.proteinGoal else null,
                    color = ProteinColor,
                )

                WeekBarsCard(
                    title = "CARBS",
                    unit = "g",
                    days = week,
                    values = week.map { it.carbsG },
                    target = if (hasTargets) profile.carbsGoal else null,
                    color = CarbsColor,
                )

                WeekBarsCard(
                    title = "FAT",
                    unit = "g",
                    days = week,
                    values = week.map { it.fatG },
                    target = if (hasTargets) profile.fatGoal else null,
                    color = FatColor,
                )

                // Everyone gets the WHO guideline line for sugar, targets or not.
                WeekBarsCard(
                    title = "SUGAR",
                    unit = "g",
                    days = week,
                    values = week.map { it.sugarG },
                    target = SUGAR_GUIDELINE_G,
                    targetLabel = "Guide $SUGAR_GUIDELINE_G g",
                    color = SugarColor,
                )

                summary.split?.let { MacroSplitCard(it) }

                Text(
                    text = "Weight changes day to day with water and food. Look at the trend over weeks, not single days. General estimates, not medical advice.",
                    style = BrandTypography.BodySmall,
                    color = MutedTextColor,
                    modifier = Modifier.padding(horizontal = 4.dp),
                )
            }
        }
    }
}

@Composable
private fun RangeToggle(range: ProgressRange, onSelect: (ProgressRange) -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(SurfaceTint, RoundedCornerShape(12.dp))
            .padding(4.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        ProgressRange.entries.forEach { r ->
            val selected = r == range
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .weight(1f)
                    .heightIn(min = 44.dp)
                    .background(if (selected) CardBackground else SurfaceTint, RoundedCornerShape(9.dp))
                    .then(if (selected) Modifier.border(1.dp, BorderColor, RoundedCornerShape(9.dp)) else Modifier)
                    .selectable(selected = selected, role = Role.Tab, onClick = { onSelect(r) }),
            ) {
                Text(
                    text = r.label,
                    fontSize = 14.sp,
                    fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
                    color = if (selected) TextColor else MutedTextColor,
                )
            }
        }
    }
}

@Composable
private fun ProgressCard(content: @Composable () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(CardBackground, RoundedCornerShape(RadiusL))
            .border(1.dp, BorderColor, RoundedCornerShape(RadiusL))
            .padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) { content() }
}

/** Animates 0 → 1 whenever [key] changes, instantly with reduced motion. */
@Composable
private fun rememberReveal(key: Any): Float {
    val progress = remember(key) { Animatable(if (REDUCED_MOTION_ENABLED) 1f else 0f) }
    LaunchedEffect(key) {
        progress.animateTo(1f, tween(if (REDUCED_MOTION_ENABLED) 0 else CHART_ANIMATION_MS, easing = FastOutSlowInEasing))
    }
    return progress.value
}

@Composable
private fun WeightCard(
    entries: List<WeightEntry>,
    hasAnyEntry: Boolean,
    targetKg: Float?,
    today: String,
    range: ProgressRange,
) {
    ProgressCard {
        Text(text = "WEIGHT", style = BrandTypography.Eyebrow, color = MutedTextColor)

        val latest = entries.lastOrNull()
        val change = weightChangeKg(entries)
        Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(
                text = latest?.let { "${formatKg(it.weightKg)} kg" } ?: "–",
                fontSize = 28.sp,
                fontWeight = FontWeight.Bold,
                color = TextColor,
            )
            if (change != null) {
                Text(
                    text = when {
                        abs(change) < 0.05f -> "no change in ${range.label}"
                        change < 0 -> "−${formatKg(-change)} kg in ${range.label}"
                        else -> "+${formatKg(change)} kg in ${range.label}"
                    },
                    style = BrandTypography.BodySmall,
                    color = MutedTextColor,
                    modifier = Modifier.padding(bottom = 6.dp),
                )
            }
        }
        if (targetKg != null) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                DashSwatch(PrimaryAccent)
                Text(
                    text = "Plan target ${formatKg(targetKg)} kg",
                    style = BrandTypography.BodySmall,
                    color = MutedTextColor,
                )
            }
        }

        val scale = remember(entries, targetKg) { weightChartScale(entries.map { it.weightKg }, targetKg) }
        if (entries.isEmpty() || scale == null) {
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(WeightChartHeight)
                    .background(SurfaceTint, RoundedCornerShape(12.dp))
                    .padding(16.dp),
            ) {
                Text(
                    text = if (hasAnyEntry) {
                        "No weigh-ins in the last ${range.label}."
                    } else {
                        "Your weight shows here after a plan check-in, or when you change it in Settings."
                    },
                    style = BrandTypography.Body,
                    color = MutedTextColor,
                )
            }
        } else {
            WeightChart(entries = entries, scale = scale, targetKg = targetKg, today = today, days = range.days)
            Row(Modifier.fillMaxWidth().padding(start = 40.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(formatPlanDate(windowStart(today, range.days)), style = BrandTypography.Micro, color = MutedTextColor)
                Text("Today", style = BrandTypography.Micro, color = MutedTextColor)
            }
        }
    }
}

@Composable
private fun DashSwatch(color: Color) {
    Canvas(Modifier.size(width = 16.dp, height = 8.dp)) {
        drawLine(
            color = color,
            start = Offset(0f, size.height / 2),
            end = Offset(size.width, size.height / 2),
            strokeWidth = 2.dp.toPx(),
            pathEffect = PathEffect.dashPathEffect(floatArrayOf(4.dp.toPx(), 3.dp.toPx())),
        )
    }
}

@Composable
private fun WeightChart(
    entries: List<WeightEntry>,
    scale: WeightChartScale,
    targetKg: Float?,
    today: String,
    days: Int,
) {
    val reveal = rememberReveal(Triple(entries, targetKg, days))
    val description = buildString {
        append("Weight chart, ${entries.size} weigh-ins. ")
        append("From ${formatKg(entries.first().weightKg)} to ${formatKg(entries.last().weightKg)} kg.")
        if (targetKg != null) append(" Target ${formatKg(targetKg)} kg.")
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(WeightChartHeight)
            .semantics { contentDescription = description },
    ) {
        // Tick labels, top (max) to bottom (min), aligned with the plot's grid lines.
        Column(
            modifier = Modifier.width(40.dp).fillMaxHeight(),
            verticalArrangement = Arrangement.SpaceBetween,
        ) {
            scale.ticks.reversed().forEach { tick ->
                Text(formatKg(tick), style = BrandTypography.Micro, color = MutedTextColor)
            }
        }
        Canvas(Modifier.weight(1f).fillMaxHeight()) {
            val inset = PlotInset.toPx()
            val plotTop = inset
            val plotHeight = size.height - inset * 2
            fun y(kg: Float) = plotTop + plotHeight * (1f - scale.yFraction(kg))
            fun x(date: String) = size.width * xFraction(date, today, days)

            // Grid
            scale.ticks.forEach { tick ->
                drawLine(BorderColor, Offset(0f, y(tick)), Offset(size.width, y(tick)), strokeWidth = 1.dp.toPx())
            }

            // Plan target: dashed
            if (targetKg != null) {
                drawLine(
                    color = PrimaryAccent.copy(alpha = 0.7f),
                    start = Offset(0f, y(targetKg)),
                    end = Offset(size.width, y(targetKg)),
                    strokeWidth = 2.dp.toPx(),
                    pathEffect = PathEffect.dashPathEffect(floatArrayOf(8.dp.toPx(), 6.dp.toPx())),
                )
            }

            // Weight line, revealed left to right
            val points = entries.map { Offset(x(it.date), y(it.weightKg)) }
            clipRect(right = size.width * reveal) {
                if (points.size >= 2) {
                    val path = Path().apply {
                        moveTo(points.first().x, points.first().y)
                        points.drop(1).forEach { lineTo(it.x, it.y) }
                    }
                    drawPath(
                        path = path,
                        color = TextColor,
                        style = Stroke(width = 2.5.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round),
                    )
                }
                points.forEachIndexed { i, p ->
                    val isLast = i == points.lastIndex
                    drawCircle(CardBackground, radius = (if (isLast) 6 else 4).dp.toPx(), center = p)
                    drawCircle(
                        color = if (isLast) PrimaryAccent else TextColor,
                        radius = (if (isLast) 6 else 4).dp.toPx(),
                        center = p,
                        style = Stroke(width = 2.dp.toPx()),
                    )
                }
            }
        }
    }
}

@Composable
private fun WeekBarsCard(
    title: String,
    unit: String,
    days: List<DayTotals>,
    values: List<Int>,
    target: Int?,
    color: Color,
    targetLabel: String? = null,
) {
    ProgressCard {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(text = title, style = BrandTypography.Eyebrow, color = MutedTextColor, modifier = Modifier.weight(1f))
            Text(
                text = targetLabel ?: if (target != null) "Target $target $unit" else "No daily target",
                style = BrandTypography.BodySmall,
                color = MutedTextColor,
            )
        }
        val loggedDays = values.count { it > 0 }
        val average = if (loggedDays > 0) values.filter { it > 0 }.sum() / loggedDays else 0
        Text(
            text = if (loggedDays > 0) "Avg $average $unit on $loggedDays logged day${if (loggedDays == 1) "" else "s"}" else "Nothing logged in the last 7 days",
            style = BrandTypography.Body,
            fontWeight = FontWeight.SemiBold,
            color = TextColor,
        )

        val barScaleValue = barScale(values, target)
        val reveal = rememberReveal(Pair(values, target))
        Canvas(
            Modifier
                .fillMaxWidth()
                .height(BarChartHeight)
                .semantics {
                    contentDescription = "$title for the last 7 days: " +
                        days.zip(values).joinToString { (d, v) -> "${weekdayShort(d.date)} $v $unit" } +
                        (target?.let { ". Target $it $unit." } ?: "")
                },
        ) {
            val slot = size.width / values.size.coerceAtLeast(1)
            val barWidth = slot * 0.56f
            values.forEachIndexed { i, v ->
                val h = size.height * barFraction(v, barScaleValue) * reveal
                val left = slot * i + (slot - barWidth) / 2
                // Track
                drawRoundRect(
                    color = SurfaceTint,
                    topLeft = Offset(left, 0f),
                    size = Size(barWidth, size.height),
                    cornerRadius = CornerRadius(6.dp.toPx()),
                )
                if (h > 0f) {
                    val over = target != null && v > target
                    drawRoundRect(
                        color = if (over) color.copy(alpha = 0.55f) else color,
                        topLeft = Offset(left, size.height - h),
                        size = Size(barWidth, h),
                        cornerRadius = CornerRadius(6.dp.toPx()),
                    )
                }
            }
            if (target != null && barScaleValue > 0) {
                val ty = size.height * (1f - barFraction(target, barScaleValue))
                drawLine(
                    color = TextColor.copy(alpha = 0.6f),
                    start = Offset(0f, ty),
                    end = Offset(size.width, ty),
                    strokeWidth = 1.5.dp.toPx(),
                    pathEffect = PathEffect.dashPathEffect(floatArrayOf(6.dp.toPx(), 4.dp.toPx())),
                )
            }
        }
        Row(Modifier.fillMaxWidth()) {
            days.forEachIndexed { i, d ->
                Text(
                    text = if (i == days.lastIndex) "Today" else weekdayShort(d.date),
                    style = BrandTypography.Micro,
                    fontWeight = if (i == days.lastIndex) FontWeight.SemiBold else FontWeight.Normal,
                    color = if (i == days.lastIndex) TextColor else MutedTextColor,
                    modifier = Modifier.weight(1f),
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                )
            }
        }
    }
}

/** The week at a glance: days logged, meals, streak, days within target and the trend. */
@Composable
private fun WeekSummaryCard(summary: WeekSummary, streak: Int) {
    ProgressCard {
        Text("THIS WEEK", style = BrandTypography.Eyebrow, color = MutedTextColor)
        if (summary.loggedDays == 0) {
            Text(
                "Nothing logged in the last 7 days yet. Your week fills in as you log.",
                style = BrandTypography.Body,
                color = TextColor,
            )
        } else {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                SummaryStat("${summary.loggedDays}/7", "days logged", Modifier.weight(1f))
                SummaryStat("${summary.meals}", if (summary.meals == 1) "meal" else "meals", Modifier.weight(1f))
                SummaryStat(if (streak >= 1) "$streak" else "-", "day streak", Modifier.weight(1f))
            }
            summary.daysWithinCalorieTarget?.let { within ->
                val days = summary.loggedDays
                Text(
                    "Within your calorie target on $within of $days logged day${if (days == 1) "" else "s"}.",
                    style = BrandTypography.BodySmall,
                    color = MutedTextColor,
                )
            }
            val avg = summary.averageCalories
            val previous = summary.previousAverageCalories
            val trend = when {
                previous == null -> "Average $avg kcal a day."
                avg == previous -> "Average $avg kcal a day, the same as the week before."
                avg > previous -> "Average $avg kcal a day, ${avg - previous} more than the week before."
                else -> "Average $avg kcal a day, ${previous - avg} less than the week before."
            }
            Text(trend, style = BrandTypography.BodySmall, color = MutedTextColor)
        }
    }
}

@Composable
private fun SummaryStat(value: String, label: String, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier
            .background(SurfaceTint, RoundedCornerShape(12.dp))
            .padding(vertical = 10.dp, horizontal = 8.dp)
            .semantics(mergeDescendants = true) {},
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(value, style = BrandTypography.KpiNumeral, color = TextColor)
        Text(label, style = BrandTypography.Micro, color = MutedTextColor)
    }
}

/** Where the week's calories came from, as one stacked bar. */
@Composable
private fun MacroSplitCard(split: MacroSplit) {
    ProgressCard {
        Text("WHERE YOUR CALORIES CAME FROM", style = BrandTypography.Eyebrow, color = MutedTextColor)
        Row(
            Modifier
                .fillMaxWidth()
                .height(12.dp)
                .semantics {
                    contentDescription = "Protein ${split.proteinPct}%, carbs ${split.carbsPct}%, fat ${split.fatPct}% of calories this week"
                },
        ) {
            listOf(split.proteinPct to ProteinColor, split.carbsPct to CarbsColor, split.fatPct to FatColor)
                .filter { it.first > 0 }
                .forEach { (pct, color) ->
                    Box(Modifier.weight(pct.toFloat()).fillMaxHeight().background(color))
                }
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text("Protein ${split.proteinPct}%", style = BrandTypography.BodySmall, color = TextColor)
            Text("Carbs ${split.carbsPct}%", style = BrandTypography.BodySmall, color = TextColor)
            Text("Fat ${split.fatPct}%", style = BrandTypography.BodySmall, color = TextColor)
        }
    }
}

