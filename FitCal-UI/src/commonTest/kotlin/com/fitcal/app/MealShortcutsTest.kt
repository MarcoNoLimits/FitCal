package com.fitcal.app

import com.fitcal.app.ui.screens.dashboard.components.copyAsNewLog
import com.fitcal.app.ui.screens.dashboard.components.localDateKey
import com.fitcal.app.ui.screens.dashboard.components.mealStreak
import com.fitcal.app.ui.screens.dashboard.components.minutesOfDay
import com.fitcal.app.ui.screens.dashboard.components.quickLogMeals
import com.fitcal.app.ui.screens.dashboard.components.streakChipLabel
import com.fitcal.shared.model.FoodItem
import com.fitcal.shared.model.LoggedMeal
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** Dashboard shortcuts: log again, favourites/recents row, and the logging streak. */
class MealShortcutsTest {

    private val today = "2026-10-22"

    private fun meal(id: String, name: String, date: String, time: String = "12:00 PM", favorite: Boolean = false) =
        LoggedMeal(id, name, 400, 20f, 40f, 10f, time, date, favorite = favorite)

    // ── Streak ──

    @Test
    fun noMealsMeansNoStreak() {
        assertEquals(0, mealStreak(emptyList(), today))
    }

    @Test
    fun consecutiveDaysEndingTodayCount() {
        val dates = listOf("2026-10-22", "2026-10-21", "2026-10-20", "2026-10-20")
        assertEquals(3, mealStreak(dates, today))
    }

    @Test
    fun todayNotLoggedYetKeepsYesterdaysStreak() {
        val dates = listOf("2026-10-21", "2026-10-20", "2026-10-19", "2026-10-18", "2026-10-17")
        assertEquals(5, mealStreak(dates, today))
    }

    @Test
    fun aGapEndsTheStreak() {
        // Logged today and yesterday, missed the 20th, logged before that.
        val dates = listOf("2026-10-22", "2026-10-21", "2026-10-19", "2026-10-18")
        assertEquals(2, mealStreak(dates, today))
    }

    @Test
    fun lastLogTwoDaysAgoIsNoStreak() {
        assertEquals(0, mealStreak(listOf("2026-10-20", "2026-10-19"), today))
    }

    @Test
    fun streakCrossesMonthAndYearBoundaries() {
        val dates = listOf("2027-01-01", "2026-12-31", "2026-12-30")
        assertEquals(3, mealStreak(dates, "2027-01-01"))
        assertEquals(2, mealStreak(listOf("2026-03-01", "2026-02-28"), "2026-03-01"))
    }

    @Test
    fun blankAndBrokenDatesAreIgnored() {
        assertEquals(2, mealStreak(listOf("", "garbage", "2026-10-22", "2026-10-21"), today))
    }

    @Test
    fun futureDatesDontExtendTheStreak() {
        assertEquals(1, mealStreak(listOf("2026-10-23", "2026-10-22"), today))
    }

    @Test
    fun localDayFollowsTheUsersTimeZone() {
        // 2026-10-21T22:30:00Z
        val instant = 1_792_621_800_000L
        assertEquals("2026-10-21", localDateKey(instant, 0))
        assertEquals("2026-10-22", localDateKey(instant, 120))   // UTC+2: already the 22nd
        assertEquals("2026-10-21", localDateKey(instant, -300))  // UTC-5: still the 21st
        // Just before and at local midnight.
        assertEquals("2026-10-21", localDateKey(instant + 90 * 60_000L - 1, 0))
        assertEquals("2026-10-22", localDateKey(instant + 90 * 60_000L, 0))
    }

    @Test
    fun streakUsesTheLocalDayNotUtc() {
        val instant = 1_792_621_800_000L   // 2026-10-21T22:30:00Z
        val dates = listOf("2026-10-22", "2026-10-21")
        // In UTC+2 it's the 22nd and the 22nd is logged: 2 days.
        assertEquals(2, mealStreak(dates, instant, 120))
        // In UTC it's still the 21st; the meal dated the 22nd is "tomorrow" and doesn't count.
        assertEquals(1, mealStreak(dates, instant, 0))
        // In UTC-5 on the 21st with only the 20th logged: yesterday keeps the streak.
        assertEquals(1, mealStreak(listOf("2026-10-20"), instant, -300))
    }

    @Test
    fun chipOnlyFromTwoDays() {
        assertNull(streakChipLabel(0))
        assertNull(streakChipLabel(1))
        assertEquals("🔥 2-day streak", streakChipLabel(2))
        assertEquals("🔥 5-day streak", streakChipLabel(5))
    }

    // ── Log again ──

    @Test
    fun copyGetsANewIdTodaysDateAndKeepsItems() {
        val items = listOf(FoodItem("Chicken Breast", 150, 248, 46.5f, 0f, 5.4f, "high"))
        val original = LoggedMeal("orig", "Chicken", 248, 46.5f, 0f, 5.4f, "08:15 AM", "2026-10-15", items, favorite = true)

        val copy = original.copyAsNewLog(newId = "new-id", today = today, time = "07:40 PM")

        assertEquals("new-id", copy.id)
        assertNotEquals(original.id, copy.id)
        assertEquals(today, copy.date)
        assertEquals("07:40 PM", copy.timestamp)
        assertEquals(items, copy.items)
        assertEquals(original.name, copy.name)
        assertEquals(original.calories, copy.calories)
        assertEquals(original.protein, copy.protein)
        assertEquals(original.carbs, copy.carbs)
        assertEquals(original.fat, copy.fat)
        // The original stays the favourite; the copy is a plain log entry.
        assertFalse(copy.favorite)
        assertTrue(original.favorite)
        assertEquals("2026-10-15", original.date)
    }

    // ── Favourites and recents ──

    @Test
    fun favouritesWinOverRecents() {
        val meals = listOf(
            meal("1", "Oats", "2026-10-20", favorite = true),
            meal("2", "Pizza", "2026-10-22"),
            meal("3", "Salad", "2026-10-21", favorite = true),
        )
        val row = quickLogMeals(meals)
        assertTrue(row.favorites)
        assertEquals(listOf("Salad", "Oats"), row.meals.map { it.name })
    }

    @Test
    fun withoutFavouritesShowsRecentDistinctMeals() {
        val meals = listOf(
            meal("1", "Oats", "2026-10-21", "08:00 AM"),
            meal("2", "Oats", "2026-10-22", "08:00 AM"),
            meal("3", "Pizza", "2026-10-22", "07:00 PM"),
            meal("4", "salad ", "2026-10-20"),
            meal("5", "Salad", "2026-10-19"),
        )
        val row = quickLogMeals(meals)
        assertFalse(row.favorites)
        assertEquals(listOf("Pizza", "Oats", "salad "), row.meals.map { it.name })
        assertEquals("2", row.meals[1].id)   // the newest Oats
    }

    @Test
    fun quickLogRowIsCapped() {
        val meals = (1..20).map { meal("$it", "Meal $it", "2026-10-${(it % 9 + 10)}") }
        assertEquals(8, quickLogMeals(meals).meals.size)
        assertTrue(quickLogMeals(emptyList()).meals.isEmpty())
    }

    @Test
    fun displayTimesSortWithinADay() {
        assertEquals(0, minutesOfDay("12:00 AM"))
        assertEquals(12 * 60, minutesOfDay("12:00 PM"))
        assertEquals(14 * 60 + 30, minutesOfDay("02:30 PM"))
        assertNull(minutesOfDay("noon"))
    }
}
