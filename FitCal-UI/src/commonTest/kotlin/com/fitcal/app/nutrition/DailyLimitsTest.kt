package com.fitcal.app.nutrition

import com.fitcal.shared.model.FoodItem
import com.fitcal.shared.model.LoggedMeal
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class DailyLimitsTest {

    private val goals = DailyGoals(calories = 2000, carbs = 250, fat = 70)

    @Test
    fun noNotesWhileUnderEveryGoal() {
        val notes = overTargetNotes(DayTotals(1000, 50f, 100f, 30f, 20f), DayTotals(500, 20f, 60f, 20f, 10f), goals)
        assertTrue(notes.isEmpty())
    }

    @Test
    fun mealThatCrossesAGoalSaysByHowMuch() {
        val notes = overTargetNotes(DayTotals(fat = 60f), DayTotals(fat = 30f), goals)
        assertEquals(listOf("This meal takes you 20 g over your fat target for today."), notes)
    }

    @Test
    fun alreadyOverSaysWhatTheMealAdds() {
        val notes = overTargetNotes(DayTotals(calories = 2100), DayTotals(calories = 300), goals)
        assertEquals(listOf("You're already past your calorie target today; this adds 300 kcal."), notes)
    }

    @Test
    fun sugarUsesTheGuidelineEvenWithoutTargets() {
        val notes = overTargetNotes(DayTotals(sugar = 40f), DayTotals(sugar = 25f), goals = null, includeSugar = true)
        assertEquals(listOf("This meal takes you 15 g over your sugar guideline for today."), notes)
    }

    @Test
    fun targetsOffMeansNoCalorieCarbOrFatNotes() {
        val notes = overTargetNotes(DayTotals(3000, 0f, 400f, 120f, 0f), DayTotals(500, 0f, 50f, 20f, 0f), goals = null)
        assertTrue(notes.isEmpty())
    }

    @Test
    fun proteinIsNeverFlagged() {
        val notes = overTargetNotes(DayTotals(protein = 300f), DayTotals(protein = 80f), goals)
        assertTrue(notes.isEmpty())
    }

    @Test
    fun zeroGoalAndZeroMealValuesAreIgnored() {
        val noGoals = DailyGoals(0, 0, 0)
        assertTrue(overTargetNotes(DayTotals(fat = 500f), DayTotals(fat = 50f), noGoals).isEmpty())
        assertTrue(overTargetNotes(DayTotals(fat = 500f), DayTotals(fat = 0f), goals).isEmpty())
    }

    @Test
    fun noWordsThatJudgeTheFood() {
        val notes = overTargetNotes(DayTotals(2500, 0f, 300f, 90f, 80f), DayTotals(900, 0f, 120f, 40f, 30f), goals, includeSugar = true)
        assertEquals(4, notes.size)
        val banned = listOf("bad", "unhealthy", "cheat", "guilt", "too much", "should")
        notes.forEach { n -> banned.forEach { assertFalse(Regex("\\b$it\\b").containsMatchIn(n.lowercase()), n) } }
    }

    @Test
    fun mealSugarComesFromItsIngredients() {
        val meal = LoggedMeal(
            id = "1", name = "x", calories = 0, protein = 0f, carbs = 0f, fat = 0f, timestamp = "",
            items = listOf(
                FoodItem("a", 100, 0, 0f, 0f, 0f, "high", sugar_g = 12.5f),
                FoodItem("b", 100, 0, 0f, 0f, 0f, "high", sugar_g = 7.5f),
            ),
        )
        assertEquals(20f, meal.sugar)
        assertEquals(20f, listOf(meal, meal.copy(items = emptyList())).dayTotals().sugar)
    }

    @Test
    fun isOverNeedsAGoal() {
        assertTrue(isOver(71f, 70))
        assertFalse(isOver(70f, 70))
        assertFalse(isOver(500f, 0))
    }

    @Test
    fun fibreIsSummedButNeverWarnsAboutGoingOver() {
        val meal = LoggedMeal(
            id = "1", name = "x", calories = 0, protein = 0f, carbs = 0f, fat = 0f, timestamp = "",
            items = listOf(FoodItem("oats", 80, 0, 0f, 0f, 0f, "high", fiber_g = 8f), FoodItem("beans", 150, 0, 0f, 0f, 0f, "high", fiber_g = 9f)),
        )
        assertEquals(17f, meal.fiber)
        assertEquals(17f, listOf(meal).dayTotals().fiber)
        val notes = overTargetNotes(DayTotals(fiber = 40f), DayTotals(fiber = 20f), goals)
        assertTrue(notes.isEmpty())
    }

    @Test
    fun noSugarNoteWhileSugarIsHidden() {
        val notes = overTargetNotes(DayTotals(sugar = 80f), DayTotals(sugar = 40f), goals = null, includeSugar = false)
        assertTrue(notes.isEmpty())
    }
}
