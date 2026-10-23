package com.fitcal.app.ui.screens.review.components

/*
 * Hidden-ingredient check (owner decision 2026-10-23). Photo estimates under-count what the
 * camera can't see: an NIH test of four photo apps (102 weighed meals, 2026) found every app
 * low by roughly 250–345 kcal and ~30 g of fat per meal. After a photo or typed scan the review
 * screen asks about the usual culprits; a tap adds a typical amount as a normal, editable item.
 * Values per gram from USDA FoodData Central (SR Legacy).
 */

data class HiddenIngredient(
    val key: String,
    val chipLabel: String,
    val itemName: String,
    val grams: Int,
    val kcalPerGram: Float,
    val proteinPerGram: Float,
    val carbsPerGram: Float,
    val fatPerGram: Float,
    /** Words that mean the scan already found it, so the chip isn't offered. */
    val alreadyFoundWords: List<String>,
)

val HIDDEN_INGREDIENTS = listOf(
    // Vegetable oil, 1 tbsp = 14 g ≈ 124 kcal, all fat (USDA FDC 171413).
    HiddenIngredient("oil", "Cooked in oil", "Cooking oil (1 tbsp)", 14, 8.84f, 0f, 0f, 1.0f, listOf("oil")),
    // Butter, 1 tbsp = 14 g ≈ 100 kcal (USDA FDC 173410).
    HiddenIngredient("butter", "Butter", "Butter (1 tbsp)", 14, 7.17f, 0.0085f, 0.0006f, 0.81f, listOf("butter", "ghee")),
    // Dressing or sauce, 2 tbsp = 30 g: a middle estimate between vinaigrette and creamy dressings.
    HiddenIngredient(
        "dressing", "Dressing or sauce", "Dressing or sauce (2 tbsp)", 30, 3.4f, 0.01f, 0.13f, 0.3f,
        listOf("dressing", "sauce", "mayo", "mayonnaise", "vinaigrette", "aioli", "pesto", "gravy"),
    ),
)

/** The checks still worth asking about: those the scan didn't already list. */
fun hiddenIngredientsToOffer(itemNames: List<String>): List<HiddenIngredient> {
    val names = itemNames.map { it.lowercase() }
    return HIDDEN_INGREDIENTS.filter { hidden -> hidden.alreadyFoundWords.none { word -> names.any { word in it } } }
}

fun HiddenIngredient.toEditable(): EditableFoodItem = EditableFoodItem(
    id = "hidden_$key",
    name = itemName,
    currentWeightStr = grams.toString(),
    calPerGram = kcalPerGram,
    proteinPerGram = proteinPerGram,
    carbsPerGram = carbsPerGram,
    fatPerGram = fatPerGram,
    confidence = "medium",
)
