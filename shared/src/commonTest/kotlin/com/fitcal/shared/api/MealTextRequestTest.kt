package com.fitcal.shared.api

import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.MockRequestHandler
import io.ktor.client.engine.mock.respond
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode
import io.ktor.http.content.TextContent
import io.ktor.http.headersOf
import io.ktor.serialization.kotlinx.json.json
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.test.fail

/** WP3: "Describe your meal" (POST /v1/analyze-text) and the review-screen fix hint. */
class MealTextRequestTest {

    private val nutritionJson = """
        {"meal_name":"Breakfast","items":[{"item":"Egg","weight_est_g":100,"calories":143,
        "protein_g":12.6,"carbs_g":0.7,"fat_g":9.5,"confidence":"medium"}],
        "totals":{"calories":143,"protein_g":12.6,"carbs_g":0.7,"fat_g":9.5},
        "estimation_notes":"From a description"}
    """.trimIndent()

    private fun mockClient(handler: MockRequestHandler) = HttpClient(MockEngine(handler)) {
        install(ContentNegotiation) { json(Json { ignoreUnknownKeys = true; isLenient = true }) }
    }

    private fun gateway(client: HttpClient, jwt: () -> String? = { "jwt" }, reAuth: (suspend () -> Unit)? = null) =
        GatewayNutritionClient(
            gatewayUrl = "https://gw.test/functions/v1/analyze-meal",
            jwtProvider = jwt,
            reAuthenticator = reAuth,
            client = client,
        )

    // ── Pure request building ──────────────────────────────────────────────

    @Test
    fun descriptionIsTrimmedAndFlattened() {
        assertEquals(
            "2 eggs, toast with butter, black coffee",
            normalizeMealDescription("  2 eggs,\ntoast with butter,\r\n\tblack coffee  "),
        )
    }

    @Test
    fun blankOrTooLongDescriptionIsNotSendable() {
        assertNull(normalizeMealDescription(""))
        assertNull(normalizeMealDescription("   \n "))
        assertNull(normalizeMealDescription("a".repeat(MealTextLimits.MAX_DESCRIPTION_CHARS + 1)))
        assertEquals(
            MealTextLimits.MAX_DESCRIPTION_CHARS,
            normalizeMealDescription("a".repeat(MealTextLimits.MAX_DESCRIPTION_CHARS))?.length,
        )
        assertNull(buildAnalyzeTextRequest(" "))
        assertEquals(AnalyzeTextRequest("toast"), buildAnalyzeTextRequest(" toast "))
    }

    @Test
    fun hintIsCleanedCutAndDroppedWhenBlank() {
        assertNull(normalizeFixHint(null))
        assertNull(normalizeFixHint("  "))
        assertEquals("it was fried in butter", normalizeFixHint(" it was fried\nin butter "))
        assertEquals(MealTextLimits.MAX_HINT_CHARS, normalizeFixHint("x".repeat(500))?.length)
    }

    @Test
    fun recalculateRequestCarriesItemsAndOptionalHint() {
        val withHint = buildRecalculateRequest(listOf("Egg" to 100, "Toast" to 40), "fried in butter")
        assertEquals(listOf(RecalcItem("Egg", 100), RecalcItem("Toast", 40)), withHint.items)
        assertEquals("fried in butter", withHint.hint)
        assertNull(buildRecalculateRequest(listOf("Egg" to 100), "   ").hint)
    }

    // ── HTTP behaviour ─────────────────────────────────────────────────────

    @Test
    fun analyzeTextPostsDescriptionToAnalyzeTextRoute() = runBlocking {
        var path = ""
        var method: HttpMethod? = null
        var auth: String? = null
        var body = ""
        val client = mockClient { request ->
            path = request.url.encodedPath
            method = request.method
            auth = request.headers["Authorization"]
            body = (request.body as TextContent).text
            respond(nutritionJson, HttpStatusCode.OK, headersOf(HttpHeaders.ContentType, "application/json"))
        }

        val result = gateway(client).analyzeMealTextWithResult("  2 eggs,\ntoast with butter ")

        assertTrue(result is AnalyzeResult.Success, "got $result")
        assertEquals("Breakfast", (result as AnalyzeResult.Success).response.meal_name)
        assertEquals("/functions/v1/analyze-meal/v1/analyze-text", path)
        assertEquals(HttpMethod.Post, method)
        assertEquals("Bearer jwt", auth)
        val sent = Json.parseToJsonElement(body).jsonObject
        assertEquals(setOf("description"), sent.keys, "only the description is sent")
        assertEquals("2 eggs, toast with butter", sent["description"]!!.jsonPrimitive.content)
    }

    @Test
    fun analyzeTextMapsQuotaExhaustedLikeAPhotoScan() = runBlocking {
        val client = mockClient {
            respond(
                """{"error":"quota_exhausted","message":"Daily scan quota exceeded."}""",
                HttpStatusCode(402, "Payment Required"),
                headersOf(HttpHeaders.ContentType, "application/json"),
            )
        }
        assertEquals(AnalyzeResult.QuotaExhausted, gateway(client).analyzeMealTextWithResult("toast"))
    }

    @Test
    fun analyzeTextReauthenticatesOnceOn401() = runBlocking {
        var token: String? = "expired"
        var calls = 0
        val client = mockClient { request ->
            calls++
            if (request.headers["Authorization"] == "Bearer fresh") {
                respond(nutritionJson, HttpStatusCode.OK, headersOf(HttpHeaders.ContentType, "application/json"))
            } else {
                respond("""{"error":"Unauthorized"}""", HttpStatusCode.Unauthorized, headersOf(HttpHeaders.ContentType, "application/json"))
            }
        }
        val result = gateway(client, jwt = { token }, reAuth = { token = "fresh" }).analyzeMealTextWithResult("toast")
        assertTrue(result is AnalyzeResult.Success, "got $result")
        assertEquals(2, calls)
    }

    @Test
    fun analyzeTextRejectsUnsendableTextWithoutARequest() = runBlocking {
        val client = mockClient { fail("no request for text that can't be sent") }
        val gw = gateway(client)
        assertTrue(gw.analyzeMealTextWithResult("   ") is AnalyzeResult.Failed)
        assertTrue(gw.analyzeMealTextWithResult("a".repeat(MealTextLimits.MAX_DESCRIPTION_CHARS + 1)) is AnalyzeResult.Failed)
    }

    @Test
    fun analyzeTextWithoutSessionNeedsAuth() = runBlocking {
        val client = mockClient { fail("no request without a token") }
        assertEquals(AnalyzeResult.AuthRequired, gateway(client, jwt = { null }).analyzeMealTextWithResult("toast"))
    }

    @Test
    fun recalculateSendsHintOnlyWhenGiven() = runBlocking {
        val bodies = mutableListOf<String>()
        val paths = mutableListOf<String>()
        val client = mockClient { request ->
            paths += request.url.encodedPath
            bodies += (request.body as TextContent).text
            respond(nutritionJson, HttpStatusCode.OK, headersOf(HttpHeaders.ContentType, "application/json"))
        }
        val gw = gateway(client)

        gw.recalculateMealNutrition(listOf("Egg" to 100), "it was fried in butter")
        gw.recalculateMealNutrition(listOf("Egg" to 100))

        assertTrue(paths.all { it.endsWith("/v1/recalculate") })
        val withHint = Json.parseToJsonElement(bodies[0]).jsonObject
        assertEquals("it was fried in butter", withHint["hint"]!!.jsonPrimitive.content)
        assertEquals("Egg", withHint["items"]!!.jsonArray[0].jsonObject["name"]!!.jsonPrimitive.content)
        assertFalse("hint" in Json.parseToJsonElement(bodies[1]).jsonObject, "no hint key without a hint")
    }
}
