package com.fitcal.shared.model

import kotlinx.serialization.Serializable

/** One weigh-in: the user's weight on a day (YYYY-MM-DD). One entry per day; the latest wins. */
@Serializable
data class WeightEntry(
    val date: String,
    val weightKg: Float,
)

/** Pure helpers for the weight history: one entry per day, sorted oldest first. */
object WeightHistory {
    /** Weights outside this range are typos or unit mix-ups and are not stored. */
    val PLAUSIBLE_KG = 20f..400f

    fun isValid(entry: WeightEntry): Boolean =
        entry.weightKg in PLAUSIBLE_KG && isDateKey(entry.date)

    /**
     * Keeps one entry per day. When a day appears more than once, the one that comes last in
     * [entries] wins (entries are recorded in order, so that is the latest). Result is sorted by date.
     */
    fun dedupePerDay(entries: List<WeightEntry>): List<WeightEntry> {
        val byDay = LinkedHashMap<String, WeightEntry>()
        for (e in entries) {
            if (!isValid(e)) continue
            byDay[e.date] = e
        }
        return byDay.values.sortedBy { it.date }
    }

    /** Adds or replaces the entry for [entry]'s day. Invalid entries leave the list as it was. */
    fun upsert(entries: List<WeightEntry>, entry: WeightEntry): List<WeightEntry> =
        if (!isValid(entry)) dedupePerDay(entries) else dedupePerDay(entries + entry)

    /** Adds [incoming] days that [local] doesn't have yet; days already on this device keep their value. */
    fun fillMissingDays(local: List<WeightEntry>, incoming: List<WeightEntry>): List<WeightEntry> {
        val known = local.mapTo(HashSet()) { it.date }
        return dedupePerDay(incoming.filter { it.date !in known } + local)
    }

    private fun isDateKey(date: String): Boolean =
        date.length == 10 && date[4] == '-' && date[7] == '-' &&
            date.substring(0, 4).all { it.isDigit() } &&
            date.substring(5, 7).toIntOrNull()?.let { it in 1..12 } == true &&
            date.substring(8, 10).toIntOrNull()?.let { it in 1..31 } == true
}
