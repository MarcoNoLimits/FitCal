package com.fitcal.shared.api

import com.fitcal.shared.data.KeyValueStorage
import com.fitcal.shared.model.FoodItem
import com.fitcal.shared.model.NutritionResponse
import com.fitcal.shared.model.Totals
import io.ktor.client.HttpClient
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.parameter
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.isSuccess
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.intOrNull
import kotlin.math.roundToInt

/**
 * Contact address sent in the Open Food Facts User-Agent, as their API terms require.
 * It is FitCal's published support address (site/, privacy policy, store listing).
 */
const val FITCAL_CONTACT_EMAIL = "fitcal@tecaa.xyz"

/** `FitCal/1.0 (fitcal@tecaa.xyz)`: Open Food Facts asks every app to identify itself this way. */
const val OPEN_FOOD_FACTS_USER_AGENT = "FitCal/1.0 ($FITCAL_CONTACT_EMAIL)"

/** Weight a barcode product is shown at when the label gives no serving size. */
const val DEFAULT_BARCODE_PORTION_GRAMS = 100

/** A serving above this is treated as bad data (a whole multipack, a typo) and ignored. */
private const val MAX_SERVING_GRAMS = 2_000f

/** A packaged food as read from Open Food Facts, with label values per 100 g. */
@Serializable
data class BarcodeProduct(
    val code: String,
    val name: String,
    val brand: String? = null,
    val kcalPer100g: Float,
    val proteinPer100g: Float,
    val carbsPer100g: Float,
    val fatPer100g: Float,
    /** The label's serving size in grams, or null when the label doesn't give one. */
    val servingGrams: Float? = null,
)

sealed class BarcodeLookupResult {
    data class Found(val product: BarcodeProduct, val fromCache: Boolean = false) : BarcodeLookupResult()

    /** Open Food Facts doesn't know this barcode (or the code isn't a food barcode at all). */
    data class NotFound(val code: String) : BarcodeLookupResult()

    /** The product exists but has no usable nutrition facts; [name] is shown to the user if known. */
    data class MissingNutrition(val code: String, val name: String?) : BarcodeLookupResult()

    /** Network or server trouble; worth a retry. [message] is for logs, never for the UI. */
    data class Failed(val code: String, val message: String) : BarcodeLookupResult()
}

/**
 * Normalises a scanned value to the barcode Open Food Facts stores: digits only, EAN-8, UPC-E,
 * UPC-A or EAN-13/GTIN-14. A 12-digit UPC-A becomes its 13-digit EAN form (leading 0), which is
 * how Open Food Facts keys it, so both spellings share one cache entry. Returns null for anything
 * that isn't a retail food barcode (QR codes, URLs, short codes).
 */
fun normalizeBarcode(raw: String): String? {
    val code = raw.trim()
    if (code.isEmpty() || !code.all { it in '0'..'9' }) return null
    return when (code.length) {
        8, 13, 14 -> code
        12 -> "0$code"
        else -> null
    }
}

/** Pure parsing of the Open Food Facts v2 product JSON. Kept separate so it can be unit-tested. */
object OpenFoodFactsParser {
    private val json = Json { ignoreUnknownKeys = true; isLenient = true }

    private const val KJ_PER_KCAL = 4.184f

    fun parse(code: String, body: String): BarcodeLookupResult {
        val root = try {
            json.parseToJsonElement(body) as? JsonObject
        } catch (e: Exception) {
            null
        } ?: return BarcodeLookupResult.Failed(code, "Unreadable Open Food Facts response")

        val status = (root["status"] as? JsonPrimitive)?.intOrNull
        val product = root["product"] as? JsonObject
        if (status == 0 || product == null) return BarcodeLookupResult.NotFound(code)

        val name = productName(product)
        val nutriments = product["nutriments"] as? JsonObject
            ?: return BarcodeLookupResult.MissingNutrition(code, name)

        val protein = nutriments.number("proteins_100g")
        val carbs = nutriments.number("carbohydrates_100g")
        val fat = nutriments.number("fat_100g")
        val kcal = nutriments.number("energy-kcal_100g")
            ?: nutriments.number("energy-kj_100g")?.div(KJ_PER_KCAL)
            // Plain `energy_100g` is always kJ in Open Food Facts.
            ?: nutriments.number("energy_100g")?.div(KJ_PER_KCAL)
            ?: if (protein != null || carbs != null || fat != null) {
                4f * (protein ?: 0f) + 4f * (carbs ?: 0f) + 9f * (fat ?: 0f)
            } else {
                null
            }

        if (kcal == null || kcal < 0f) return BarcodeLookupResult.MissingNutrition(code, name)

        val serving = product.number("serving_quantity")
            ?.takeIf { it > 0f && it <= MAX_SERVING_GRAMS }

        return BarcodeLookupResult.Found(
            BarcodeProduct(
                code = code,
                name = name ?: "Packaged food",
                brand = product.text("brands")?.substringBefore(',')?.trim()?.takeIf { it.isNotEmpty() },
                kcalPer100g = kcal,
                proteinPer100g = (protein ?: 0f).coerceAtLeast(0f),
                carbsPer100g = (carbs ?: 0f).coerceAtLeast(0f),
                fatPer100g = (fat ?: 0f).coerceAtLeast(0f),
                servingGrams = serving,
            )
        )
    }

    private fun productName(product: JsonObject): String? =
        product.text("product_name")
            ?: product.text("product_name_en")
            ?: product.text("generic_name")

    private fun JsonObject.text(key: String): String? {
        val value = this[key] as? JsonPrimitive ?: return null
        if (value is JsonNull || !value.isString) return null
        return value.content.trim().takeIf { it.isNotEmpty() }
    }

    /** Open Food Facts sends numbers either as JSON numbers or as strings ("30", "12,5"). */
    private fun JsonObject.number(key: String): Float? {
        val value: JsonElement = this[key] ?: return null
        if (value !is JsonPrimitive || value is JsonNull) return null
        return value.content.trim().replace(',', '.').toFloatOrNull()?.takeIf { it.isFinite() }
    }
}

/** The weight a barcode product opens the review screen at: the label serving, or 100 g. */
fun BarcodeProduct.portionGrams(): Int =
    servingGrams?.roundToInt()?.takeIf { it > 0 } ?: DEFAULT_BARCODE_PORTION_GRAMS

private fun Float.scaledTo(grams: Int): Float = this * grams / 100f

private fun Float.oneDecimal(): Float = (this * 10f).roundToInt() / 10f

/**
 * A one-item [NutritionResponse] for the review screen, scaled from the label's per-100 g values
 * to [grams] (default: [portionGrams]). The review screen derives per-gram rates from this item,
 * so changing the weight there recalculates locally without any AI call.
 */
fun BarcodeProduct.toNutritionResponse(grams: Int = portionGrams()): NutritionResponse {
    val weight = grams.coerceAtLeast(1)
    val displayName = if (brand != null && !name.contains(brand, ignoreCase = true)) "$name ($brand)" else name
    val item = FoodItem(
        item = displayName,
        weight_est_g = weight,
        calories = kcalPer100g.scaledTo(weight).roundToInt(),
        protein_g = proteinPer100g.scaledTo(weight).oneDecimal(),
        carbs_g = carbsPer100g.scaledTo(weight).oneDecimal(),
        fat_g = fatPer100g.scaledTo(weight).oneDecimal(),
        confidence = "high",
    )
    val portionNote = if (servingGrams != null) "one serving ($weight g)" else "$weight g (no serving size on the label)"
    return NutritionResponse(
        meal_name = displayName,
        items = listOf(item),
        totals = Totals(item.calories, item.protein_g, item.carbs_g, item.fat_g),
        estimation_notes = "Label values from Open Food Facts, scaled to $portionNote.",
    )
}

/**
 * Looks packaged foods up by barcode on Open Food Facts (free, no key, no AI scan).
 *
 * - `GET {baseUrl}/api/v2/product/{code}.json` with the `User-Agent` their terms ask for.
 * - Successful lookups are cached in [storage] by barcode, so a repeat scan is instant and works offline.
 * - Never throws: every outcome is a [BarcodeLookupResult].
 */
class OpenFoodFactsClient(
    private val storage: KeyValueStorage? = null,
    client: HttpClient? = null,
    private val baseUrl: String = "https://world.openfoodfacts.org",
) {
    private val cacheJson = Json { ignoreUnknownKeys = true }

    private val httpClient = client ?: HttpClient {
        install(HttpTimeout) {
            connectTimeoutMillis = 5_000
            requestTimeoutMillis = 10_000
            socketTimeoutMillis = 10_000
        }
    }

    suspend fun lookup(rawCode: String): BarcodeLookupResult {
        val code = normalizeBarcode(rawCode) ?: return BarcodeLookupResult.NotFound(rawCode.trim())

        cached(code)?.let { return BarcodeLookupResult.Found(it, fromCache = true) }

        val result = try {
            val response = httpClient.get("${baseUrl.trimEnd('/')}/api/v2/product/$code.json") {
                header(HttpHeaders.UserAgent, OPEN_FOOD_FACTS_USER_AGENT)
                parameter("fields", FIELDS)
            }
            when {
                response.status.isSuccess() -> OpenFoodFactsParser.parse(code, response.bodyAsText())
                response.status == HttpStatusCode.NotFound -> BarcodeLookupResult.NotFound(code)
                else -> BarcodeLookupResult.Failed(code, "Open Food Facts HTTP ${response.status.value}")
            }
        } catch (e: Exception) {
            BarcodeLookupResult.Failed(code, e.message ?: e::class.simpleName ?: "network error")
        }

        if (result is BarcodeLookupResult.Found) store(result.product)
        return result
    }

    private fun cacheKey(code: String) = "$CACHE_PREFIX$code"

    private fun cached(code: String): BarcodeProduct? {
        val raw = storage?.getString(cacheKey(code), "").orEmpty()
        if (raw.isEmpty()) return null
        return try {
            cacheJson.decodeFromString(BarcodeProduct.serializer(), raw)
        } catch (e: Exception) {
            storage?.remove(cacheKey(code))
            null
        }
    }

    private fun store(product: BarcodeProduct) {
        storage?.putString(cacheKey(product.code), cacheJson.encodeToString(BarcodeProduct.serializer(), product))
    }

    companion object {
        const val CACHE_PREFIX = "off_product_"

        /** Only what FitCal reads, to keep the response small on mobile data. */
        const val FIELDS = "product_name,product_name_en,generic_name,brands,nutriments,serving_quantity"
    }
}
