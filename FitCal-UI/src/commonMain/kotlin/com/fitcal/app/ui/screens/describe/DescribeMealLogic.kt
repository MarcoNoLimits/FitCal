package com.fitcal.app.ui.screens.describe

import com.fitcal.app.ScanReadiness
import com.fitcal.shared.api.AnalyzeResult
import com.fitcal.shared.api.MealTextLimits
import com.fitcal.shared.api.normalizeMealDescription

/** Example shown in the empty description field. */
const val DESCRIBE_PLACEHOLDER = "2 eggs, toast with butter, black coffee"

/** What the describe screen shows. Mirrors the photo scan's gate in CameraScreen. */
enum class DescribeGate { CONNECTING, OFFLINE, QUOTA_WALL, FORM }

/**
 * Same rules as the camera: anything but [ScanReadiness.Ready] blocks, and an empty
 * quota shows the quota wall (rewarded ad opt-in, create account) unless a scan is
 * already running or showing its error.
 */
fun describeGate(
    readiness: ScanReadiness,
    scansRemaining: Int,
    isAnalyzing: Boolean,
    hasError: Boolean,
): DescribeGate {
    val blocked = readiness != ScanReadiness.Ready || (scansRemaining <= 0 && !isAnalyzing && !hasError)
    if (!blocked) return DescribeGate.FORM
    return when {
        readiness is ScanReadiness.AuthPending -> DescribeGate.CONNECTING
        readiness is ScanReadiness.Offline -> DescribeGate.OFFLINE
        readiness is ScanReadiness.QuotaExhausted || scansRemaining <= 0 -> DescribeGate.QUOTA_WALL
        else -> DescribeGate.FORM
    }
}

/** True when the typed text can be sent (not blank, within the length limit). */
fun canSubmitDescription(text: String): Boolean = normalizeMealDescription(text) != null

/** Keeps typing within the gateway's limit, so the field never holds unsendable text. */
fun capDescriptionInput(text: String): String = text.take(MealTextLimits.MAX_DESCRIPTION_CHARS)

/** End of one describe request, before it is shown. */
sealed class DescribeOutcome {
    data class Deliver(val responseJson: String) : DescribeOutcome()
    data class Fail(val message: String, val isQuota: Boolean = false) : DescribeOutcome()
}

/** Turns the gateway's typed result into what the screen shows. */
fun describeOutcomeOf(result: AnalyzeResult, encode: (AnalyzeResult.Success) -> String): DescribeOutcome =
    when (result) {
        is AnalyzeResult.Success -> DescribeOutcome.Deliver(encode(result))
        is AnalyzeResult.QuotaExhausted -> DescribeOutcome.Fail("Daily scan quota exhausted", isQuota = true)
        is AnalyzeResult.AuthRequired -> DescribeOutcome.Fail(
            "We couldn't connect to FitCal. Check your connection and try again."
        )
        is AnalyzeResult.Failed -> DescribeOutcome.Fail(
            if (result.httpStatus == 400) {
                "FitCal couldn't read that. List the foods and amounts, for example \"$DESCRIBE_PLACEHOLDER\"."
            } else {
                "We couldn't estimate this meal. Check your connection and try again."
            }
        )
    }

/** Short error class for analytics, in the same vocabulary as the photo scan's `scan_failed`. */
fun describeErrorClass(result: AnalyzeResult): String? = when (result) {
    is AnalyzeResult.Success -> null
    is AnalyzeResult.QuotaExhausted -> "quota"
    is AnalyzeResult.AuthRequired -> "auth"
    is AnalyzeResult.Failed -> result.httpStatus?.let { "http_$it" } ?: "network"
}

/**
 * Pairs the scan interstitial with the AI answer exactly like the photo scan
 * (AGENTS.md 5.2): the request starts at once, and when an ad plays the result is
 * shown only after the ad closes. Each request is released at most once, and never
 * after [cancel].
 */
class AdGatedScan(adPlaying: Boolean) {
    private var adClosed = !adPlaying
    private var answer: DescribeOutcome? = null
    private var released = false
    private var cancelled = false

    /** The AI answered; returns the outcome to show now, or null to keep waiting. */
    fun onAnswer(outcome: DescribeOutcome): DescribeOutcome? {
        answer = outcome
        return release()
    }

    /** The interstitial closed; returns the outcome to show now, or null to keep waiting. */
    fun onAdClosed(): DescribeOutcome? {
        adClosed = true
        return release()
    }

    fun cancel() {
        cancelled = true
    }

    private fun release(): DescribeOutcome? {
        if (cancelled || released || !adClosed) return null
        val ready = answer ?: return null
        released = true
        return ready
    }
}
