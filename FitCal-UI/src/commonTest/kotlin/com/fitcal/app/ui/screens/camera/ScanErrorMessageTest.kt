package com.fitcal.app.ui.screens.camera

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse

/** The scan error card shows plain text, never the raw API error. */
class ScanErrorMessageTest {

    @Test
    fun unresolvedHostReadsAsOffline() {
        assertEquals(
            SCAN_ERROR_MESSAGE_OFFLINE,
            friendlyScanErrorMessage("Unable to resolve host \"uedtovphilwstzdpzjrk.supabase.co\": No address associated with hostname"),
        )
    }

    @Test
    fun failedConnectionReadsAsOffline() {
        assertEquals(SCAN_ERROR_MESSAGE_OFFLINE, friendlyScanErrorMessage("Failed to connect to /10.0.0.1:443"))
        assertEquals(SCAN_ERROR_MESSAGE_OFFLINE, friendlyScanErrorMessage("java.net.ConnectException: Network is unreachable"))
        assertEquals(SCAN_ERROR_MESSAGE_OFFLINE, friendlyScanErrorMessage("Network error"))
    }

    @Test
    fun serverErrorsReadAsGeneric() {
        assertEquals(
            SCAN_ERROR_MESSAGE_GENERIC,
            friendlyScanErrorMessage("Gateway error (502): {\"error\":\"All providers failed\",\"detail\":\"gemini 429\"}"),
        )
        assertEquals(SCAN_ERROR_MESSAGE_GENERIC, friendlyScanErrorMessage("Request timeout has expired [url=https://x, request_timeout=30000 ms]"))
        assertEquals(SCAN_ERROR_MESSAGE_GENERIC, friendlyScanErrorMessage("Unknown API Error"))
    }

    @Test
    fun emptyOrMissingErrorReadsAsGeneric() {
        assertEquals(SCAN_ERROR_MESSAGE_GENERIC, friendlyScanErrorMessage(""))
        assertEquals(SCAN_ERROR_MESSAGE_GENERIC, friendlyScanErrorMessage(null))
    }

    @Test
    fun rawTextNeverLeaksIntoTheMessage() {
        val raw = listOf(
            "Gateway error (500): stack trace at analyze-meal/index.ts:120",
            "Unable to resolve host \"api.example.com\"",
            "Gemini API error (gemini-2.0-flash): 403 Forbidden",
        )
        for (r in raw) {
            val shown = friendlyScanErrorMessage(r)
            assertFalse(shown.contains(r), "raw text shown: $r")
            assertFalse(shown.contains("Gateway") || shown.contains("Gemini") || shown.contains("host \""), "leaked: $shown")
        }
    }
}
