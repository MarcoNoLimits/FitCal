package com.fitcal.app.health

import com.fitcal.shared.model.WeightEntry

// iOS is not a v1 target; Apple Health would go here.
actual fun healthConnectStatus(): HealthConnectStatus = HealthConnectStatus.UNAVAILABLE
actual fun requestHealthConnectPermissions(onResult: (Boolean) -> Unit) = onResult(false)
actual suspend fun hasHealthConnectPermissions(): Boolean = false
actual suspend fun readHealthConnectWeights(sinceDate: String): List<WeightEntry> = emptyList()
actual suspend fun readHealthConnectToday(): TodayActivity? = null
actual fun openHealthConnectSettings() {}
