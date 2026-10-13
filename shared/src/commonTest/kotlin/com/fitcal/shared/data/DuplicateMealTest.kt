package com.fitcal.shared.data

import com.fitcal.shared.model.FoodItem
import com.fitcal.shared.model.LoggedMeal
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * A meal must appear once. Each app start downloads today's meals from the server; that used
 * to append copies of meals already on the device, so the same meal showed up twice.
 */
class DuplicateMealTest {

    private val meal = LoggedMeal(
        id = "m1", name = "Chicken & Rice", calories = 381, protein = 49.6f, carbs = 28.6f, fat = 6.5f,
        timestamp = "12:30 PM", date = "2026-10-13",
        items = listOf(FoodItem("Chicken Breast", 150, 248, 46.5f, 0f, 5.4f, "high")),
    )

    @Test
    fun savingTheSameMealTwiceKeepsOneCopy() = runBlocking {
        val repo = LocalMealRepository(FakeKeyValueStorage())
        repo.saveMeal(meal)
        repo.saveMeal(meal.copy(calories = 400))
        val meals = repo.getMealsForDate("2026-10-13")
        assertEquals(1, meals.size)
        assertEquals(400, meals.single().calories)
    }

    @Test
    fun downloadingAMealTheDeviceHasDoesNotDuplicateOrOverwriteIt() = runBlocking {
        val repo = LocalMealRepository(FakeKeyValueStorage())
        repo.saveMeal(meal)
        // The server copy has no ingredient list and older numbers.
        val fromServer = meal.copy(calories = 300, items = emptyList())
        repeat(3) { repo.saveIfAbsent(fromServer) }   // three app starts

        val stored = repo.getMealsForDate("2026-10-13").single()
        assertEquals(381, stored.calories)
        assertEquals(1, stored.items.size)
    }

    @Test
    fun downloadingAMealFromAnotherDeviceAddsIt() = runBlocking {
        val repo = LocalMealRepository(FakeKeyValueStorage())
        repo.saveIfAbsent(meal)
        assertEquals(1, repo.getMealsForDate("2026-10-13").size)
    }

    @Test
    fun duplicatesSavedByOlderBuildsAreRemovedOnLoad() = runBlocking {
        val storage = FakeKeyValueStorage()
        val one = """{"id":"m1","name":"Oats","calories":350,"protein":15.0,"carbs":60.0,"fat":5.0,"timestamp":"08:00 AM","date":"2026-10-13"}"""
        storage.putString(LocalMealRepository.KEY_LOGGED_MEALS, "[$one,$one]")

        assertEquals(1, LocalMealRepository(storage).getMealsForDate("2026-10-13").size)
    }

    @Test
    fun undoAfterDeleteRestoresOneCopy() = runBlocking {
        val repo = LocalMealRepository(FakeKeyValueStorage())
        repo.saveMeal(meal)
        repo.deleteMeal("m1")
        repo.saveMeal(meal)   // dashboard "Undo"
        assertEquals(1, repo.getMealsForDate("2026-10-13").size)
    }
}
