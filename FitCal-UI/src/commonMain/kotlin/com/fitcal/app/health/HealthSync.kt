package com.fitcal.app.health

import com.fitcal.app.loadPreference
import com.fitcal.app.savePreference
import com.fitcal.shared.model.WeightEntry
import com.fitcal.shared.model.WeightHistory

/*
 * Health Connect, read only (owner decision 2026-10-23): weight from smart scales and other apps
 * goes into FitCal's weight history (Progress, check-ins, the adjusting target); today's steps
 * and active calories are shown on the dashboard as information. Nothing is added to the
 * calorie budget and nothing is written back. Android only; other platforms report UNAVAILABLE.
 */

enum class HealthConnectStatus { AVAILABLE, NEEDS_INSTALL_OR_UPDATE, UNAVAILABLE }

/** Today's movement from Health Connect. Either value may be missing. */
data class TodayActivity(val steps: Long?, val activeKcal: Int?)

/** Whether Health Connect can be used on this device. */
expect fun healthConnectStatus(): HealthConnectStatus

/** Opens the Health Connect permission screen; [onResult] gets true when weight access was granted. */
expect fun requestHealthConnectPermissions(onResult: (Boolean) -> Unit)

/** True when FitCal currently holds the read permissions it asks for. */
expect suspend fun hasHealthConnectPermissions(): Boolean

/** Weigh-ins on or after [sinceDate] (YYYY-MM-DD), one per day (the latest), oldest first. */
expect suspend fun readHealthConnectWeights(sinceDate: String): List<WeightEntry>

/** Steps and active calories so far today, or null when unavailable. */
expect suspend fun readHealthConnectToday(): TodayActivity?

/** Opens the Health Connect app's settings, where access can be changed or revoked. */
expect fun openHealthConnectSettings()

object HealthSyncPrefs {
    private const val KEY_ENABLED = "health_connect_enabled"

    var reader: (String, String) -> String = { k, d -> loadPreference(k, d) }
    var writer: (String, String) -> Unit = { k, v -> savePreference(k, v) }

    fun resetToDefaults() {
        reader = { k, d -> loadPreference(k, d) }
        writer = { k, v -> savePreference(k, v) }
    }

    fun isEnabled(): Boolean = reader(KEY_ENABLED, "false").toBoolean()
    fun setEnabled(enabled: Boolean) = writer(KEY_ENABLED, enabled.toString())
}

/**
 * Health Connect weigh-ins that FitCal doesn't have yet. A day already in FitCal's history
 * (typed in a check-in or Settings) is left alone: the user's own entry wins.
 */
fun weightsToImport(fromHealth: List<WeightEntry>, existing: List<WeightEntry>): List<WeightEntry> {
    val have = existing.map { it.date }.toSet()
    return WeightHistory.dedupePerDay(fromHealth).filter { it.date !in have }
}
