package com.fitcal.app.plan

import com.fitcal.app.loadPreference
import com.fitcal.app.savePreference
import com.fitcal.shared.model.UserProfile

/**
 * The one-time "Want daily targets made for you?" offer on first open (owner decision 2026-10-22).
 * Most people dislike onboarding quizzes, so it is only offered, with Skip as an equal choice,
 * and never again once answered. Users who already have targets are never asked.
 */
object GoalQuizPolicy {
    private const val KEY_OFFERED = "goal_quiz_offered"

    var preferenceReader: (key: String, defaultValue: String) -> String = { key, default -> loadPreference(key, default) }
    var preferenceWriter: (key: String, value: String) -> Unit = { key, value -> savePreference(key, value) }

    fun resetToDefaults() {
        preferenceReader = { key, default -> loadPreference(key, default) }
        preferenceWriter = { key, value -> savePreference(key, value) }
    }

    fun shouldOffer(profile: UserProfile): Boolean =
        !hasBeenOffered() && profile.resolvedTargetsMode() == TargetsMode.NONE

    fun hasBeenOffered(): Boolean = preferenceReader(KEY_OFFERED, "false").toBoolean()

    /** Call as the offer appears, so closing the app on it doesn't bring it back. */
    fun recordOffered() {
        preferenceWriter(KEY_OFFERED, "true")
    }
}

/** The quiz's questions, in order. Target and pace are skipped for Maintain. */
enum class QuizStep { GOAL, ABOUT_YOU, BODY, ACTIVITY, TARGET, RESULT }

fun quizSteps(goal: PlanGoal): List<QuizStep> =
    QuizStep.entries.filter { it != QuizStep.TARGET || goal != PlanGoal.MAINTAIN }
