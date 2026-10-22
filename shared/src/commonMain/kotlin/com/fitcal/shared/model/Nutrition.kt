package com.fitcal.shared.model

import kotlinx.serialization.Serializable

@Serializable
data class NutritionResponse(
    val meal_name: String,
    val items: List<FoodItem>,
    val totals: Totals,
    val estimation_notes: String
)

@Serializable
data class FoodItem(
    val item: String,
    val weight_est_g: Int,
    val calories: Int,
    val protein_g: Float,
    val carbs_g: Float,
    val fat_g: Float,
    val confidence: String,
    // Total sugars (natural + added). Absent from estimates and meals made before sugar was
    // tracked, which read as 0.
    val sugar_g: Float = 0f
)

@Serializable
data class Totals(
    val calories: Int,
    val protein_g: Float,
    val carbs_g: Float,
    val fat_g: Float,
    val sugar_g: Float = 0f
)
