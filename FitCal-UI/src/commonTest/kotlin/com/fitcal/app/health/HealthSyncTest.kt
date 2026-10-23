package com.fitcal.app.health

import com.fitcal.shared.model.WeightEntry
import kotlin.test.Test
import kotlin.test.assertEquals

class HealthSyncTest {

    @Test
    fun importsOnlyDaysFitCalDoesNotHave() {
        val existing = listOf(WeightEntry("2026-10-20", 80f))
        val health = listOf(
            WeightEntry("2026-10-20", 79.4f),   // the user's own entry that day wins
            WeightEntry("2026-10-21", 79.8f),
            WeightEntry("2026-10-22", 79.6f),
        )
        assertEquals(listOf("2026-10-21", "2026-10-22"), weightsToImport(health, existing).map { it.date })
    }

    @Test
    fun dropsImplausibleReadings() {
        val health = listOf(WeightEntry("2026-10-21", 5f), WeightEntry("2026-10-22", 79.6f))
        assertEquals(listOf("2026-10-22"), weightsToImport(health, emptyList()).map { it.date })
    }
}
