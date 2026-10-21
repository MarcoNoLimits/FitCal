package com.fitcal.app

import com.fitcal.app.ui.screens.describe.AdGatedScan
import com.fitcal.app.ui.screens.describe.DESCRIBE_PLACEHOLDER
import com.fitcal.app.ui.screens.describe.DescribeGate
import com.fitcal.app.ui.screens.describe.DescribeOutcome
import com.fitcal.app.ui.screens.describe.canSubmitDescription
import com.fitcal.app.ui.screens.describe.capDescriptionInput
import com.fitcal.app.ui.screens.describe.describeErrorClass
import com.fitcal.app.ui.screens.describe.describeGate
import com.fitcal.app.ui.screens.describe.describeOutcomeOf
import com.fitcal.shared.api.AnalyzeResult
import com.fitcal.shared.api.MealTextLimits
import com.fitcal.shared.model.NutritionResponse
import com.fitcal.shared.model.Totals
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** WP3 "Describe your meal": the same scan gate and interstitial pairing as a photo scan. */
class DescribeMealTest {

    private val deliver = DescribeOutcome.Deliver("{\"meal_name\":\"Breakfast\"}")
    private val fail = DescribeOutcome.Fail("Couldn't estimate")

    // ── Gate: same readiness and quota wall as CameraScreen ────────────────

    @Test
    fun readyWithQuotaShowsTheForm() {
        assertEquals(DescribeGate.FORM, describeGate(ScanReadiness.Ready, scansRemaining = 3, isAnalyzing = false, hasError = false))
    }

    @Test
    fun authPendingAndOfflineBlockLikeTheCamera() {
        assertEquals(DescribeGate.CONNECTING, describeGate(ScanReadiness.AuthPending, 3, isAnalyzing = false, hasError = false))
        assertEquals(DescribeGate.OFFLINE, describeGate(ScanReadiness.Offline, 3, isAnalyzing = false, hasError = false))
        assertEquals(
            DescribeGate.OFFLINE,
            describeGate(ensureReadyForScan(authReady = false, scansRemaining = 3, networkOnline = false), 3, false, false),
        )
    }

    @Test
    fun emptyQuotaShowsTheQuotaWallNotAHardBlock() {
        // ensureReadyForScan never hard-blocks on quota (AGENTS.md 5.2); the wall comes from scansRemaining.
        val readiness = ensureReadyForScan(authReady = true, scansRemaining = 0)
        assertEquals(ScanReadiness.Ready, readiness)
        assertEquals(DescribeGate.QUOTA_WALL, describeGate(readiness, scansRemaining = 0, isAnalyzing = false, hasError = false))
        assertEquals(DescribeGate.QUOTA_WALL, describeGate(ScanReadiness.QuotaExhausted, 0, false, false))
    }

    @Test
    fun runningScanOrItsErrorStaysOnTheFormWhenQuotaHitsZero() {
        // The scan that used the last free scan must still finish (or show its error) on this screen.
        assertEquals(DescribeGate.FORM, describeGate(ScanReadiness.Ready, scansRemaining = 0, isAnalyzing = true, hasError = false))
        assertEquals(DescribeGate.FORM, describeGate(ScanReadiness.Ready, scansRemaining = 0, isAnalyzing = false, hasError = true))
    }

    // ── Interstitial pairing: AGENTS.md 5.2 ────────────────────────────────

    @Test
    fun withoutAnAdTheAnswerShowsAtOnce() {
        val scan = AdGatedScan(adPlaying = false)
        assertEquals(deliver, scan.onAnswer(deliver))
    }

    @Test
    fun withAnAdTheAnswerWaitsForTheAdToClose() {
        val scan = AdGatedScan(adPlaying = true)
        assertNull(scan.onAnswer(deliver), "result must not show while the interstitial plays")
        assertEquals(deliver, scan.onAdClosed())
    }

    @Test
    fun adClosingFirstWaitsForTheAnswer() {
        val scan = AdGatedScan(adPlaying = true)
        assertNull(scan.onAdClosed())
        assertEquals(fail, scan.onAnswer(fail))
    }

    @Test
    fun resultIsReleasedOnlyOnce() {
        val scan = AdGatedScan(adPlaying = true)
        scan.onAnswer(deliver)
        assertEquals(deliver, scan.onAdClosed())
        assertNull(scan.onAdClosed(), "a second ad callback must not open the review twice")
        assertNull(scan.onAnswer(deliver))
    }

    @Test
    fun cancelledScanNeverShows() {
        val scan = AdGatedScan(adPlaying = true)
        scan.cancel()
        assertNull(scan.onAnswer(deliver))
        assertNull(scan.onAdClosed())
        val noAd = AdGatedScan(adPlaying = false)
        noAd.cancel()
        assertNull(noAd.onAnswer(deliver))
    }

    // ── Result mapping ─────────────────────────────────────────────────────

    @Test
    fun quotaExhaustedFromTheServerOpensTheQuotaCard() {
        val outcome = describeOutcomeOf(AnalyzeResult.QuotaExhausted) { "" }
        assertTrue(outcome is DescribeOutcome.Fail && outcome.isQuota)
        assertEquals("quota", describeErrorClass(AnalyzeResult.QuotaExhausted))
    }

    @Test
    fun successIsEncodedForTheReviewScreen() {
        val response = NutritionResponse("Breakfast", emptyList(), Totals(0, 0f, 0f, 0f), "")
        val outcome = describeOutcomeOf(AnalyzeResult.Success(response)) { it.response.meal_name }
        assertEquals(DescribeOutcome.Deliver("Breakfast"), outcome)
        assertNull(describeErrorClass(AnalyzeResult.Success(response)))
    }

    @Test
    fun failuresShowPlainWordsNeverServerText() {
        val rejected = describeOutcomeOf(AnalyzeResult.Failed("Gateway error (400): {\"error\":\"Bad Request\"}", 400)) { "" }
        assertTrue(rejected is DescribeOutcome.Fail && !rejected.isQuota)
        val message = (rejected as DescribeOutcome.Fail).message
        assertTrue(message.contains(DESCRIBE_PLACEHOLDER))
        assertFalse(message.contains("Gateway"))

        val server = describeOutcomeOf(AnalyzeResult.Failed("Gateway error (500): boom", 500)) { "" }
        assertTrue(server is DescribeOutcome.Fail && !server.message.contains("boom"))
        assertEquals("http_500", describeErrorClass(AnalyzeResult.Failed("x", 500)))
        assertEquals("network", describeErrorClass(AnalyzeResult.Failed("timeout")))
        assertEquals("auth", describeErrorClass(AnalyzeResult.AuthRequired))
    }

    // ── Input ──────────────────────────────────────────────────────────────

    @Test
    fun inputIsCappedAndBlankCannotBeSent() {
        assertFalse(canSubmitDescription("   "))
        assertTrue(canSubmitDescription(DESCRIBE_PLACEHOLDER))
        val long = "a".repeat(MealTextLimits.MAX_DESCRIPTION_CHARS + 20)
        assertEquals(MealTextLimits.MAX_DESCRIPTION_CHARS, capDescriptionInput(long).length)
        assertTrue(canSubmitDescription(capDescriptionInput(long)))
    }
}
