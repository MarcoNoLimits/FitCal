package com.fitcal.shared.api

/**
 * Limits and clean-up for the two typed inputs the gateway accepts: a meal description
 * (POST /v1/analyze-text) and a "Tell FitCal what's off" hint (POST /v1/recalculate).
 * Mirrors supabase/functions/analyze-meal/text_input.ts; the gateway re-checks both.
 */
object MealTextLimits {
    /** Longest meal description the gateway accepts (longer text is rejected with 400). */
    const val MAX_DESCRIPTION_CHARS = 500

    /** Longest correction hint the gateway passes to the model (longer text is cut). */
    const val MAX_HINT_CHARS = 200
}

private val controlChars = Regex("[\\u0000-\\u001f\\u007f]")
private val whitespaceRun = Regex("\\s+")

private fun cleanUserText(raw: String): String =
    raw.replace(controlChars, " ").replace(whitespaceRun, " ").trim()

/**
 * The description as it is sent, or null when it can't be sent: blank, or over
 * [MealTextLimits.MAX_DESCRIPTION_CHARS] after trimming.
 */
fun normalizeMealDescription(raw: String): String? {
    val text = cleanUserText(raw)
    return if (text.isEmpty() || text.length > MealTextLimits.MAX_DESCRIPTION_CHARS) null else text
}

/** The hint as it is sent (cut to [MealTextLimits.MAX_HINT_CHARS]), or null when blank. */
fun normalizeFixHint(raw: String?): String? {
    if (raw == null) return null
    val text = cleanUserText(raw).take(MealTextLimits.MAX_HINT_CHARS).trim()
    return text.ifEmpty { null }
}
