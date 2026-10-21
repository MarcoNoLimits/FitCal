package com.fitcal.shared.api

import com.fitcal.shared.data.KeyValueStorage
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.engine.mock.respondError
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import kotlinx.coroutines.runBlocking
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

class OpenFoodFactsClientTest {

    private val foundJson = """
        {
          "code": "3017620422003",
          "status": 1,
          "status_verbose": "product found",
          "product": {
            "product_name": "Nutella",
            "brands": "Ferrero,Nutella",
            "serving_quantity": "15",
            "nutriments": {
              "energy-kcal_100g": 539,
              "energy_100g": 2252,
              "proteins_100g": 6.3,
              "carbohydrates_100g": "57.5",
              "fat_100g": 30.9
            }
          }
        }
    """.trimIndent()

    private val notFoundJson = """{"code":"0000000000000","status":0,"status_verbose":"product not found"}"""

    private class MemoryStorage : KeyValueStorage {
        val map = mutableMapOf<String, String>()
        override fun getString(key: String, defaultValue: String) = map[key] ?: defaultValue
        override fun putString(key: String, value: String) { map[key] = value }
        override fun getInt(key: String, defaultValue: Int) = map[key]?.toIntOrNull() ?: defaultValue
        override fun putInt(key: String, value: Int) { map[key] = value.toString() }
        override fun remove(key: String) { map.remove(key) }
    }

    private fun assertNear(expected: Float, actual: Float, tolerance: Float = 0.051f) =
        assertTrue(abs(expected - actual) <= tolerance, "expected $expected but was $actual")

    // ── Parsing ───────────────────────────────────────────────────────────────

    @Test
    fun parsesFoundProductWithStringAndNumberValues() {
        val result = OpenFoodFactsParser.parse("3017620422003", foundJson)
        val product = assertIs<BarcodeLookupResult.Found>(result).product
        assertEquals("Nutella", product.name)
        assertEquals("Ferrero", product.brand)
        assertNear(539f, product.kcalPer100g)
        assertNear(6.3f, product.proteinPer100g)
        assertNear(57.5f, product.carbsPer100g)
        assertNear(30.9f, product.fatPer100g)
        assertEquals(15f, product.servingGrams)
    }

    @Test
    fun statusZeroIsNotFound() {
        val result = OpenFoodFactsParser.parse("0000000000000", notFoundJson)
        assertEquals(BarcodeLookupResult.NotFound("0000000000000"), result)
    }

    @Test
    fun productWithoutNutrimentsIsMissingNutrition() {
        val body = """{"status":1,"product":{"product_name":"Mystery Crisps"}}"""
        val result = OpenFoodFactsParser.parse("12345670", body)
        assertEquals(BarcodeLookupResult.MissingNutrition("12345670", "Mystery Crisps"), result)
    }

    @Test
    fun emptyNutrimentsIsMissingNutrition() {
        val body = """{"status":1,"product":{"product_name":"","nutriments":{"salt_100g":1.2}}}"""
        val result = OpenFoodFactsParser.parse("12345670", body)
        assertEquals(BarcodeLookupResult.MissingNutrition("12345670", null), result)
    }

    @Test
    fun energyFallsBackToKilojoulesThenToMacros() {
        val kj = """{"status":1,"product":{"product_name":"Oats","nutriments":{"energy_100g":1569,"proteins_100g":13}}}"""
        val fromKj = assertIs<BarcodeLookupResult.Found>(OpenFoodFactsParser.parse("12345670", kj)).product
        assertNear(375f, fromKj.kcalPer100g, tolerance = 0.5f)
        assertEquals(0f, fromKj.carbsPer100g) // missing macros read as 0, not as a failure

        val macros = """{"status":1,"product":{"product_name":"Bar","nutriments":{"proteins_100g":10,"carbohydrates_100g":50,"fat_100g":20}}}"""
        val fromMacros = assertIs<BarcodeLookupResult.Found>(OpenFoodFactsParser.parse("12345670", macros)).product
        assertNear(420f, fromMacros.kcalPer100g)
    }

    @Test
    fun missingOrSillyServingSizeIsIgnored() {
        val noServing = """{"status":1,"product":{"product_name":"Milk","nutriments":{"energy-kcal_100g":64}}}"""
        assertNull(assertIs<BarcodeLookupResult.Found>(OpenFoodFactsParser.parse("12345670", noServing)).product.servingGrams)

        val huge = """{"status":1,"product":{"product_name":"Milk","serving_quantity":50000,"nutriments":{"energy-kcal_100g":64}}}"""
        assertNull(assertIs<BarcodeLookupResult.Found>(OpenFoodFactsParser.parse("12345670", huge)).product.servingGrams)
    }

    @Test
    fun unnamedProductGetsAGenericName() {
        val body = """{"status":1,"product":{"nutriments":{"energy-kcal_100g":100}}}"""
        assertEquals("Packaged food", assertIs<BarcodeLookupResult.Found>(OpenFoodFactsParser.parse("12345670", body)).product.name)
    }

    @Test
    fun garbageBodyIsFailedNotCrash() {
        assertIs<BarcodeLookupResult.Failed>(OpenFoodFactsParser.parse("12345670", "<html>busy</html>"))
    }

    // ── Scaling ───────────────────────────────────────────────────────────────

    @Test
    fun servingSizeScalesPer100gValues() {
        val product = assertIs<BarcodeLookupResult.Found>(OpenFoodFactsParser.parse("3017620422003", foundJson)).product
        val response = product.toNutritionResponse()
        val item = response.items.single()
        assertEquals(15, item.weight_est_g)
        assertEquals(81, item.calories) // 539 * 0.15 = 80.85
        assertNear(0.95f, item.protein_g, tolerance = 0.06f) // 6.3 * 0.15 = 0.945, rounded to 0.9 or 1.0
        assertNear(8.6f, item.carbs_g) // 57.5 * 0.15 = 8.625
        assertNear(4.6f, item.fat_g) // 30.9 * 0.15 = 4.635
        assertEquals(item.calories, response.totals.calories)
        assertEquals(item.protein_g, response.totals.protein_g)
        assertEquals("Nutella (Ferrero)", response.meal_name)
    }

    @Test
    fun noServingSizeUses100g() {
        val product = BarcodeProduct("12345670", "Rice", null, 360f, 7f, 79f, 0.6f, servingGrams = null)
        assertEquals(DEFAULT_BARCODE_PORTION_GRAMS, product.portionGrams())
        val item = product.toNutritionResponse().items.single()
        assertEquals(100, item.weight_est_g)
        assertEquals(360, item.calories)
        assertNear(79f, item.carbs_g)
    }

    @Test
    fun explicitWeightOverridesServing() {
        val product = BarcodeProduct("12345670", "Rice", null, 360f, 7f, 79f, 0.6f, servingGrams = 75f)
        val item = product.toNutritionResponse(grams = 250).items.single()
        assertEquals(250, item.weight_est_g)
        assertEquals(900, item.calories)
        assertNear(17.5f, item.protein_g)
    }

    @Test
    fun brandAlreadyInNameIsNotRepeated() {
        val product = BarcodeProduct("12345670", "Ferrero Rocher", "Ferrero", 600f, 8f, 44f, 43f)
        assertEquals("Ferrero Rocher", product.toNutritionResponse().meal_name)
    }

    // ── Barcode normalisation ─────────────────────────────────────────────────

    @Test
    fun normalizesRetailBarcodes() {
        assertEquals("3017620422003", normalizeBarcode(" 3017620422003 "))
        assertEquals("0049000006346", normalizeBarcode("049000006346")) // UPC-A -> EAN-13
        assertEquals("12345670", normalizeBarcode("12345670"))
        assertNull(normalizeBarcode("https://example.com"))
        assertNull(normalizeBarcode("12345"))
        assertNull(normalizeBarcode(""))
    }

    // ── Client: header, not found, cache ─────────────────────────────────────

    @Test
    fun sendsUserAgentAndCachesSuccessfulLookups() = runBlocking {
        var calls = 0
        var userAgent: String? = null
        var requestedUrl = ""
        val engine = MockEngine { request ->
            calls++
            userAgent = request.headers[HttpHeaders.UserAgent]
            requestedUrl = request.url.toString()
            respond(foundJson, HttpStatusCode.OK, headersOf(HttpHeaders.ContentType, "application/json"))
        }
        val storage = MemoryStorage()
        val client = OpenFoodFactsClient(storage = storage, client = HttpClient(engine))

        val first = assertIs<BarcodeLookupResult.Found>(client.lookup("3017620422003"))
        assertEquals(false, first.fromCache)
        assertEquals("FitCal/1.0 (fitcal@tecaa.xyz)", userAgent)
        assertTrue(requestedUrl.startsWith("https://world.openfoodfacts.org/api/v2/product/3017620422003.json"), requestedUrl)

        val second = assertIs<BarcodeLookupResult.Found>(client.lookup("3017620422003"))
        assertEquals(true, second.fromCache)
        assertEquals(first.product, second.product)
        assertEquals(1, calls)
    }

    @Test
    fun http404IsNotFoundAndNotCached() = runBlocking {
        var calls = 0
        val engine = MockEngine {
            calls++
            respond(notFoundJson, HttpStatusCode.NotFound, headersOf(HttpHeaders.ContentType, "application/json"))
        }
        val storage = MemoryStorage()
        val client = OpenFoodFactsClient(storage = storage, client = HttpClient(engine))

        assertEquals(BarcodeLookupResult.NotFound("0000000000000"), client.lookup("0000000000000"))
        client.lookup("0000000000000")
        assertEquals(2, calls)
        assertTrue(storage.map.isEmpty())
    }

    @Test
    fun serverErrorIsFailed() = runBlocking {
        val client = OpenFoodFactsClient(client = HttpClient(MockEngine { respondError(HttpStatusCode.ServiceUnavailable) }))
        assertIs<BarcodeLookupResult.Failed>(client.lookup("3017620422003"))
    }

    @Test
    fun invalidCodeNeverHitsTheNetwork() = runBlocking {
        var calls = 0
        val client = OpenFoodFactsClient(client = HttpClient(MockEngine { calls++; respondError(HttpStatusCode.OK) }))
        assertIs<BarcodeLookupResult.NotFound>(client.lookup("not-a-barcode"))
        assertEquals(0, calls)
    }
}
