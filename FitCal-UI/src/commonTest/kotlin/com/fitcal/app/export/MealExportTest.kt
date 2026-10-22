package com.fitcal.app.export

import com.fitcal.shared.model.FoodItem
import com.fitcal.shared.model.LoggedMeal
import com.fitcal.shared.model.WeightEntry
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class MealExportTest {

    private val today = "2026-10-23"

    private fun meal(id: String, date: String, time: String, name: String = "Meal", items: List<FoodItem> = emptyList()) =
        LoggedMeal(id, name, 500, 30.25f, 60f, 12.04f, time, date, items)

    @Test
    fun rangeKeepsTheWindowAndSortsByDateAndTime() {
        val meals = listOf(
            meal("a", "2026-10-23", "07:30 PM"),
            meal("b", "2026-10-23", "08:00 AM"),
            meal("c", "2026-09-01", "12:00 PM"),   // outside 30 days
            meal("d", "2026-10-25", "12:00 PM"),   // future
        )
        assertEquals(listOf("b", "a"), mealsInRange(meals, today, ExportRange.DAYS_30).map { it.id })
        assertEquals(listOf("c", "b", "a"), mealsInRange(meals, today, ExportRange.ALL).map { it.id }.filter { it != "d" })
    }

    @Test
    fun csvHasHeaderRowsFoodsAndWeights() {
        val m = meal("a", "2026-10-23", "08:00 AM", "Oats, berries", listOf(FoodItem("Oats", 60, 220, 8f, 40f, 4f, "high")))
        val csv = buildMealsCsv(listOf(m), listOf(WeightEntry("2026-10-20", 80.04f)))
        val lines = csv.split("\r\n")
        assertEquals("date,time,meal,calories_kcal,protein_g,carbs_g,fat_g,foods", lines[0])
        assertEquals("2026-10-23,08:00 AM,\"Oats, berries\",500,30.3,60,12,Oats (60 g)", lines[1])
        assertTrue("date,weight_kg" in lines)
        assertTrue("2026-10-20,80" in lines)
    }

    @Test
    fun csvEscapesQuotesAndBlocksFormulas() {
        assertEquals("\"Mum's \"\"special\"\" stew\"", csvField("Mum's \"special\" stew"))
        assertEquals("'=HYPERLINK(1)", csvField("=HYPERLINK(1)"))
        assertEquals("'-5 kcal", csvField("-5 kcal"))
        assertEquals("plain", csvField("plain"))
    }

    @Test
    fun timesSortAcrossNoon() {
        assertTrue(minutesOfDay("12:30 AM") < minutesOfDay("08:00 AM"))
        assertTrue(minutesOfDay("11:59 AM") < minutesOfDay("12:00 PM"))
        assertTrue(minutesOfDay("12:00 PM") < minutesOfDay("01:00 PM"))
    }
}
