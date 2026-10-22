package com.fitcal.app.plan

import com.fitcal.shared.model.LoggedMeal
import com.fitcal.shared.model.UserProfile
import com.fitcal.shared.model.WeightEntry
import com.fitcal.shared.model.WeightHistory
import kotlin.math.abs
import kotlin.math.roundToInt

/*
 * Adjusting calorie target (owner decision 2026-10-23): every two weeks, when the weight trend
 * is clearly off the plan's pace, FitCal *suggests* a small change. Nothing changes until the
 * user taps Accept. Safety rules, all enforced here:
 * - one step is at most ADJUSTMENT_STEP_MAX kcal, the total at most ADJUSTMENT_TOTAL_MAX;
 * - never below the plan's calorie floor or past the safe deficit; losing never goes above
 *   maintenance, gaining never below it;
 * - lowering calories needs enough logging and intake close to the target, so FitCal never
 *   cuts calories for someone who simply hasn't been eating to plan or logging;
 * - losing faster than planned always suggests eating more (the safer direction).
 */

const val ADJUSTMENT_INTERVAL_DAYS = 14
const val ADJUSTMENT_STEP_MAX = 150
const val ADJUSTMENT_TOTAL_MAX = 300
private const val TREND_WINDOW_DAYS = 28
private const val MIN_TREND_SPAN_DAYS = 14
private const val MIN_LOGGED_DAYS = 8          // of the last 14
private const val OFF_PACE_KG_PER_WEEK = 0.2f  // smaller gaps are noise (water, food in the gut)
private const val KCAL_PER_KG_DAY = 1100f      // 7700 kcal per kg / 7 days

/** A suggested change to the daily calorie target, with the numbers behind it. */
data class CalorieSuggestion(
    /** kcal/day to add (negative = less). */
    val deltaKcal: Int,
    val newCalories: Int,
    val actualKgPerWeek: Float,
    val plannedKgPerWeek: Float,
    val reason: String,
)

/** Applies an accepted total adjustment, keeping every safety limit. Carbs absorb the change. */
fun TargetSuggestion.withCalorieAdjustment(adjustment: Int, body: BodyStats, goal: PlanGoal): TargetSuggestion {
    if (adjustment == 0) return this
    val adj = adjustment.coerceIn(-ADJUSTMENT_TOTAL_MAX, ADJUSTMENT_TOTAL_MAX)
    var newCalories = calories + adj
    val lowest = when (goal) {
        PlanGoal.LOSE -> maxOf(body.calorieFloor, maintenance - body.maxSafeDeficit)
        PlanGoal.GAIN -> maintenance
        PlanGoal.MAINTAIN -> maxOf(body.calorieFloor, maintenance - ADJUSTMENT_TOTAL_MAX)
    }
    val highest = when (goal) {
        PlanGoal.LOSE -> maintenance
        PlanGoal.GAIN, PlanGoal.MAINTAIN -> maintenance + ADJUSTMENT_TOTAL_MAX + 500
    }
    // A plan already held at maintenance (underweight, no safe deficit) is never lowered.
    if (lowest > highest) {
        newCalories = calories
    } else {
        // Round to 10 kcal inside the limits, so rounding can never cross one.
        val lo = ((lowest + 9) / 10) * 10
        val hi = (highest / 10) * 10
        newCalories = ((newCalories / 10f).roundToInt() * 10).coerceIn(minOf(lo, hi), hi)
    }
    val carbs = ((newCalories - proteinG * 4 - fatG * 9) / 4f).roundToInt().coerceAtLeast(0)
    return copy(calories = newCalories, carbsG = carbs)
}

/** Weight change per week over the trend window, from the average of the first and last few weigh-ins. */
fun weightTrendKgPerWeek(entries: List<WeightEntry>, from: String, today: String): Float? {
    val inWindow = WeightHistory.dedupePerDay(entries)
        .filter { (daysBetween(from, it.date) ?: -1) >= 0 && (daysBetween(it.date, today) ?: -1) >= 0 }
        .sortedBy { it.date }
    if (inWindow.size < 2) return null
    val k = if (inWindow.size >= 6) 3 else 1
    val first = inWindow.take(k)
    val last = inWindow.takeLast(k)
    fun avgDay(list: List<WeightEntry>) = list.map { epochDay(it.date) ?: 0L }.average()
    val spanDays = avgDay(last) - avgDay(first)
    val firstSpan = daysBetween(inWindow.first().date, inWindow.last().date) ?: 0
    if (firstSpan < MIN_TREND_SPAN_DAYS || spanDays <= 0.0) return null
    val change = last.map { it.weightKg }.average() - first.map { it.weightKg }.average()
    return (change / (spanDays / 7.0)).toFloat()
}

/**
 * The suggestion to show today, or null. Null whenever the plan is off, it's too soon since the
 * plan started or the last change/dismissal, there isn't enough weight or logging data, the
 * pace is close enough, or the safe limits leave no room.
 */
fun UserProfile.calorieSuggestion(
    weightEntries: List<WeightEntry>,
    meals: List<LoggedMeal>,
    today: String,
): CalorieSuggestion? {
    val p = plan ?: return null
    if (resolvedTargetsMode() != TargetsMode.PLAN) return null
    val body = BodyStats.from(this) ?: return null
    val goal = PlanGoal.fromKey(goalType)
    val pace = PlanPace.fromKey(p.pace)
    if (hasReachedTarget(goal, weight, p.targetWeightKg)) return null

    // Not more often than every two weeks, counting from the start, the last change or "Not now".
    val anchor = listOfNotNull(p.startDate, p.lastAdjustmentDate, p.adjustmentDismissedDate)
        .maxByOrNull { epochDay(it) ?: 0L } ?: p.startDate
    if ((daysBetween(anchor, today) ?: 0) < ADJUSTMENT_INTERVAL_DAYS) return null

    // The trend since the later of the plan start and the last accepted change (older data
    // reflects a different target), within the last four weeks.
    val windowStart = listOfNotNull(p.startDate, p.lastAdjustmentDate, addDays(today, -(TREND_WINDOW_DAYS - 1)))
        .maxByOrNull { epochDay(it) ?: 0L } ?: return null
    val actual = weightTrendKgPerWeek(weightEntries, windowStart, today) ?: return null

    val base = planTargets(body, goal, pace)
    val current = base.withCalorieAdjustment(p.calorieAdjustment, body, goal)
    val planned = when (goal) {
        PlanGoal.LOSE -> -base.kgPerWeek
        PlanGoal.GAIN -> base.kgPerWeek
        PlanGoal.MAINTAIN -> 0f
    }
    val gap = actual - planned   // + = gaining more / losing less than planned
    if (abs(gap) < OFF_PACE_KG_PER_WEEK) return null

    // Half the implied energy gap, in 50 kcal steps, at most one step.
    val rawStep = (abs(gap) * KCAL_PER_KG_DAY / 2f / 50f).roundToInt() * 50
    val step = rawStep.coerceIn(50, ADJUSTMENT_STEP_MAX)
    val delta = if (gap > 0) -step else step

    // Lowering calories only for someone logging steadily and eating close to the target.
    if (delta < 0) {
        val last14 = (0 until 14).mapNotNull { addDays(today, -it) }.toSet()
        val byDay = meals.filter { it.date in last14 }.groupBy { it.date }
        if (byDay.size < MIN_LOGGED_DAYS) return null
        val avgIntake = byDay.values.map { day -> day.sumOf { it.calories } }.average()
        if (avgIntake > current.calories * 1.1) return null
    }

    val newTotal = (p.calorieAdjustment + delta).coerceIn(-ADJUSTMENT_TOTAL_MAX, ADJUSTMENT_TOTAL_MAX)
    val proposed = base.withCalorieAdjustment(newTotal, body, goal)
    val realDelta = proposed.calories - current.calories
    if (abs(realDelta) < 50) return null

    return CalorieSuggestion(
        deltaKcal = realDelta,
        newCalories = proposed.calories,
        actualKgPerWeek = actual,
        plannedKgPerWeek = planned,
        reason = suggestionReason(goal, actual, planned, realDelta),
    )
}

private fun kg(v: Float): String = formatKg((abs(v) * 10f).roundToInt() / 10f)

private fun trendPhrase(v: Float): String = when {
    v <= -0.05f -> "lost about ${kg(v)} kg a week"
    v >= 0.05f -> "gained about ${kg(v)} kg a week"
    else -> "stayed about the same"
}

private fun suggestionReason(goal: PlanGoal, actual: Float, planned: Float, delta: Int): String {
    val aim = when (goal) {
        PlanGoal.LOSE -> "your plan aims to lose ${kg(planned)} kg a week"
        PlanGoal.GAIN -> "your plan aims to gain ${kg(planned)} kg a week"
        PlanGoal.MAINTAIN -> "your plan aims to keep your weight steady"
    }
    val change = if (delta > 0) "${delta} kcal more a day" else "${-delta} kcal less a day"
    val why = if (goal == PlanGoal.LOSE && delta > 0) {
        " Losing faster than planned can cost muscle and energy, so eating a little more keeps it steady."
    } else {
        ""
    }
    return "Over the last few weeks you've ${trendPhrase(actual)}; $aim. $change would bring you closer.$why"
}

/** Accepting a suggestion: adds it to the plan's adjustment and recomputes the targets. */
fun UserProfile.acceptCalorieSuggestion(s: CalorieSuggestion, today: String): UserProfile {
    val p = plan ?: return this
    val updated = copy(
        plan = p.copy(
            calorieAdjustment = (p.calorieAdjustment + s.deltaKcal).coerceIn(-ADJUSTMENT_TOTAL_MAX, ADJUSTMENT_TOTAL_MAX),
            lastAdjustmentDate = today,
        )
    )
    return updated.withPlanTargetsRecomputed()
}

/** "Not now": no change, and no new suggestion for another two weeks. */
fun UserProfile.dismissCalorieSuggestion(today: String): UserProfile {
    val p = plan ?: return this
    return copy(plan = p.copy(adjustmentDismissedDate = today))
}
