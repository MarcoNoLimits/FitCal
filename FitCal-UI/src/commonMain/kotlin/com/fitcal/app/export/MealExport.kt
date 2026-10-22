package com.fitcal.app.export

import com.fitcal.app.plan.addDays
import com.fitcal.app.plan.daysBetween
import com.fitcal.shared.model.LoggedMeal
import com.fitcal.shared.model.WeightEntry
import com.fitcal.shared.model.WeightHistory
import kotlin.math.roundToInt

/*
 * Export for a dietitian or coach (owner decision 2026-10-23): CSV is free; a PDF report is a
 * Premium perk planned for v1.1 (see tasks/NEXT.md). Opens in Excel, Google Sheets and
 * Numbers. Meal photos are never included.
 */

enum class ExportRange(val days: Int?, val label: String) {
    DAYS_30(30, "Last 30 days"),
    DAYS_90(90, "Last 90 days"),
    ALL(null, "Everything"),
}

/** Meals and weigh-ins inside [range], oldest first. */
fun mealsInRange(meals: List<LoggedMeal>, today: String, range: ExportRange): List<LoggedMeal> {
    val from = range.days?.let { addDays(today, -(it - 1)) }
    return meals
        .map { if (it.date.isBlank()) it.copy(date = today) else it }
        .filter { m -> from == null || ((daysBetween(from, m.date) ?: -1) >= 0 && (daysBetween(m.date, today) ?: -1) >= 0) }
        .sortedWith(compareBy({ it.date }, { minutesOfDay(it.timestamp) }))
}

/** "08:30 AM" → minutes since midnight, for sorting; unknown times sort first. */
internal fun minutesOfDay(time: String): Int {
    val m = Regex("""(\d{1,2}):(\d{2})\s*([AaPp][Mm])?""").find(time) ?: return -1
    var h = m.groupValues[1].toInt() % 12
    if (m.groupValues[3].uppercase() == "PM") h += 12
    if (m.groupValues[3].isEmpty()) h = m.groupValues[1].toInt()
    return h * 60 + m.groupValues[2].toInt()
}

/** RFC 4180 field: quoted when it holds a comma, quote or line break; formulas neutralised. */
internal fun csvField(raw: String): String {
    // A leading = + - @ makes spreadsheets run it as a formula (CSV injection); prefix with '.
    val safe = if (raw.isNotEmpty() && raw[0] in "=+-@") "'$raw" else raw
    return if (safe.any { it == ',' || it == '"' || it == '\n' || it == '\r' }) {
        "\"" + safe.replace("\"", "\"\"") + "\""
    } else {
        safe
    }
}

private fun oneDecimal(v: Float): String {
    val r = (v * 10f).roundToInt()
    return if (r % 10 == 0) "${r / 10}" else "${r / 10}.${kotlin.math.abs(r % 10)}"
}

/**
 * One row per meal, then (after a blank line) one row per weigh-in, so a coach sees both in
 * one file. Foods list each item with its grams.
 */
fun buildMealsCsv(meals: List<LoggedMeal>, weights: List<WeightEntry>): String {
    val sb = StringBuilder()
    sb.append("date,time,meal,calories_kcal,protein_g,carbs_g,fat_g,foods\r\n")
    for (m in meals) {
        val foods = m.items.joinToString("; ") { "${it.item} (${it.weight_est_g} g)" }
        sb.append(
            listOf(
                m.date, m.timestamp, m.name, m.calories.toString(),
                oneDecimal(m.protein), oneDecimal(m.carbs), oneDecimal(m.fat), foods,
            ).joinToString(",") { csvField(it) }
        ).append("\r\n")
    }
    val dedupedWeights = WeightHistory.dedupePerDay(weights).sortedBy { it.date }
    if (dedupedWeights.isNotEmpty()) {
        sb.append("\r\n")
        sb.append("date,weight_kg\r\n")
        dedupedWeights.forEach { sb.append("${it.date},${oneDecimal(it.weightKg)}\r\n") }
    }
    return sb.toString()
}

/** Weigh-ins inside the same window as the exported meals. */
fun weightsInRange(entries: List<WeightEntry>, today: String, range: ExportRange): List<WeightEntry> {
    val from = range.days?.let { addDays(today, -(it - 1)) }
    return entries.filter { e -> from == null || ((daysBetween(from, e.date) ?: -1) >= 0 && (daysBetween(e.date, today) ?: -1) >= 0) }
}

fun exportFileName(today: String, range: ExportRange): String =
    "fitcal-meals-${range.name.lowercase().replace('_', '-')}-$today.csv"
