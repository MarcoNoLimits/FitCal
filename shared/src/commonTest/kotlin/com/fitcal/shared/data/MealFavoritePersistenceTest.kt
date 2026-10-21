package com.fitcal.shared.data

import com.fitcal.shared.model.LoggedMeal
import com.fitcal.shared.sync.OutboxItem
import com.fitcal.shared.sync.SupabaseMealRow
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.boolean
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** A meal can be starred as a favourite; meals saved before favourites existed still load. */
class MealFavoritePersistenceTest {

    private val json = Json { ignoreUnknownKeys = true; isLenient = true }

    @Test
    fun mealsStoredBeforeFavouritesExistedStillLoad() = runBlocking {
        val storage = FakeKeyValueStorage()
        storage.putString(
            LocalMealRepository.KEY_LOGGED_MEALS,
            """[{"id":"old","name":"Oats","calories":350,"protein":15.0,"carbs":60.0,"fat":5.0,"timestamp":"08:00 AM","date":"2026-10-01","items":[]}]""",
        )

        val meal = LocalMealRepository(storage).getMealsForDate("2026-10-01").single()
        assertEquals("old", meal.id)
        assertFalse(meal.favorite)
    }

    @Test
    fun aStarSurvivesAnAppRestart() = runBlocking {
        val storage = FakeKeyValueStorage()
        val meal = LoggedMeal("m1", "Oats", 350, 15f, 60f, 5f, "08:00 AM", "2026-10-01")
        LocalMealRepository(storage).saveMeal(meal)
        LocalMealRepository(storage).updateMeal(meal.copy(favorite = true))

        assertTrue(LocalMealRepository(storage).getAllMeals().single().favorite)
    }

    @Test
    fun queuedMealsFromOlderBuildsStillDecode() {
        val raw = """[{"type":"com.fitcal.shared.sync.OutboxItem.UpsertMeal","meal":{"id":"q","name":"Toast","calories":200,"protein":6.0,"carbs":30.0,"fat":4.0,"timestamp":"09:00 AM","date":"2026-10-02"}}]"""
        val items = json.decodeFromString(ListSerializer(OutboxItem.serializer()), raw)
        val meal = (items.single() as OutboxItem.UpsertMeal).meal
        assertFalse(meal.favorite)
    }

    @Test
    fun serverRowsWithoutTheColumnDecodeAsNotStarred() {
        val row = json.decodeFromString(
            SupabaseMealRow.serializer(),
            """{"id":"r","name":"Soup","calories":180,"protein_g":8.0,"carbs_g":20.0,"fat_g":6.0,"eaten_at":"2026-10-02T12:00:00Z"}""",
        )
        assertFalse(row.favorite)
    }

    @Test
    fun unstarringIsAlwaysSentToTheServer() {
        // A Json that drops defaults must still send favorite=false, or an unstar never syncs.
        val encoded = Json { encodeDefaults = false }.encodeToJsonElement(
            SupabaseMealRow.serializer(),
            SupabaseMealRow("r", "Soup", 180, 8f, 20f, 6f, favorite = false),
        ).jsonObject
        assertEquals(false, encoded["favorite"]?.jsonPrimitive?.boolean)
    }
}
