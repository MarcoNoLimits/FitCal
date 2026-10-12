package com.fitcal.app

import com.fitcal.app.plan.BodyStats
import com.fitcal.app.plan.PlanGoal
import com.fitcal.app.plan.PlanPace
import com.fitcal.app.plan.SuggestionNote
import com.fitcal.app.plan.TargetsMode
import com.fitcal.app.plan.addDays
import com.fitcal.app.plan.checkIn
import com.fitcal.app.plan.currentPlanTargets
import com.fitcal.app.plan.dateFromEpochDay
import com.fitcal.app.plan.daysBetween
import com.fitcal.app.plan.endPlan
import com.fitcal.app.plan.epochDay
import com.fitcal.app.plan.formatKg
import com.fitcal.app.plan.formatPlanDate
import com.fitcal.app.plan.hasReachedTarget
import com.fitcal.app.plan.isCheckInDue
import com.fitcal.app.plan.isPaceAllowed
import com.fitcal.app.plan.planTargets
import com.fitcal.app.plan.projectedTargetDate
import com.fitcal.app.plan.resolvedTargetsMode
import com.fitcal.app.plan.startPlan
import com.fitcal.app.plan.targetWeightRange
import com.fitcal.app.plan.weeksToTarget
import com.fitcal.app.ui.components.ActivityLevel
import com.fitcal.app.ui.components.maintenanceCalories
import com.fitcal.shared.model.MacroGoals
import com.fitcal.shared.model.UserProfile
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** The optional personal plan: targets, paces, safety rules, time frame and check-ins. */
class PersonalPlanTest {

    // Male, 80 kg, 180 cm, 30 y: BMR 1,780; Moderate ×1.55 → maintenance 2,759.
    private val male80 = BodyStats(80f, 180f, 30, "Male", ActivityLevel.MODERATE)
    // Female, 55 kg, 165 cm, 30 y, sedentary: maintenance 1,524.
    private val female55 = BodyStats(55f, 165f, 30, "Female", ActivityLevel.SEDENTARY)

    private val defaultProfile = UserProfile(
        weight = 70f, height = 175f, calGoal = 2000, proteinGoal = 150, carbsGoal = 200, fatGoal = 65,
    )

    // ── Targets ─────────────────────────────────────────────────────────────────

    @Test
    fun maintainUsesActivityAdjustedMaintenance() {
        val s = planTargets(male80, PlanGoal.MAINTAIN, PlanPace.STEADY)
        assertEquals(2759, s.maintenance)
        assertEquals(2760, s.calories)
        assertEquals(128, s.proteinG)   // 1.6 g/kg
        assertEquals(77, s.fatG)        // 25% of calories
        assertEquals(389, s.carbsG)     // the rest
        assertEquals(0f, s.kgPerWeek)
    }

    @Test
    fun steadyLossIsHalfAKiloAWeek() {
        val s = planTargets(male80, PlanGoal.LOSE, PlanPace.STEADY)
        assertEquals(2210, s.calories)  // −550 kcal/day
        assertEquals(160, s.proteinG)   // 2.0 g/kg keeps muscle in a deficit
        assertEquals(0.5f, s.kgPerWeek, 0.01f)
        assertNull(s.note)
    }

    @Test
    fun gainUsesSlowerRates() {
        val s = planTargets(male80, PlanGoal.GAIN, PlanPace.STEADY)
        assertEquals(3030, s.calories)  // +275 kcal/day = 0.25 kg/week
        assertEquals(144, s.proteinG)   // 1.8 g/kg
    }

    @Test
    fun activityLevelChangesCalories() {
        val sedentary = planTargets(male80.copy(activity = ActivityLevel.SEDENTARY), PlanGoal.MAINTAIN, PlanPace.STEADY)
        val veryActive = planTargets(male80.copy(activity = ActivityLevel.VERY_ACTIVE), PlanGoal.MAINTAIN, PlanPace.STEADY)
        assertEquals(2140, sedentary.calories)   // 1,780 × 1.2
        assertEquals(3070, veryActive.calories)  // 1,780 × 1.725
    }

    @Test
    fun macrosAddUpToTheCalories() {
        for (goal in PlanGoal.entries) for (pace in PlanPace.entries) for (level in ActivityLevel.entries) {
            val s = planTargets(BodyStats(68f, 172f, 35, "Female", level), goal, pace)
            val energy = s.proteinG * 4 + s.carbsG * 4 + s.fatG * 9
            assertTrue(abs(energy - s.calories) <= 10, "$goal/$pace/$level: $energy vs ${s.calories}")
        }
    }

    @Test
    fun proteinStaysSensibleForHeavierBodies() {
        // 150 kg at 180 cm: protein is based on the weight at BMI 25 (81 kg), not 150 kg.
        val s = planTargets(BodyStats(150f, 180f, 40, "Male", ActivityLevel.SEDENTARY), PlanGoal.MAINTAIN, PlanPace.STEADY)
        assertEquals(130, s.proteinG)
    }

    // ── Safety ──────────────────────────────────────────────────────────────────

    @Test
    fun paceThatIsTooFastCantBePicked() {
        // Male 80: max safe deficit is 25% of 2,759 = 690 kcal; Faster needs 825.
        assertTrue(isPaceAllowed(male80, PlanGoal.LOSE, PlanPace.STEADY))
        assertFalse(isPaceAllowed(male80, PlanGoal.LOSE, PlanPace.FASTER))
    }

    @Test
    fun smallerBodiesOnlyGetTheGentlePace() {
        // Female 55: the 1,200 kcal floor leaves 324 kcal; only Gentle (275) fits.
        assertTrue(isPaceAllowed(female55, PlanGoal.LOSE, PlanPace.GENTLE))
        assertFalse(isPaceAllowed(female55, PlanGoal.LOSE, PlanPace.STEADY))
        // If the stored pace is no longer safe, the targets fall back to the fastest safe one.
        val s = planTargets(female55, PlanGoal.LOSE, PlanPace.STEADY)
        assertEquals(1250, s.calories)
        assertEquals(0.25f, s.kgPerWeek, 0.01f)
    }

    @Test
    fun noDeficitWhenTheFloorLeavesNoRoom() {
        // Maintenance 1,089 is already under the 1,200 kcal floor.
        val body = BodyStats(45f, 155f, 70, "Female", ActivityLevel.SEDENTARY)
        val s = planTargets(body, PlanGoal.LOSE, PlanPace.GENTLE)
        assertEquals(SuggestionNote.NO_SAFE_DEFICIT, s.note)
        assertEquals(1090, s.calories)
        PlanPace.entries.forEach { assertFalse(isPaceAllowed(body, PlanGoal.LOSE, it)) }
    }

    @Test
    fun underweightUsersGetNoDeficitAndNoLoseTarget() {
        val body = BodyStats(45f, 170f, 30, "Female", ActivityLevel.LIGHT)   // BMI 15.6
        assertEquals(SuggestionNote.UNDERWEIGHT, planTargets(body, PlanGoal.LOSE, PlanPace.GENTLE).note)
        assertNull(targetWeightRange(body, PlanGoal.LOSE))
    }

    @Test
    fun loseTargetCantGoBelowAHealthyBmi() {
        val range = targetWeightRange(male80, PlanGoal.LOSE)!!
        assertEquals(59.9f, range.start, 0.1f)          // BMI 18.5 at 180 cm
        assertEquals(79.5f, range.endInclusive, 0.01f)
    }

    @Test
    fun bodyStatsRejectMissingOrUnrealisticInput() {
        assertNull(BodyStats.from(null, 180f, 30, "Male", "Light"))
        assertNull(BodyStats.from(80f, 180f, 17, "Male", "Light"))   // under 18
        assertNull(BodyStats.from(80f, 1.8f, 30, "Male", "Light"))   // height typed in m
        assertEquals(ActivityLevel.SEDENTARY, BodyStats.from(80f, 180f, 30, "Male", "Athlete")!!.activity)
    }

    // ── Time frame ──────────────────────────────────────────────────────────────

    @Test
    fun weeksAndProjectedDate() {
        assertEquals(16, weeksToTarget(80f, 72f, 0.5f))
        val profile = defaultProfile.startPlan(male80, PlanGoal.LOSE, PlanPace.STEADY, 72f, "2026-10-12")
        assertEquals("2027-02-01", profile.projectedTargetDate("2026-10-12"))   // 16 weeks later
    }

    @Test
    fun reachingTheTarget() {
        assertTrue(hasReachedTarget(PlanGoal.LOSE, 71.9f, 72f))
        assertFalse(hasReachedTarget(PlanGoal.LOSE, 72.1f, 72f))
        assertTrue(hasReachedTarget(PlanGoal.GAIN, 80f, 80f))
        assertFalse(hasReachedTarget(PlanGoal.MAINTAIN, 80f, null))
    }

    @Test
    fun dateArithmetic() {
        assertEquals(0L, epochDay("1970-01-01"))
        assertEquals("2026-10-12", dateFromEpochDay(epochDay("2026-10-12")!!))
        assertEquals("2027-01-08", addDays("2026-12-25", 14))
        assertEquals(2L, daysBetween("2024-02-28", "2024-03-01"))   // leap year
        assertNull(epochDay("12/10/2026"))
        assertEquals("1 Feb 2027", formatPlanDate("2027-02-01"))
        assertEquals("72", formatKg(72f))
        assertEquals("72.5", formatKg(72.5f))
    }

    // ── Optional, reversible ────────────────────────────────────────────────────

    @Test
    fun untouchedProfilesHaveNoTargetsAndEditedOnesKeepTheirs() {
        assertEquals(TargetsMode.NONE, defaultProfile.resolvedTargetsMode())
        assertEquals(TargetsMode.CUSTOM, defaultProfile.copy(calGoal = 1800).resolvedTargetsMode())
        assertEquals(TargetsMode.NONE, defaultProfile.copy(targetsMode = "plan", plan = null).resolvedTargetsMode())
    }

    @Test
    fun startingAPlanSetsTargetsFromThePlan() {
        val p = defaultProfile.startPlan(male80, PlanGoal.LOSE, PlanPace.STEADY, 72f, "2026-10-12")
        assertEquals(TargetsMode.PLAN, p.resolvedTargetsMode())
        assertEquals(2210, p.calGoal)
        assertEquals(160, p.proteinGoal)
        assertEquals("Moderate", p.activityLevel)
        assertEquals("Lose Weight", p.goalType)
        assertEquals("2026-10-12", p.plan!!.startDate)
    }

    @Test
    fun endingAPlanRestoresTheUsersOwnNumbers() {
        val custom = defaultProfile.copy(targetsMode = "custom", calGoal = 1800, proteinGoal = 120, carbsGoal = 180, fatGoal = 60)
        val ended = custom.startPlan(male80, PlanGoal.LOSE, PlanPace.STEADY, 72f, "2026-10-12")
            .startPlan(male80, PlanGoal.LOSE, PlanPace.GENTLE, 70f, "2026-10-20")   // edited plan
            .endPlan()
        assertEquals(TargetsMode.CUSTOM, ended.resolvedTargetsMode())
        assertEquals(MacroGoals(1800, 120, 180, 60), MacroGoals(ended.calGoal, ended.proteinGoal, ended.carbsGoal, ended.fatGoal))
        assertNull(ended.plan)
    }

    @Test
    fun endingAPlanWithoutOwnNumbersMeansNoTargets() {
        val ended = defaultProfile.startPlan(male80, PlanGoal.MAINTAIN, PlanPace.STEADY, null, "2026-10-12").endPlan()
        assertEquals(TargetsMode.NONE, ended.resolvedTargetsMode())
        assertNull(ended.currentPlanTargets())
    }

    // ── Check-ins ───────────────────────────────────────────────────────────────

    @Test
    fun checkInIsOfferedEveryTwoWeeksAndCanBeSnoozed() {
        val p = defaultProfile.startPlan(male80, PlanGoal.LOSE, PlanPace.STEADY, 72f, "2026-10-12")
        assertFalse(p.isCheckInDue("2026-10-25", snoozedUntil = null))   // day 13
        assertTrue(p.isCheckInDue("2026-10-26", snoozedUntil = null))    // day 14
        assertFalse(p.isCheckInDue("2026-10-26", snoozedUntil = "2026-10-29"))
        assertTrue(p.isCheckInDue("2026-10-29", snoozedUntil = "2026-10-29"))
        assertFalse(defaultProfile.isCheckInDue("2027-01-01", snoozedUntil = null))   // no plan
    }

    @Test
    fun checkInUpdatesWeightAndRecomputesTargets() {
        val p = defaultProfile.startPlan(male80, PlanGoal.LOSE, PlanPace.STEADY, 72f, "2026-10-12")
        val after = p.checkIn(77f, "2026-10-26")
        assertEquals(77f, after.weight)
        assertEquals("2026-10-26", after.plan!!.lastCheckInDate)
        assertTrue(after.calGoal < p.calGoal, "lighter body → lower maintenance")
        assertFalse(after.isCheckInDue("2026-10-27", snoozedUntil = null))
        assertNotNull(after.projectedTargetDate("2026-10-26"))
    }

    @Test
    fun dashboardMaintenanceUsesActivityLevel() {
        val profile = defaultProfile.copy(weight = 80f, height = 180f, age = 30, gender = "Male", activityLevel = "Moderate")
        assertEquals(2759, maintenanceCalories(profile))
    }
}
