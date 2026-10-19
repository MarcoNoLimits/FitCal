package com.fitcal.shared.data

import com.fitcal.shared.model.MacroGoals
import com.fitcal.shared.model.PersonalPlan
import com.fitcal.shared.model.UserProfile
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/** The optional personal plan is stored with the profile and survives a restart. */
class PersonalPlanPersistenceTest {

    @Test
    fun planSurvivesAnAppRestart() = runBlocking {
        val storage = FakeKeyValueStorage()
        val plan = PersonalPlan(
            pace = "Steady",
            targetWeightKg = 72f,
            startDate = "2026-10-12",
            startWeightKg = 80f,
            lastCheckInDate = "2026-10-12",
            customGoalsBeforePlan = MacroGoals(1800, 120, 180, 60),
        )
        val profile = UserProfile(
            weight = 80f, height = 180f, calGoal = 2210, proteinGoal = 160, carbsGoal = 266, fatGoal = 61,
            activityLevel = "Moderate", targetsMode = "plan", plan = plan,
        )
        LocalUserRepository(storage).saveUserProfile(profile)

        val reloaded = LocalUserRepository(storage).getUserProfile()
        assertEquals(plan, reloaded.plan)
        assertEquals("plan", reloaded.targetsMode)
        assertEquals("Moderate", reloaded.activityLevel)
    }

    @Test
    fun profilesSavedBeforePlansStillLoad() = runBlocking {
        val storage = FakeKeyValueStorage()
        LocalUserRepository(storage).saveUserProfile(
            UserProfile(weight = 70f, height = 175f, calGoal = 2000, proteinGoal = 150, carbsGoal = 200, fatGoal = 65)
        )
        val reloaded = LocalUserRepository(storage).getUserProfile()
        assertNull(reloaded.plan)
        assertNull(reloaded.targetsMode)
        assertEquals("Sedentary", reloaded.activityLevel)
    }
}
