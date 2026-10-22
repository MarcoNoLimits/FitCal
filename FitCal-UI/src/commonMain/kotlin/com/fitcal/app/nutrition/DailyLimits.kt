package com.fitcal.app.nutrition

import com.fitcal.shared.model.LoggedMeal
import kotlin.math.roundToInt

/**
 * WHO guideline: keep free sugars under 10% of energy, about 50 g a day for an adult.
 * FitCal's figure is total sugars (fruit and milk included), so it's shown as a guide, not a limit.
 */
const val SUGAR_GUIDELINE_G = 50

/**
 * Fibre is a "get enough" number, not a limit: EFSA and most guidelines suggest at least 25 g a
 * day for adults. Shown as progress towards it; never an over-target note.
 */
const val FIBER_GUIDE_G = 25

/** What a day (or a meal) adds up to. */
data class DayTotals(
    val calories: Int = 0,
    val protein: Float = 0f,
    val carbs: Float = 0f,
    val fat: Float = 0f,
    val sugar: Float = 0f,
    val fiber: Float = 0f,
) {
    operator fun plus(other: DayTotals) = DayTotals(
        calories + other.calories,
        protein + other.protein,
        carbs + other.carbs,
        fat + other.fat,
        sugar + other.sugar,
        fiber + other.fiber,
    )
}

fun List<LoggedMeal>.dayTotals(): DayTotals =
    fold(DayTotals()) { acc, m -> acc + DayTotals(m.calories, m.protein, m.carbs, m.fat, m.sugar, m.fiber) }

/** The daily goals the over-target check compares against; 0 = no goal for that value. */
data class DailyGoals(val calories: Int, val carbs: Int, val fat: Int)

/**
 * Calm, factual notes for the review screen when logging this meal takes the day past a goal.
 *
 * - Calories, carbs and fat against the user's own goals, only when targets are on ([goals] null = off).
 *   Protein is left out: going over a protein target isn't a concern for most people.
 * - Sugar against [SUGAR_GUIDELINE_G], for everyone.
 * - Only a value this meal pushes over (or adds to while already over) is mentioned; the wording
 *   states the amount and never judges the food (some users read the eating-disorder guide).
 */
fun overTargetNotes(before: DayTotals, meal: DayTotals, goals: DailyGoals?): List<String> {
    val after = before + meal
    val notes = mutableListOf<String>()
    fun check(label: String, unit: String, goal: Int, beforeValue: Float, mealValue: Float, afterValue: Float, target: String) {
        if (goal <= 0 || mealValue <= 0f || afterValue <= goal) return
        val over = (afterValue - goal).roundToInt()
        if (over <= 0) return
        notes += if (beforeValue > goal) {
            "You're already past your $label $target today; this adds ${mealValue.roundToInt()} $unit."
        } else {
            "This meal takes you $over $unit over your $label $target for today."
        }
    }
    if (goals != null) {
        check("calorie", "kcal", goals.calories, before.calories.toFloat(), meal.calories.toFloat(), after.calories.toFloat(), "target")
        check("carb", "g", goals.carbs, before.carbs, meal.carbs, after.carbs, "target")
        check("fat", "g", goals.fat, before.fat, meal.fat, after.fat, "target")
    }
    check("sugar", "g", SUGAR_GUIDELINE_G, before.sugar, meal.sugar, after.sugar, "guideline")
    return notes
}

/** Whether a day's value is past its goal, for the amber state on the dashboard. */
fun isOver(value: Float, goal: Int): Boolean = goal > 0 && value > goal
