package com.fitcal.app

import com.fitcal.app.plan.BodyStats
import com.fitcal.app.plan.PlanGoal
import com.fitcal.app.plan.PlanPace
import com.fitcal.app.plan.checkIn
import com.fitcal.app.plan.checkInWithEntry
import com.fitcal.app.plan.startPlan
import com.fitcal.app.plan.weightEntryForChange
import com.fitcal.app.ui.components.ActivityLevel
import com.fitcal.app.ui.screens.progress.barFraction
import com.fitcal.app.ui.screens.progress.barScale
import com.fitcal.app.ui.screens.progress.dailyTotals
import com.fitcal.app.ui.screens.progress.entriesInWindow
import com.fitcal.app.ui.screens.progress.niceStepKg
import com.fitcal.app.ui.screens.progress.weekdayShort
import com.fitcal.app.ui.screens.progress.weightChangeKg
import com.fitcal.app.ui.screens.progress.weightChartScale
import com.fitcal.app.ui.screens.progress.windowStart
import com.fitcal.app.ui.screens.progress.xFraction
import com.fitcal.shared.model.LoggedMeal
import com.fitcal.shared.model.UserProfile
import com.fitcal.shared.model.WeightEntry
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** The Progress screen's math: chart scale, window, 7-day bars, and weigh-ins from check-ins. */
class ProgressMathTest {

    private val today = "2026-10-22"
    private val eps = 0.001f

    private fun assertTicks(expected: List<Float>, actual: List<Float>) {
        assertEquals(expected.size, actual.size, "tick count: $actual")
        expected.zip(actual).forEach { (e, a) -> assertEquals(e, a, eps) }
    }

    // ── Weight chart scale ──────────────────────────────────────────────────────

    @Test
    fun scaleIncludesTheTargetAndSnapsToNiceSteps() {
        val s = assertNotNull(weightChartScale(listOf(80f, 79f, 78.5f), targetKg = 72f))
        assertEquals(2f, s.stepKg, eps)
        assertEquals(72f, s.minKg, eps)
        assertEquals(80f, s.maxKg, eps)
        assertTicks(listOf(72f, 74f, 76f, 78f, 80f), s.ticks)
    }

    @Test
    fun scaleWidensOutwardsToWholeSteps() {
        val s = assertNotNull(weightChartScale(listOf(81.3f, 77.6f)))
        assertEquals(1f, s.stepKg, eps)
        assertEquals(77f, s.minKg, eps)
        assertEquals(82f, s.maxKg, eps)
        assertTrue(s.minKg <= 77.6f && s.maxKg >= 81.3f)
    }

    @Test
    fun flatWeightStillSpansAtLeastTwoKilos() {
        val s = assertNotNull(weightChartScale(listOf(70f, 70.4f)))
        assertEquals(0.5f, s.stepKg, eps)
        assertEquals(69f, s.minKg, eps)
        assertEquals(71.5f, s.maxKg, eps)
        assertTrue(s.maxKg - s.minKg >= 2f)
        assertTicks(listOf(69f, 69.5f, 70f, 70.5f, 71f, 71.5f), s.ticks)
    }

    @Test
    fun singleWeighInSitsInTheMiddle() {
        val s = assertNotNull(weightChartScale(listOf(75f)))
        assertEquals(74f, s.minKg, eps)
        assertEquals(76f, s.maxKg, eps)
        assertEquals(0.5f, s.yFraction(75f), eps)
    }

    @Test
    fun wideRangeUsesBiggerSteps() {
        val s = assertNotNull(weightChartScale(listOf(100f, 60f)))
        assertEquals(10f, s.stepKg, eps)
        assertTicks(listOf(60f, 70f, 80f, 90f, 100f), s.ticks)
    }

    @Test
    fun noDataMeansNoChart() {
        assertNull(weightChartScale(emptyList()))
        // The target alone still gives an axis (the screen shows the empty state instead).
        assertNotNull(weightChartScale(emptyList(), targetKg = 70f))
    }

    @Test
    fun yFractionIsBottomToTopAndClamped() {
        val s = assertNotNull(weightChartScale(listOf(80f), targetKg = 72f))
        assertEquals(0f, s.yFraction(s.minKg), eps)
        assertEquals(1f, s.yFraction(s.maxKg), eps)
        assertEquals(0f, s.yFraction(10f), eps)
        assertEquals(1f, s.yFraction(500f), eps)
    }

    @Test
    fun niceStepsRoundUp() {
        assertEquals(0.5f, niceStepKg(0.1f), eps)
        assertEquals(1f, niceStepKg(0.9f), eps)
        assertEquals(2.5f, niceStepKg(2.1f), eps)
        assertEquals(5f, niceStepKg(3f), eps)
        assertEquals(100f, niceStepKg(60f), eps)
    }

    // ── Window and x-axis ───────────────────────────────────────────────────────

    @Test
    fun windowCoversTheLastNDaysIncludingToday() {
        assertEquals("2026-09-23", windowStart(today, 30))
        assertEquals("2026-07-25", windowStart(today, 90))
        val entries = listOf(
            WeightEntry("2026-09-22", 81f),   // one day too old
            WeightEntry("2026-09-23", 80f),
            WeightEntry("2026-10-22", 78f),
            WeightEntry("2026-10-23", 77f),   // tomorrow (clock change): not shown
        )
        assertEquals(listOf("2026-09-23", "2026-10-22"), entriesInWindow(entries, today, 30).map { it.date })
        assertEquals(4 - 1, entriesInWindow(entries, today, 90).size)
    }

    @Test
    fun windowKeepsOneEntryPerDay() {
        val entries = listOf(
            WeightEntry("2026-10-20", 80f),
            WeightEntry("2026-10-20", 79.4f),
            WeightEntry("2026-10-21", 79f),
        )
        val shown = entriesInWindow(entries, today, 30)
        assertEquals(2, shown.size)
        assertEquals(79.4f, shown.first().weightKg)
    }

    @Test
    fun xFractionRunsFromFirstDayToToday() {
        assertEquals(0f, xFraction("2026-09-23", today, 30), eps)
        assertEquals(1f, xFraction(today, today, 30), eps)
        assertEquals(15f / 29f, xFraction("2026-10-08", today, 30), eps)
        assertEquals(0f, xFraction("2026-01-01", today, 30), eps)
    }

    @Test
    fun weightChangeNeedsTwoWeighIns() {
        assertNull(weightChangeKg(listOf(WeightEntry(today, 80f))))
        assertEquals(-1.5f, weightChangeKg(listOf(WeightEntry("2026-10-01", 80f), WeightEntry(today, 78.5f)))!!, eps)
    }

    // ── 7-day bars ──────────────────────────────────────────────────────────────

    private fun meal(date: String, kcal: Int, protein: Float) =
        LoggedMeal("m-$date-$kcal", "Meal", kcal, protein, 0f, 0f, "12:00 PM", date)

    @Test
    fun dailyTotalsCoverSevenDaysOldestFirst() {
        val meals = listOf(
            meal(today, 500, 30f),
            meal(today, 700, 40.6f),
            meal("", 100, 5f),             // no date: counts as today, as on the dashboard
            meal("2026-10-20", 1800, 90f),
            meal("2026-10-14", 2500, 120f), // 8 days ago: outside the week
        )
        val week = dailyTotals(meals, today, 7)
        assertEquals(7, week.size)
        assertEquals("2026-10-16", week.first().date)
        assertEquals(today, week.last().date)
        assertEquals(1300, week.last().calories)
        assertEquals(76, week.last().proteinG)
        assertEquals(1800, week[4].calories)
        assertEquals(0, week[0].calories)
        assertEquals(3100, week.sumOf { it.calories })
    }

    @Test
    fun barScaleFitsTheTargetAndTheHighestDay() {
        assertEquals(2000, barScale(listOf(1200, 1500), target = 2000))
        assertEquals(2600, barScale(listOf(2600, 1500), target = 2000))
        assertEquals(1500, barScale(listOf(1200, 1500), target = null))
        assertEquals(0, barScale(listOf(0, 0), target = null))
        assertEquals(0.5f, barFraction(1000, 2000), eps)
        assertEquals(1f, barFraction(3000, 2000), eps)
        assertEquals(0f, barFraction(1000, 0), eps)
    }

    @Test
    fun weekdayLabels() {
        assertEquals("Th", weekdayShort("1970-01-01"))
        assertEquals("Th", weekdayShort(today))
        assertEquals("Mo", weekdayShort("2026-10-19"))
        assertEquals("", weekdayShort("not a date"))
    }

    // ── Weigh-ins from check-ins and Settings ───────────────────────────────────

    private val defaultProfile = UserProfile(
        weight = 70f, height = 175f, calGoal = 2000, proteinGoal = 150, carbsGoal = 200, fatGoal = 65,
    )
    private val male80 = BodyStats(80f, 180f, 30, "Male", ActivityLevel.MODERATE)

    @Test
    fun checkInStillUpdatesTheProfileExactlyAsBeforeAndRecordsAnEntry() {
        val p = defaultProfile.startPlan(male80, PlanGoal.LOSE, PlanPace.STEADY, 72f, "2026-10-12")
        val result = p.checkInWithEntry(77f, "2026-10-26")
        assertEquals(p.checkIn(77f, "2026-10-26"), result.profile)
        assertEquals(77f, result.profile.weight)
        assertEquals("2026-10-26", result.profile.plan!!.lastCheckInDate)
        assertEquals(WeightEntry("2026-10-26", 77f), result.entry)
    }

    @Test
    fun checkInWithoutAPlanChangesNothingAndRecordsNothing() {
        val result = defaultProfile.checkInWithEntry(68f, today)
        assertEquals(defaultProfile, result.profile)
        assertNull(result.entry)
    }

    @Test
    fun settingsSaveRecordsAnEntryOnlyWhenTheWeightChanges() {
        assertEquals(
            WeightEntry(today, 71.5f),
            weightEntryForChange(defaultProfile, defaultProfile.copy(weight = 71.5f), today),
        )
        assertNull(weightEntryForChange(defaultProfile, defaultProfile.copy(calGoal = 1800), today))
    }
}
