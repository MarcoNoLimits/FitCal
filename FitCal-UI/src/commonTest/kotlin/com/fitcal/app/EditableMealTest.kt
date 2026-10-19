package com.fitcal.app

import com.fitcal.app.ui.screens.review.components.LEGACY_PORTION_GRAMS
import com.fitcal.app.ui.screens.review.components.caloriesAtCurrentWeight
import com.fitcal.app.ui.screens.review.components.toEditable
import com.fitcal.app.ui.screens.review.components.toEditableResponse
import com.fitcal.app.ui.screens.review.components.toFoodItems
import com.fitcal.shared.model.FoodItem
import com.fitcal.shared.model.LoggedMeal
import kotlin.test.Test
import kotlin.test.assertEquals

/** Reopening a logged meal from the dashboard and saving it again. */
class EditableMealTest {

    private val chicken = FoodItem("Chicken Breast", 150, 248, 46.5f, 0f, 5.4f, "high")
    private val rice = FoodItem("Brown Rice", 150, 168, 3.9f, 35.7f, 1.4f, "medium")

    private fun meal(items: List<FoodItem>) = LoggedMeal(
        id = "m1", name = "Chicken & Rice", calories = 416, protein = 50.4f, carbs = 35.7f, fat = 6.8f,
        timestamp = "12:30 PM", date = "2026-10-12", items = items,
    )

    @Test
    fun savedMealReopensWithItsIngredients() {
        val response = meal(listOf(chicken, rice)).toEditableResponse()
        assertEquals(listOf(chicken, rice), response.items)
        assertEquals("Chicken & Rice", response.meal_name)
    }

    @Test
    fun reopeningAndSavingUnchangedKeepsTheSameCalories() {
        // 248 kcal / 150 g as a Float rate times 150 is 247.99…; truncation would lose 1 kcal per save.
        val items = listOf(chicken, rice)
        var current = items
        repeat(5) { current = current.mapIndexed { i, it -> it.toEditable(i) }.toFoodItems() }
        assertEquals(items.map { it.calories }, current.map { it.calories })
        assertEquals(items.map { it.weight_est_g }, current.map { it.weight_est_g })
    }

    @Test
    fun changingWeightScalesThatIngredient() {
        val edited = chicken.toEditable(0).copy(currentWeightStr = "300")
        assertEquals(496, edited.caloriesAtCurrentWeight())
        val saved = listOf(edited).toFoodItems().single()
        assertEquals(300, saved.weight_est_g)
        assertEquals(93f, saved.protein_g, 0.01f)
    }

    @Test
    fun storedIngredientsAddUpToTheLoggedTotal() {
        val editable = listOf(chicken, rice).mapIndexed { i, it -> it.toEditable(i) }
        val total = editable.sumOf { it.caloriesAtCurrentWeight() }
        assertEquals(total, editable.toFoodItems().sumOf { it.calories })
    }

    @Test
    fun mealLoggedBeforeIngredientsWereSavedBecomesOneScalablePortion() {
        val response = meal(emptyList()).toEditableResponse()
        val portion = response.items.single()
        assertEquals("Chicken & Rice", portion.item)
        assertEquals(LEGACY_PORTION_GRAMS, portion.weight_est_g)
        assertEquals(416, portion.calories)

        val half = portion.toEditable(0).copy(currentWeightStr = "50")
        assertEquals(208, half.caloriesAtCurrentWeight())
    }
}
