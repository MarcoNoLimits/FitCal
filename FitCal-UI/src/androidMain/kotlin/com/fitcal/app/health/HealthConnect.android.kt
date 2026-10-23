package com.fitcal.app.health

import android.content.Intent
import androidx.activity.result.ActivityResultLauncher
import androidx.health.connect.client.HealthConnectClient
import androidx.health.connect.client.permission.HealthPermission
import androidx.health.connect.client.records.ActiveCaloriesBurnedRecord
import androidx.health.connect.client.records.StepsRecord
import androidx.health.connect.client.records.WeightRecord
import androidx.health.connect.client.request.AggregateRequest
import androidx.health.connect.client.request.ReadRecordsRequest
import androidx.health.connect.client.time.TimeRangeFilter
import com.fitcal.app.appContext
import com.fitcal.app.isAppContextReady
import com.fitcal.shared.model.WeightEntry
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import kotlin.math.roundToInt

/** The read permissions FitCal asks for. Weight is the one that matters; the others are optional. */
internal val HEALTH_PERMISSIONS = setOf(
    HealthPermission.getReadPermission(WeightRecord::class),
    HealthPermission.getReadPermission(StepsRecord::class),
    HealthPermission.getReadPermission(ActiveCaloriesBurnedRecord::class),
)

/**
 * Holds the permission launcher MainActivity registers (it must be registered before the
 * activity starts) and the callback of the request in flight.
 */
object HealthConnectBridge {
    var launcher: ActivityResultLauncher<Set<String>>? = null
    private var pending: ((Boolean) -> Unit)? = null

    fun onPermissionsResult(granted: Set<String>) {
        val callback = pending
        pending = null
        callback?.invoke(HealthPermission.getReadPermission(WeightRecord::class) in granted)
    }

    internal fun request(onResult: (Boolean) -> Unit) {
        val l = launcher ?: return onResult(false)
        pending = onResult
        try {
            l.launch(HEALTH_PERMISSIONS)
        } catch (_: Exception) {
            pending = null
            onResult(false)
        }
    }
}

private fun client(): HealthConnectClient? =
    if (isAppContextReady && healthConnectStatus() == HealthConnectStatus.AVAILABLE) {
        HealthConnectClient.getOrCreate(appContext)
    } else {
        null
    }

actual fun healthConnectStatus(): HealthConnectStatus {
    if (!isAppContextReady) return HealthConnectStatus.UNAVAILABLE
    return when (HealthConnectClient.getSdkStatus(appContext)) {
        HealthConnectClient.SDK_AVAILABLE -> HealthConnectStatus.AVAILABLE
        HealthConnectClient.SDK_UNAVAILABLE_PROVIDER_UPDATE_REQUIRED -> HealthConnectStatus.NEEDS_INSTALL_OR_UPDATE
        else -> HealthConnectStatus.UNAVAILABLE
    }
}

actual fun requestHealthConnectPermissions(onResult: (Boolean) -> Unit) {
    if (healthConnectStatus() != HealthConnectStatus.AVAILABLE) return onResult(false)
    HealthConnectBridge.request(onResult)
}

actual suspend fun hasHealthConnectPermissions(): Boolean = try {
    val granted = client()?.permissionController?.getGrantedPermissions() ?: emptySet()
    HealthPermission.getReadPermission(WeightRecord::class) in granted
} catch (_: Exception) {
    false
}

actual suspend fun readHealthConnectWeights(sinceDate: String): List<WeightEntry> = try {
    val c = client() ?: return emptyList()
    val zone = ZoneId.systemDefault()
    val start = LocalDate.parse(sinceDate).atStartOfDay(zone).toInstant()
    val byDay = sortedMapOf<String, Pair<Instant, Float>>()
    var pageToken: String? = null
    var pages = 0
    do {
        val response = c.readRecords(
            ReadRecordsRequest(WeightRecord::class, TimeRangeFilter.after(start), pageToken = pageToken)
        )
        for (r in response.records) {
            val day = r.time.atZone(r.zoneOffset ?: zone.rules.getOffset(r.time)).toLocalDate().toString()
            val kg = r.weight.inKilograms.toFloat()
            // Several readings on one day: keep the latest.
            val previous = byDay[day]
            if (previous == null || r.time.isAfter(previous.first)) byDay[day] = r.time to kg
        }
        pageToken = response.pageToken
        pages++
    } while (pageToken != null && pages < 20)
    byDay.map { (day, v) -> WeightEntry(day, (v.second * 10f).roundToInt() / 10f) }
} catch (_: Exception) {
    // Permission revoked or Health Connect busy: try again next time.
    emptyList()
}

actual suspend fun readHealthConnectToday(): TodayActivity? = try {
    val c = client() ?: return null
    val granted = c.permissionController.getGrantedPermissions()
    val metrics = buildSet {
        if (HealthPermission.getReadPermission(StepsRecord::class) in granted) add(StepsRecord.COUNT_TOTAL)
        if (HealthPermission.getReadPermission(ActiveCaloriesBurnedRecord::class) in granted) add(ActiveCaloriesBurnedRecord.ACTIVE_CALORIES_TOTAL)
    }
    if (metrics.isEmpty()) {
        null
    } else {
        val zone = ZoneId.systemDefault()
        val result = c.aggregate(
            AggregateRequest(metrics, TimeRangeFilter.between(LocalDate.now(zone).atStartOfDay(zone).toInstant(), Instant.now()))
        )
        TodayActivity(
            steps = result[StepsRecord.COUNT_TOTAL],
            activeKcal = result[ActiveCaloriesBurnedRecord.ACTIVE_CALORIES_TOTAL]?.inKilocalories?.roundToInt(),
        )
    }
} catch (_: Exception) {
    null
}

actual fun openHealthConnectSettings() {
    if (!isAppContextReady) return
    try {
        appContext.startActivity(
            Intent(HealthConnectClient.ACTION_HEALTH_CONNECT_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        )
    } catch (_: Exception) {
        // No Health Connect settings screen on this device.
    }
}
