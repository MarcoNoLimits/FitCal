package com.fitcal.app.ui.navigation

import kotlinx.serialization.Serializable

// Type-safe Navigation Destinations
@Serializable
object DashboardDestination

@Serializable
object CameraDestination

@Serializable
data class ResultDestination(val responseJson: String)

/** The optional personal plan: set up, or edit an existing one. */
@Serializable
object PlanDestination

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
