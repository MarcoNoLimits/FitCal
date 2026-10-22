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
    val defaultPlateSize: Float = 9.0f,
    // "Sedentary" | "Light" | "Moderate" | "Very Active"; scales maintenance calories.
    val activityLevel: String = "Sedentary",
    // Where the daily targets come from: "none" (no targets shown), "plan" (from [plan]) or
    // "custom" (the user's own numbers). Null for profiles saved before plans existed; the app
    // then infers it from whether the goals were changed from the factory defaults.
    val targetsMode: String? = null,
    // The optional personal plan. Only meaningful while targetsMode is "plan".
    val plan: PersonalPlan? = null
)

/** An optional, user-started plan: goal from [UserProfile.goalType], at a chosen pace. */
@Serializable
data class PersonalPlan(
    val pace: String = "Steady",            // "Gentle" | "Steady" | "Faster"; unused for Maintain
    val targetWeightKg: Float? = null,      // null for Maintain
    val startDate: String,                  // YYYY-MM-DD
    val startWeightKg: Float,
    val lastCheckInDate: String? = null,    // YYYY-MM-DD; check-ins are optional
    // The user's own numbers from before the plan, restored if the plan is ended.
    val customGoalsBeforePlan: MacroGoals? = null
)

@Serializable
data class MacroGoals(
    val calories: Int,
    val proteinG: Int,
    val carbsG: Int,
    val fatG: Int
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
    val items: List<FoodItem> = emptyList(),
    // Starred by the user for one-tap logging from the dashboard. Absent in meals saved before
    // favourites existed, which load as not starred.
    val favorite: Boolean = false,
    // On-device path of the meal's photo (never uploaded; see MealPhotoStore). Null for typed,
    // barcode and older meals, and on another device after a sync.
    val photoPath: String? = null
) {
    /** Total sugars, from the stored ingredients (they sync with the meal). 0 for older meals. */
    val sugar: Float get() = items.sumOf { it.sugar_g.toDouble() }.toFloat()
}

@Serializable
data class LoggedMealsWrapper(
    val meals: List<LoggedMeal> = emptyList()
)
