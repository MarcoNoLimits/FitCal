package com.fitcal.app.ui.screens.dashboard.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.fitcal.app.plan.dateFromEpochDay
import com.fitcal.app.plan.epochDay
import com.fitcal.app.ui.components.PressableBox
import com.fitcal.app.ui.theme.BorderColor
import com.fitcal.app.ui.theme.BrandTypography
import com.fitcal.app.ui.theme.MutedTextColor
import com.fitcal.app.ui.theme.PrimaryAccent
import com.fitcal.app.ui.theme.RadiusM
import com.fitcal.app.ui.theme.TextColor
import com.fitcal.shared.model.LoggedMeal

// ── Log again ──────────────────────────────────────────────────────────────────

/**
 * A fresh copy of this meal, items included, logged on [today] at [time]. It never uses a scan.
 * The copy gets its own [newId] and is not starred: the original stays the favourite, so the
 * favourites row doesn't fill up with duplicates.
 */
fun LoggedMeal.copyAsNewLog(newId: String, today: String, time: String): LoggedMeal =
    // The photo belongs to the original plate, so the copy has none.
    copy(id = newId, date = today, timestamp = time, favorite = false, photoPath = null)

// ── Favourites and recents ─────────────────────────────────────────────────────

/** What the quick-log row above the history shows. */
data class QuickLogMeals(val favorites: Boolean, val meals: List<LoggedMeal>)

const val QUICK_LOG_LIMIT = 8

/**
 * Starred meals, newest first, one per name. When nothing is starred, the most recent distinct
 * meals instead, so the row is useful from the first day.
 */
fun quickLogMeals(allMeals: List<LoggedMeal>, limit: Int = QUICK_LOG_LIMIT): QuickLogMeals {
    val newestFirst = allMeals.sortedByDescending { mealSortKey(it) }
    val starred = newestFirst.filter { it.favorite }
    val source = starred.ifEmpty { newestFirst }
    return QuickLogMeals(
        favorites = starred.isNotEmpty(),
        meals = source.distinctBy { it.name.trim().lowercase() }.take(limit),
    )
}

/** Orders meals by day, then by time of day. Meals without a usable date sort last. */
fun mealSortKey(meal: LoggedMeal): Long {
    val day = epochDay(meal.date) ?: return Long.MIN_VALUE
    return day * MINUTES_PER_DAY + (minutesOfDay(meal.timestamp) ?: 0)
}

/** "02:30 PM" → 870. Null when the display time can't be read. */
fun minutesOfDay(timestamp: String): Int? {
    val parts = timestamp.trim().split(" ")
    val clock = parts.firstOrNull()?.split(":") ?: return null
    if (clock.size < 2) return null
    var hour = clock[0].toIntOrNull() ?: return null
    val minute = clock[1].toIntOrNull() ?: return null
    when (parts.getOrNull(1)?.uppercase()) {
        "PM" -> if (hour != 12) hour += 12
        "AM" -> if (hour == 12) hour = 0
    }
    if (hour !in 0..23 || minute !in 0..59) return null
    return hour * 60 + minute
}

private const val MINUTES_PER_DAY = 24 * 60L

// ── Streak ─────────────────────────────────────────────────────────────────────

/**
 * Consecutive days with at least one logged meal, ending today or, if nothing is logged yet
 * today, yesterday. Dates are local YYYY-MM-DD keys, the same ones meals are stored under.
 * Unreadable or blank dates are ignored.
 */
fun mealStreak(mealDates: Iterable<String>, today: String): Int {
    val todayDay = epochDay(today) ?: return 0
    val days = mealDates.mapNotNullTo(HashSet()) { epochDay(it) }
    var cursor = when {
        todayDay in days -> todayDay
        (todayDay - 1) in days -> todayDay - 1
        else -> return 0
    }
    var streak = 0
    while (cursor in days) {
        streak++
        cursor--
    }
    return streak
}

/** The same streak, with today taken from a clock reading in the user's time zone. */
fun mealStreak(mealDates: Iterable<String>, nowEpochMillis: Long, utcOffsetMinutes: Int): Int =
    mealStreak(mealDates, localDateKey(nowEpochMillis, utcOffsetMinutes))

/** The local YYYY-MM-DD for an instant, at the given offset from UTC. */
fun localDateKey(epochMillis: Long, utcOffsetMinutes: Int): String {
    val localMillis = epochMillis + utcOffsetMinutes * 60_000L
    return dateFromEpochDay(localMillis.floorDiv(MILLIS_PER_DAY))
}

private const val MILLIS_PER_DAY = 86_400_000L

/** The chip only appears from a 2-day streak; one day isn't a streak yet. */
fun streakChipLabel(streak: Int): String? = if (streak >= 2) "🔥 $streak-day streak" else null

// ── UI ─────────────────────────────────────────────────────────────────────────

// Warm amber pair for the streak chip; local to the dashboard shortcuts.
private val StreakSoft = Color(0xFFFFF7E6)
private val StreakBorder = Color(0xFFFCD9A0)

@Composable
fun StreakChip(label: String, modifier: Modifier = Modifier) {
    Text(
        text = label,
        style = BrandTypography.BodySmall,
        fontWeight = FontWeight.Bold,
        color = TextColor,
        modifier = modifier
            .background(StreakSoft, RoundedCornerShape(50))
            .border(1.dp, StreakBorder, RoundedCornerShape(50))
            .padding(horizontal = 12.dp, vertical = 6.dp)
            .semantics { contentDescription = label.removePrefix("🔥 ") }
    )
}

/** One-tap logging: a horizontal row of favourites, or recent meals when none are starred. */
@Composable
fun QuickLogRow(quickLog: QuickLogMeals, onLog: (LoggedMeal) -> Unit, modifier: Modifier = Modifier) {
    if (quickLog.meals.isEmpty()) return
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            text = if (quickLog.favorites) "FAVOURITES · TAP TO LOG TODAY" else "RECENT · TAP TO LOG TODAY",
            style = BrandTypography.Eyebrow,
            color = MutedTextColor
        )
        Row(
            modifier = Modifier.horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            quickLog.meals.forEach { meal ->
                PressableBox(
                    onTap = { onLog(meal) },
                    modifier = Modifier.semantics {
                        contentDescription = "Log ${meal.name} again today, ${meal.calories} kcal"
                    }
                ) {
                    Column(
                        modifier = Modifier
                            .widthIn(min = 120.dp, max = 180.dp)
                            .background(Color.White, RoundedCornerShape(RadiusM))
                            .border(1.dp, BorderColor, RoundedCornerShape(RadiusM))
                            .padding(horizontal = 14.dp, vertical = 10.dp),
                        verticalArrangement = Arrangement.spacedBy(2.dp),
                        horizontalAlignment = Alignment.Start
                    ) {
                        Text(
                            text = (if (meal.favorite) "★ " else "") + meal.name,
                            style = BrandTypography.Body,
                            fontWeight = FontWeight.SemiBold,
                            color = TextColor,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            text = "+ ${meal.calories} kcal",
                            style = BrandTypography.BodySmall,
                            fontWeight = FontWeight.Bold,
                            color = PrimaryAccent
                        )
                    }
                }
            }
        }
    }
}
