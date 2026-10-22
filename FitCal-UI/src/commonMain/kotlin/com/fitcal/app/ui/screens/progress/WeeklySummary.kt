package com.fitcal.app.ui.screens.progress

import com.fitcal.app.loadPreference
import com.fitcal.app.plan.addDays
import com.fitcal.app.plan.epochDay
import com.fitcal.app.savePreference
import com.fitcal.shared.model.LoggedMeal

/*
 * Weekly summary (owner decision 2026-10-23): a dashboard card on the first open of each week
 * about the last full Monday–Sunday week, plus a Monday-morning notification that carries no
 * numbers (it shows on the lock screen) and just opens the app. Both can be turned off.
 */

/** The last completed Monday–Sunday week before [today]'s week. */
data class SummaryWeek(val monday: String, val sunday: String)

/** 0 = Monday … 6 = Sunday, for a YYYY-MM-DD date (1970-01-05 was a Monday). */
fun dayOfWeek(date: String): Int? = epochDay(date)?.let { (((it - 4) % 7 + 7) % 7).toInt() }

fun lastCompletedWeek(today: String): SummaryWeek? {
    val dow = dayOfWeek(today) ?: return null
    val thisMonday = addDays(today, -dow) ?: return null
    val monday = addDays(thisMonday, -7) ?: return null
    val sunday = addDays(thisMonday, -1) ?: return null
    return SummaryWeek(monday, sunday)
}

/** The card's content: the summary of [week], or null when nothing was logged that week. */
fun weeklySummaryFor(meals: List<LoggedMeal>, week: SummaryWeek, calorieTarget: Int?): WeekSummary? =
    weekSummary(meals, week.sunday, calorieTarget).takeIf { it.loggedDays > 0 }

object WeeklySummaryPrefs {
    private const val KEY_SEEN_WEEK = "weekly_summary_seen_week"
    private const val KEY_NOTIFY = "weekly_summary_notify"

    var reader: (String, String) -> String = { k, d -> loadPreference(k, d) }
    var writer: (String, String) -> Unit = { k, v -> savePreference(k, v) }

    fun resetToDefaults() {
        reader = { k, d -> loadPreference(k, d) }
        writer = { k, v -> savePreference(k, v) }
    }

    /** Whether the card for [week] is still to be shown (not dismissed yet). */
    fun isDue(week: SummaryWeek): Boolean = reader(KEY_SEEN_WEEK, "") != week.monday

    fun markSeen(week: SummaryWeek) = writer(KEY_SEEN_WEEK, week.monday)

    /** The Monday notification; on by default. */
    fun notificationsEnabled(): Boolean = reader(KEY_NOTIFY, "true").toBoolean()

    fun setNotificationsEnabled(enabled: Boolean) = writer(KEY_NOTIFY, enabled.toString())
}
