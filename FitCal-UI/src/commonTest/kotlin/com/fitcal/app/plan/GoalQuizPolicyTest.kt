package com.fitcal.app.plan

import com.fitcal.shared.model.UserProfile
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class GoalQuizPolicyTest {

    private val store = mutableMapOf<String, String>()
    private val fresh = UserProfile(weight = 70f, height = 175f, calGoal = 2000, proteinGoal = 150, carbsGoal = 200, fatGoal = 65)

    @BeforeTest
    fun setUp() {
        store.clear()
        GoalQuizPolicy.preferenceReader = { k, d -> store[k] ?: d }
        GoalQuizPolicy.preferenceWriter = { k, v -> store[k] = v }
    }

    @AfterTest
    fun tearDown() = GoalQuizPolicy.resetToDefaults()

    @Test
    fun offeredOnceToSomeoneWithoutTargets() {
        assertTrue(GoalQuizPolicy.shouldOffer(fresh))
        GoalQuizPolicy.recordOffered()
        assertFalse(GoalQuizPolicy.shouldOffer(fresh))
    }

    @Test
    fun neverOfferedWhenTargetsAlreadyExist() {
        assertFalse(GoalQuizPolicy.shouldOffer(fresh.copy(targetsMode = "custom")))
        assertFalse(GoalQuizPolicy.shouldOffer(fresh.copy(calGoal = 1800)))   // own numbers, older profile
    }

    @Test
    fun maintainSkipsTheTargetQuestion() {
        assertEquals(
            listOf(QuizStep.GOAL, QuizStep.ABOUT_YOU, QuizStep.BODY, QuizStep.ACTIVITY, QuizStep.RESULT),
            quizSteps(PlanGoal.MAINTAIN),
        )
        assertTrue(QuizStep.TARGET in quizSteps(PlanGoal.LOSE))
        assertTrue(QuizStep.TARGET in quizSteps(PlanGoal.GAIN))
    }
}
