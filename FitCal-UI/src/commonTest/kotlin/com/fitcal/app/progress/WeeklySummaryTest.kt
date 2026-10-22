package com.fitcal.app.progress

import com.fitcal.app.ui.screens.progress.SummaryWeek
import com.fitcal.app.ui.screens.progress.WeeklySummaryPrefs
import com.fitcal.app.ui.screens.progress.dayOfWeek
import com.fitcal.app.ui.screens.progress.lastCompletedWeek
import com.fitcal.app.ui.screens.progress.weeklySummaryFor
import com.fitcal.shared.model.LoggedMeal
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class WeeklySummaryTest {

    private val store = mutableMapOf<String, String>()

    @BeforeTest
    fun setUp() {
        store.clear()
        WeeklySummaryPrefs.reader = { k, d -> store[k] ?: d }
        WeeklySummaryPrefs.writer = { k, v -> store[k] = v }
    }

    @AfterTest
    fun tearDown() = WeeklySummaryPrefs.resetToDefaults()

    @Test
    fun weekdays() {
        assertEquals(0, dayOfWeek("2026-10-19"))   // Monday
        assertEquals(6, dayOfWeek("2026-10-25"))   // Sunday
    }

    @Test
    fun lastCompletedWeekIsTheMondayToSundayBefore() {
        val expected = SummaryWeek("2026-10-12", "2026-10-18")
        assertEquals(expected, lastCompletedWeek("2026-10-19"))   // Monday
        assertEquals(expected, lastCompletedWeek("2026-10-23"))   // Friday
        assertEquals(expected, lastCompletedWeek("2026-10-25"))   // Sunday
    }

    @Test
    fun summaryOnlyWhenSomethingWasLoggedThatWeek() {
        val week = SummaryWeek("2026-10-12", "2026-10-18")
        assertNull(weeklySummaryFor(emptyList(), week, null))
        val meals = listOf(
            LoggedMeal("a", "Meal", 1800, 0f, 0f, 0f, "", "2026-10-13"),
            LoggedMeal("b", "Meal", 900, 0f, 0f, 0f, "", "2026-10-20"),   // this week: not counted
        )
        val s = assertNotNull(weeklySummaryFor(meals, week, 2000))
        assertEquals(1, s.loggedDays)
        assertEquals(1800, s.averageCalories)
    }

    @Test
    fun cardShowsOncePerWeekAndNotificationsDefaultOn() {
        val week = SummaryWeek("2026-10-12", "2026-10-18")
        assertTrue(WeeklySummaryPrefs.isDue(week))
        WeeklySummaryPrefs.markSeen(week)
        assertFalse(WeeklySummaryPrefs.isDue(week))
        assertTrue(WeeklySummaryPrefs.isDue(SummaryWeek("2026-10-19", "2026-10-25")))
        assertTrue(WeeklySummaryPrefs.notificationsEnabled())
    }
}
