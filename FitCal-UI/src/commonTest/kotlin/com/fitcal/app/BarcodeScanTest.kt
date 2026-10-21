package com.fitcal.app

import com.fitcal.app.ui.screens.camera.barcode.BarcodeOutcome
import com.fitcal.app.ui.screens.camera.barcode.BarcodeScanState
import com.fitcal.app.ui.screens.camera.barcode.barcodeOutcome
import com.fitcal.app.ui.screens.camera.barcode.barcodeResultLabel
import com.fitcal.app.ui.screens.camera.barcode.manualEntryResponseJson
import com.fitcal.app.ui.screens.review.components.caloriesAtCurrentWeight
import com.fitcal.app.ui.screens.review.components.toEditable
import com.fitcal.shared.api.BarcodeLookupResult
import com.fitcal.shared.api.BarcodeProduct
import com.fitcal.shared.model.NutritionResponse
import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertTrue

class BarcodeScanTest {

    private val oats = BarcodeProduct(
        code = "12345670",
        name = "Rolled Oats",
        brand = null,
        kcalPer100g = 375f,
        proteinPer100g = 13f,
        carbsPer100g = 60f,
        fatPer100g = 7f,
        servingGrams = 40f,
    )

    @Test
    fun foundProductOpensReviewWithOneScaledItem() {
        val outcome = assertIs<BarcodeOutcome.OpenReview>(barcodeOutcome(BarcodeLookupResult.Found(oats)))
        val response = Json.decodeFromString(NutritionResponse.serializer(), outcome.responseJson)
        val item = response.items.single()
        assertEquals("Rolled Oats", item.item)
        assertEquals(40, item.weight_est_g)
        assertEquals(150, item.calories)
        assertFalse(outcome.fromCache)
    }

    @Test
    fun reviewWeightEditRecalculatesFromLabelValues() {
        // The review screen derives per-gram rates from the item, so editing the weight is local maths.
        val outcome = assertIs<BarcodeOutcome.OpenReview>(barcodeOutcome(BarcodeLookupResult.Found(oats)))
        val item = Json.decodeFromString(NutritionResponse.serializer(), outcome.responseJson).items.single()
        val editable = item.toEditable(0).copy(currentWeightStr = "100")
        assertEquals(375, editable.caloriesAtCurrentWeight())
    }

    @Test
    fun notFoundAndMissingNutritionStayOnCameraWithOptions() {
        assertEquals(
            BarcodeOutcome.Stay(BarcodeScanState.NotFound("0000000000000", null)),
            barcodeOutcome(BarcodeLookupResult.NotFound("0000000000000")),
        )
        assertEquals(
            BarcodeOutcome.Stay(BarcodeScanState.NotFound("12345670", "Mystery Crisps")),
            barcodeOutcome(BarcodeLookupResult.MissingNutrition("12345670", "Mystery Crisps")),
        )
        assertEquals(
            BarcodeOutcome.Stay(BarcodeScanState.Failed("12345670")),
            barcodeOutcome(BarcodeLookupResult.Failed("12345670", "timeout")),
        )
    }

    @Test
    fun onlyTheIdleViewfinderAcceptsDetections() {
        assertTrue(BarcodeScanState.Scanning.acceptsDetections)
        assertFalse(BarcodeScanState.LookingUp("12345670").acceptsDetections)
        assertFalse(BarcodeScanState.NotFound("12345670", null).acceptsDetections)
        assertFalse(BarcodeScanState.Failed("12345670").acceptsDetections)
    }

    @Test
    fun manualEntryIsAnEmptyMealNamedAfterTheProduct() {
        val named = Json.decodeFromString(NutritionResponse.serializer(), manualEntryResponseJson("Mystery Crisps"))
        assertEquals("Mystery Crisps", named.meal_name)
        assertTrue(named.items.isEmpty())
        assertEquals(0, named.totals.calories)

        val unnamed = Json.decodeFromString(NutritionResponse.serializer(), manualEntryResponseJson(null))
        assertEquals("Packaged food", unnamed.meal_name)
    }

    @Test
    fun analyticsLabelsCarryNoProductData() {
        assertEquals("found", barcodeResultLabel(BarcodeLookupResult.Found(oats)))
        assertEquals("not_found", barcodeResultLabel(BarcodeLookupResult.NotFound("1")))
        assertEquals("missing_nutrition", barcodeResultLabel(BarcodeLookupResult.MissingNutrition("1", "x")))
        assertEquals("failed", barcodeResultLabel(BarcodeLookupResult.Failed("1", "boom")))
    }
}
