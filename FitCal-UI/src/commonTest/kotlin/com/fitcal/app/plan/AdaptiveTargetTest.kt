package com.fitcal.app.plan

import com.fitcal.app.ui.components.ActivityLevel
import com.fitcal.shared.model.LoggedMeal
import com.fitcal.shared.model.UserProfile
import com.fitcal.shared.model.WeightEntry
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class AdaptiveTargetTest {

    private val start = "2026-09-01"
    private val today = "2026-09-29"   // 28 days later
    private val body = BodyStats(90f, 180f, 35, "Male", ActivityLevel.MODERATE)
    private val fresh = UserProfile(weight = 90f, height = 180f, calGoal = 2000, proteinGoal = 150, carbsGoal = 200, fatGoal = 65)

    private fun losing(pace: PlanPace = PlanPace.STEADY) = fresh.startPlan(body, PlanGoal.LOSE, pace, 80f, start)

    /** Weekly weigh-ins from [start], changing by [kgPerWeek]. */
    private fun weights(kgPerWeek: Float, from: Float = 90f): List<WeightEntry> =
        (0..4).map { w -> WeightEntry(addDays(start, w * 7)!!, from + kgPerWeek * w) }

    /** A meal of [kcal] on each of the last [days] days before [today]. */
    private fun meals(kcal: Int, days: Int = 14): List<LoggedMeal> =
        (0 until days).map { d -> LoggedMeal("m$d", "Meal", kcal, 0f, 0f, 0f, "12:00 PM", addDays(today, -d)!!) }

    @Test
    fun onPaceMeansNoSuggestion() {
        val p = losing()
        assertNull(p.calorieSuggestion(weights(-0.5f), meals(p.calGoal), today))
    }

    @Test
    fun tooSlowWhileLoggingAndOnTargetSuggestsLessWithinOneStep() {
        val p = losing()
        val s = assertNotNull(p.calorieSuggestion(weights(0f), meals(p.calGoal), today))
        assertTrue(s.deltaKcal < 0)
        assertTrue(-s.deltaKcal <= ADJUSTMENT_STEP_MAX)
        assertTrue(s.newCalories >= body.calorieFloor)
        assertTrue("stayed about the same" in s.reason)
    }

    @Test
    fun neverLowersForSomeoneEatingWellAboveTarget() {
        val p = losing()
        assertNull(p.calorieSuggestion(weights(0f), meals(p.calGoal + 400), today))
    }

    @Test
    fun neverLowersWithoutEnoughLogging() {
        val p = losing()
        assertNull(p.calorieSuggestion(weights(0f), meals(p.calGoal, days = 5), today))
    }

    @Test
    fun losingTooFastSuggestsEatingMoreEvenWithoutLogging() {
        val p = losing()
        val s = assertNotNull(p.calorieSuggestion(weights(-1.3f), emptyList(), today))
        assertTrue(s.deltaKcal > 0)
        assertTrue("eating a little more" in s.reason)
    }

    @Test
    fun notBeforeTwoWeeksOrRightAfterNotNow() {
        val p = losing()
        assertNull(p.calorieSuggestion(weights(0f), meals(p.calGoal), addDays(start, 10)!!))
        val dismissed = p.dismissCalorieSuggestion(addDays(today, -3)!!)
        assertNull(dismissed.calorieSuggestion(weights(0f), meals(p.calGoal), today))
    }

    @Test
    fun acceptingChangesTheTargetAndTheTotalStaysCapped() {
        var p = losing()
        val s = assertNotNull(p.calorieSuggestion(weights(0f), meals(p.calGoal), today))
        val before = p.calGoal
        p = p.acceptCalorieSuggestion(s, today)
        assertEquals(before + s.deltaKcal, p.calGoal)
        assertEquals(today, p.plan?.lastAdjustmentDate)
        // Many accepted steps never go past the total cap or the floor.
        repeat(10) { p = p.copy(plan = p.plan!!.copy(calorieAdjustment = p.plan!!.calorieAdjustment - 150)).withPlanTargetsRecomputed() }
        val base = planTargets(body, PlanGoal.LOSE, PlanPace.STEADY).calories
        assertTrue(p.calGoal >= base - ADJUSTMENT_TOTAL_MAX)
        assertTrue(p.calGoal >= body.calorieFloor)
    }

    @Test
    fun losingNeverGoesAboveMaintenance() {
        val t = planTargets(body, PlanGoal.LOSE, PlanPace.GENTLE)
        val adjusted = t.withCalorieAdjustment(ADJUSTMENT_TOTAL_MAX, body, PlanGoal.LOSE)
        assertTrue(adjusted.calories <= t.maintenance)
    }

    @Test
    fun noPlanOrTargetReachedMeansNoSuggestion() {
        assertNull(fresh.calorieSuggestion(weights(0f), meals(2000), today))
        val reached = losing().copy(weight = 79f)
        assertNull(reached.calorieSuggestion(weights(0f), meals(2000), today))
    }

    @Test
    fun trendNeedsTwoWeeksOfWeighIns() {
        assertNull(weightTrendKgPerWeek(listOf(WeightEntry("2026-09-20", 90f), WeightEntry("2026-09-27", 89f)), start, today))
        val t = assertNotNull(weightTrendKgPerWeek(weights(-0.5f), start, today))
        assertEquals(-0.5f, t, 0.01f)
    }
}
