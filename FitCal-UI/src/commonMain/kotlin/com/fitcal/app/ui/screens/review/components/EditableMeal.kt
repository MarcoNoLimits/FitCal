package com.fitcal.app.ui.screens.review.components

import com.fitcal.shared.model.FoodItem
import com.fitcal.shared.model.LoggedMeal
import com.fitcal.shared.model.NutritionResponse
import com.fitcal.shared.model.Totals
import kotlin.math.roundToInt

/** Weight a meal logged before ingredients were saved is shown at: 100 g = the amount logged. */
const val LEGACY_PORTION_GRAMS = 100

fun EditableFoodItem.weightGrams(): Int = currentWeightStr.toIntOrNull() ?: 0

/** Rounded rather than truncated, so reopening and saving an unchanged meal keeps its calories. */
fun EditableFoodItem.caloriesAtCurrentWeight(): Int = (calPerGram * weightGrams()).roundToInt()

/** Per-gram rates from an estimate, so totals can be recalculated locally when the weight changes. */
fun FoodItem.toEditable(index: Int): EditableFoodItem {
    val perGram = { amount: Float -> if (weight_est_g > 0) amount / weight_est_g else 0f }
    return EditableFoodItem(
        id = "${item}_$index",
        name = item,
        currentWeightStr = weight_est_g.toString(),
        calPerGram = perGram(calories.toFloat()),
        proteinPerGram = perGram(protein_g),
        carbsPerGram = perGram(carbs_g),
        fatPerGram = perGram(fat_g),
        confidence = confidence,
        sugarPerGram = perGram(sugar_g),
        fiberPerGram = perGram(fiber_g),
    )
}

/** The ingredient list stored with a logged meal; per-item values add up to the meal's totals. */
fun List<EditableFoodItem>.toFoodItems(): List<FoodItem> = map {
    val weight = it.weightGrams()
    FoodItem(
        item = it.name,
        weight_est_g = weight,
        calories = it.caloriesAtCurrentWeight(),
        protein_g = it.proteinPerGram * weight,
        carbs_g = it.carbsPerGram * weight,
        fat_g = it.fatPerGram * weight,
        confidence = it.confidence,
        sugar_g = it.sugarPerGram * weight,
        fiber_g = it.fiberPerGram * weight,
    )
}

/**
 * A logged meal as review-screen input. Meals logged before ingredients were saved become a
 * single item at [LEGACY_PORTION_GRAMS], so changing its weight scales the whole meal.
 */
fun LoggedMeal.toEditableResponse(): NutritionResponse {
    val editableItems = items.ifEmpty {
        listOf(
            FoodItem(
                item = name,
                weight_est_g = LEGACY_PORTION_GRAMS,
                calories = calories,
                protein_g = protein,
                carbs_g = carbs,
                fat_g = fat,
                confidence = "high",
            )
        )
    }
    return NutritionResponse(
        meal_name = name,
        items = editableItems,
        totals = Totals(calories, protein, carbs, fat, sugar, fiber),
        estimation_notes = "",
    )
}
