package com.fitcal.shared.data

import com.fitcal.shared.model.FoodItem
import com.fitcal.shared.model.LoggedMeal
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** A logged meal keeps its ingredients so it can be reopened and corrected later. */
class MealItemsPersistenceTest {

    private val items = listOf(
        FoodItem("Chicken Breast", 150, 248, 46.5f, 0f, 5.4f, "high"),
        FoodItem("Brown Rice", 120, 133, 3.1f, 28.6f, 1.1f, "medium"),
    )

    @Test
    fun ingredientsSurviveAnAppRestart() = runBlocking {
        val storage = FakeKeyValueStorage()
        LocalMealRepository(storage).saveMeal(
            LoggedMeal("m1", "Chicken & Rice", 381, 49.6f, 28.6f, 6.5f, "12:30 PM", "2026-10-12", items)
        )

        val reloaded = LocalMealRepository(storage).getMealsForDate("2026-10-12").single()
        assertEquals(items, reloaded.items)
    }

    @Test
    fun editingIngredientsUpdatesTheSameMeal() = runBlocking {
        val repo = LocalMealRepository(FakeKeyValueStorage())
        val meal = LoggedMeal("m1", "Chicken & Rice", 381, 49.6f, 28.6f, 6.5f, "12:30 PM", "2026-10-12", items)
        repo.saveMeal(meal)

        val edited = meal.copy(calories = 248, items = items.take(1))
        repo.updateMeal(edited)

        val fetched = repo.getMealsForDate("2026-10-12").single()
        assertEquals("m1", fetched.id)
        assertEquals("12:30 PM", fetched.timestamp)
        assertEquals(1, fetched.items.size)
        assertEquals(248, fetched.calories)
    }

    @Test
    fun mealsStoredBeforeIngredientsExistedStillLoad() = runBlocking {
        val storage = FakeKeyValueStorage()
        storage.putString(
            LocalMealRepository.KEY_LOGGED_MEALS,
            """[{"id":"old","name":"Oats","calories":350,"protein":15.0,"carbs":60.0,"fat":5.0,"timestamp":"08:00 AM","date":"2026-10-01"}]""",
        )

        val meal = LocalMealRepository(storage).getMealsForDate("2026-10-01").single()
        assertEquals(350, meal.calories)
        assertTrue(meal.items.isEmpty())
    }
}
