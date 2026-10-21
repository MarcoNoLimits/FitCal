package com.fitcal.app.ui.screens.camera.barcode

import com.fitcal.shared.api.BarcodeLookupResult
import com.fitcal.shared.api.toNutritionResponse
import com.fitcal.shared.model.NutritionResponse
import kotlinx.serialization.json.Json

/** What the camera feeds: a meal photo for the AI, or a barcode for Open Food Facts. */
enum class CaptureMode { Photo, Barcode }

/** The barcode flow on the camera screen. Barcode lookups never use an AI scan or show an ad. */
sealed class BarcodeScanState {
    /** Viewfinder open, waiting for a barcode. */
    object Scanning : BarcodeScanState()

    data class LookingUp(val code: String) : BarcodeScanState()

    /** Unknown product, or one without nutrition facts; [productName] is shown when known. */
    data class NotFound(val code: String, val productName: String?) : BarcodeScanState()

    /** Network trouble; the user can try again. */
    data class Failed(val code: String) : BarcodeScanState()

    /** Only the idle viewfinder reacts to detections, so one barcode triggers one lookup. */
    val acceptsDetections: Boolean get() = this is Scanning
}

/** Where a finished lookup goes: the review screen with this JSON, or a state on the camera screen. */
sealed class BarcodeOutcome {
    data class OpenReview(val responseJson: String, val fromCache: Boolean) : BarcodeOutcome()
    data class Stay(val state: BarcodeScanState) : BarcodeOutcome()
}

fun barcodeOutcome(result: BarcodeLookupResult): BarcodeOutcome = when (result) {
    is BarcodeLookupResult.Found -> BarcodeOutcome.OpenReview(
        responseJson = Json.encodeToString(NutritionResponse.serializer(), result.product.toNutritionResponse()),
        fromCache = result.fromCache,
    )
    is BarcodeLookupResult.NotFound -> BarcodeOutcome.Stay(BarcodeScanState.NotFound(result.code, null))
    is BarcodeLookupResult.MissingNutrition -> BarcodeOutcome.Stay(BarcodeScanState.NotFound(result.code, result.name))
    is BarcodeLookupResult.Failed -> BarcodeOutcome.Stay(BarcodeScanState.Failed(result.code))
}

/** Analytics label for a lookup result; never contains product data. */
fun barcodeResultLabel(result: BarcodeLookupResult): String = when (result) {
    is BarcodeLookupResult.Found -> "found"
    is BarcodeLookupResult.NotFound -> "not_found"
    is BarcodeLookupResult.MissingNutrition -> "missing_nutrition"
    is BarcodeLookupResult.Failed -> "failed"
}

/**
 * Review-screen input for "Add it manually": no items, so the user adds the food themselves
 * (library search or custom values). [productName] names the meal when the barcode told us it.
 */
fun manualEntryResponseJson(productName: String?): String = Json.encodeToString(
    NutritionResponse.serializer(),
    NutritionResponse(
        meal_name = productName?.takeIf { it.isNotBlank() } ?: "Packaged food",
        items = emptyList(),
        totals = com.fitcal.shared.model.Totals(0, 0f, 0f, 0f),
        estimation_notes = "",
    ),
)
