package com.fitcal.app.ui.screens.progress

import com.fitcal.app.plan.addDays
import com.fitcal.app.plan.daysBetween
import com.fitcal.shared.model.LoggedMeal
import com.fitcal.shared.model.WeightEntry
import com.fitcal.shared.model.WeightHistory
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.roundToInt

/*
 * Pure math behind the Progress screen: which weigh-ins fall in the chosen window, the weight
 * chart's axis scale, and the 7-day calorie and protein bars. Kept free of Compose so it is tested.
 */

enum class ProgressRange(val days: Int, val label: String) {
    DAYS_30(30, "30 days"),
    DAYS_90(90, "90 days"),
}

/** The chart's y-axis never spans less than this, so a flat week doesn't look like a cliff. */
const val MIN_WEIGHT_SPAN_KG = 2f

/** Tick steps the axis can use, smallest first. */
private val NICE_STEPS_KG = listOf(0.5f, 1f, 2f, 2.5f, 5f, 10f, 20f, 25f, 50f)

/** Linear y-axis for the weight chart, in kg, with evenly spaced [ticks] from [minKg] to [maxKg]. */
data class WeightChartScale(
    val minKg: Float,
    val maxKg: Float,
    val stepKg: Float,
    val ticks: List<Float>,
) {
    /** 0 at the bottom of the chart, 1 at the top; values outside the axis are clamped. */
    fun yFraction(kg: Float): Float =
        if (maxKg <= minKg) 0.5f else ((kg - minKg) / (maxKg - minKg)).coerceIn(0f, 1f)
}

/** The smallest "nice" step at least [raw] kg wide. */
fun niceStepKg(raw: Float): Float =
    NICE_STEPS_KG.firstOrNull { it >= raw } ?: (ceil(raw / 50f) * 50f)

/**
 * The y-axis for [weightsKg] and the plan's [targetKg] (so the target line is always on the
 * chart). Spans at least [MIN_WEIGHT_SPAN_KG], centred on the data, and is widened outwards
 * to whole multiples of a nice step. Null when there is nothing to draw.
 */
fun weightChartScale(weightsKg: List<Float>, targetKg: Float? = null, tickIntervals: Int = 4): WeightChartScale? {
    val values = weightsKg + listOfNotNull(targetKg)
    if (values.isEmpty()) return null
    var lo = values.min()
    var hi = values.max()
    if (hi - lo < MIN_WEIGHT_SPAN_KG) {
        val mid = (lo + hi) / 2f
        lo = mid - MIN_WEIGHT_SPAN_KG / 2f
        hi = mid + MIN_WEIGHT_SPAN_KG / 2f
    }
    val step = niceStepKg((hi - lo) / tickIntervals.coerceAtLeast(1))
    val min = floor(lo / step) * step
    var max = ceil(hi / step) * step
    if (max <= min) max = min + step
    val count = ((max - min) / step).roundToInt()
    val ticks = (0..count).map { min + it * step }
    return WeightChartScale(min, max, step, ticks)
}

/** The first day of a [days]-long window that ends [today] (inclusive). */
fun windowStart(today: String, days: Int): String = addDays(today, -(days - 1)) ?: today

/** Weigh-ins from the window's first day through [today], one per day, oldest first. */
fun entriesInWindow(entries: List<WeightEntry>, today: String, days: Int): List<WeightEntry> {
    val start = windowStart(today, days)
    return WeightHistory.dedupePerDay(entries).filter { e ->
        val fromStart = daysBetween(start, e.date) ?: return@filter false
        fromStart >= 0 && (daysBetween(e.date, today) ?: -1) >= 0
    }
}

/** Where [date] sits along the x-axis of a [days]-long window ending [today]: 0 = first day, 1 = today. */
fun xFraction(date: String, today: String, days: Int): Float {
    if (days <= 1) return 1f
    val index = daysBetween(windowStart(today, days), date) ?: return 0f
    return (index.toFloat() / (days - 1)).coerceIn(0f, 1f)
}

/** Calories and protein eaten on one day. */
data class DayTotals(val date: String, val calories: Int, val proteinG: Int)

/**
 * Totals for each of the [days] days ending [today], oldest first; days without meals are 0.
 * Meals saved without a date count as today, as on the dashboard.
 */
fun dailyTotals(meals: List<LoggedMeal>, today: String, days: Int = 7): List<DayTotals> {
    val byDay = meals.groupBy { it.date.ifBlank { today } }
    return (days - 1 downTo 0).map { back ->
        val date = addDays(today, -back) ?: today
        val dayMeals = byDay[date].orEmpty()
        DayTotals(
            date = date,
            calories = dayMeals.sumOf { it.calories },
            proteinG = dayMeals.sumOf { it.protein.toDouble() }.roundToInt(),
        )
    }
}

/**
 * The value a full-height bar stands for: the largest day or the target, whichever is higher,
 * so the target line always fits. 0 when there is nothing to show.
 */
fun barScale(values: List<Int>, target: Int?): Int =
    maxOf(values.maxOrNull() ?: 0, target?.takeIf { it > 0 } ?: 0)

/** Height of a bar as a share of the chart (0..1). */
fun barFraction(value: Int, scale: Int): Float =
    if (scale <= 0) 0f else (value.toFloat() / scale).coerceIn(0f, 1f)

/** Weight change from the first to the last weigh-in, or null with fewer than two. */
fun weightChangeKg(entries: List<WeightEntry>): Float? =
    if (entries.size < 2) null else entries.last().weightKg - entries.first().weightKg

private val WEEKDAY_LETTERS = listOf("Th", "Fr", "Sa", "Su", "Mo", "Tu", "We") // 1970-01-01 was a Thursday

/** Two-letter weekday for a YYYY-MM-DD date ("Mo"), or "" if it isn't one. */
fun weekdayShort(date: String): String {
    val day = com.fitcal.app.plan.epochDay(date) ?: return ""
    return WEEKDAY_LETTERS[(((day % 7) + 7) % 7).toInt()]
}
