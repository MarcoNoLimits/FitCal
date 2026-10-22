package com.fitcal.app.ui.navigation

import kotlinx.serialization.Serializable

// Type-safe Navigation Destinations
@Serializable
object DashboardDestination

@Serializable
object CameraDestination

@Serializable
data class ResultDestination(val responseJson: String)

/** "Describe your meal": type what you ate instead of taking a photo (one AI scan). */
@Serializable
object DescribeMealDestination

/** Review of a barcode product from Open Food Facts (or an empty meal to fill in by hand). No AI scan used. */
@Serializable
data class BarcodeResultDestination(val responseJson: String)

/** The optional personal plan: set up, or edit an existing one. */
@Serializable
object PlanDestination

/** The step-by-step goal quiz offered once at first open (skippable). */
@Serializable
object GoalQuizDestination

/** A logged meal reopened from the dashboard for correction. */
@Serializable
data class EditMealDestination(val mealId: String)

@Serializable
object MonetizationDestination

@Serializable
object SettingsDestination

@Serializable
object AuthDestination

@Serializable
object PrivacyPolicyDestination

@Serializable
object TermsDestination

@Serializable
object FoodLibraryDestination

/** Weight history and the last 7 days of calories and protein; opened from the calorie card. */
@Serializable
object ProgressDestination

/** A nutrition guide; [source] says where it was opened from (for analytics). */
@Serializable
data class GuideDestination(val guideId: String, val source: String)
