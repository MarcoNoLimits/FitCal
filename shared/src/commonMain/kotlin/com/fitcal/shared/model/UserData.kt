package com.fitcal.shared.model

import kotlinx.serialization.Serializable

@Serializable
data class UserProfile(
    val weight: Float,
    val height: Float,
    val calGoal: Int,
    val proteinGoal: Int,
    val carbsGoal: Int,
    val fatGoal: Int,
    val age: Int = 25,
    val gender: String = "Male",
    val goalType: String = "Maintain",
    val defaultPlateSize: Float = 9.0f
)

@Serializable
data class LoggedMeal(
    val id: String,
    val name: String,
    val calories: Int,
    val protein: Float,
    val carbs: Float,
    val fat: Float,
    val timestamp: String,
    val date: String = "",
    // Ingredients as logged, so the meal can be reopened and corrected. Empty for meals logged
    // before ingredients were saved; those are edited as a single portion (see toEditableResponse).
    val items: List<FoodItem> = emptyList()
)

@Serializable
data class LoggedMealsWrapper(
    val meals: List<LoggedMeal> = emptyList()
)
