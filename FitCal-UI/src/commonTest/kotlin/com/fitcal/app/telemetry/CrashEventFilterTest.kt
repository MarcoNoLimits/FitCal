package com.fitcal.app.telemetry

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

// Stand-ins with the same simple names as the JVM / Ktor network errors.
private class UnknownHostException(message: String?) : Exception(message)
private class ConnectException(message: String?) : Exception(message)
private class SocketTimeoutException(message: String?) : Exception(message)

/** Sentry beforeSend predicate: offline errors are expected and are dropped. */
class CrashEventFilterTest {

    @Test
    fun dropsEachNetworkUnavailableType() {
        assertTrue(CrashEventFilter.isNetworkUnavailable(UnknownHostException("x")))
        assertTrue(CrashEventFilter.isNetworkUnavailable(ConnectException("x")))
        assertTrue(CrashEventFilter.isNetworkUnavailable(SocketTimeoutException("x")))
    }

    @Test
    fun dropsWhenTheRootCauseIsOffline() {
        // FITCAL-2: token refresh while offline, wrapped by the auth library.
        val wrapped = IllegalStateException(
            "Session refresh failed",
            RuntimeException("HTTP request failed", UnknownHostException("Unable to resolve host \"x.supabase.co\"")),
        )
        assertTrue(CrashEventFilter.isNetworkUnavailable(wrapped))
    }

    @Test
    fun dropsOnUnresolvedHostMessageAlone() {
        assertTrue(CrashEventFilter.isNetworkUnavailable(RuntimeException("Unable to resolve host \"x.supabase.co\": No address associated with hostname")))
    }

    @Test
    fun keepsRealBugs() {
        assertFalse(CrashEventFilter.isNetworkUnavailable(IllegalStateException("Compose state read outside composition")))
        assertFalse(CrashEventFilter.isNetworkUnavailable(NullPointerException()))
        assertFalse(CrashEventFilter.isNetworkUnavailable(RuntimeException("Gateway error (500)", IllegalArgumentException("bad json"))))
    }

    @Test
    fun matchesSentryExceptionListByQualifiedOrSimpleType() {
        assertTrue(CrashEventFilter.shouldDrop(listOf("java.net.UnknownHostException" to "Unable to resolve host")))
        assertTrue(CrashEventFilter.shouldDrop(listOf("RuntimeException" to "boom", "SocketTimeoutException" to "timeout")))
        assertTrue(CrashEventFilter.shouldDrop(listOf("io.ktor.client.network.sockets.ConnectTimeoutException" to null)))
        assertFalse(CrashEventFilter.shouldDrop(listOf("java.lang.IllegalStateException" to "boom")))
        assertFalse(CrashEventFilter.shouldDrop(listOf(null to null)))
        assertFalse(CrashEventFilter.shouldDrop(emptyList()))
    }

    @Test
    fun chainListsOutermostFirst() {
        val chain = CrashEventFilter.chainOf(RuntimeException("outer", ConnectException("inner")))
        assertEquals(listOf<Pair<String?, String?>>("RuntimeException" to "outer", "ConnectException" to "inner"), chain)
    }
}
