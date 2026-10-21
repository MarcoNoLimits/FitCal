package com.fitcal.app.telemetry

/**
 * Decides which crash-report events are noise. A request made while the phone is
 * offline (for example a token refresh, FITCAL-2) fails with a network-unavailable
 * error; that is expected behaviour, not a bug, so it is not sent to Sentry.
 *
 * Pure logic so it can be unit tested; the Sentry `beforeSend` hook on Android calls it.
 */
object CrashEventFilter {

    /** Simple class names of errors that mean "no network", on the JVM and in Ktor. */
    private val networkUnavailableTypes = setOf(
        "UnknownHostException",
        "ConnectException",
        "SocketTimeoutException",
        "ConnectTimeoutException",
    )

    private const val UNRESOLVED_HOST = "unable to resolve host"

    /** Cause chains are short; this only guards against a pathological self-referencing chain. */
    private const val MAX_CHAIN_DEPTH = 16

    /**
     * True when any exception in the chain, given as (simple class name, message) pairs,
     * is a network-unavailable error. Accepts a fully qualified name as well.
     */
    fun isNetworkUnavailable(chain: List<Pair<String?, String?>>): Boolean =
        chain.any { (type, message) ->
            val simpleName = type?.substringAfterLast('.')
            simpleName in networkUnavailableTypes ||
                message?.contains(UNRESOLVED_HOST, ignoreCase = true) == true
        }

    /** Same check on a throwable and its causes. */
    fun isNetworkUnavailable(throwable: Throwable): Boolean = isNetworkUnavailable(chainOf(throwable))

    /** A throwable and its causes as (simple class name, message) pairs, outermost first. */
    fun chainOf(throwable: Throwable): List<Pair<String?, String?>> {
        val chain = mutableListOf<Pair<String?, String?>>()
        var current: Throwable? = throwable
        while (current != null && chain.size < MAX_CHAIN_DEPTH) {
            chain += current::class.simpleName to current.message
            val next = current.cause
            current = if (next === current) null else next
        }
        return chain
    }

    /** True when the event should be dropped instead of reported. */
    fun shouldDrop(chain: List<Pair<String?, String?>>): Boolean = isNetworkUnavailable(chain)
}
