package com.fitcal.shared.data

import com.fitcal.shared.model.WeightEntry
import com.fitcal.shared.model.WeightHistory
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** Weight history: one entry per day (the latest wins), stored on the device and restored. */
class WeightHistoryTest {

    @Test
    fun dedupeKeepsTheLatestEntryPerDayAndSortsByDate() {
        val entries = listOf(
            WeightEntry("2026-10-21", 80f),
            WeightEntry("2026-10-20", 81f),
            WeightEntry("2026-10-21", 79.6f),
        )
        assertEquals(
            listOf(WeightEntry("2026-10-20", 81f), WeightEntry("2026-10-21", 79.6f)),
            WeightHistory.dedupePerDay(entries),
        )
    }

    @Test
    fun upsertReplacesTheSameDay() {
        val start = listOf(WeightEntry("2026-10-20", 81f), WeightEntry("2026-10-21", 80f))
        val updated = WeightHistory.upsert(start, WeightEntry("2026-10-21", 79.8f))
        assertEquals(2, updated.size)
        assertEquals(79.8f, updated.last().weightKg)
        val added = WeightHistory.upsert(updated, WeightEntry("2026-10-19", 82f))
        assertEquals(listOf("2026-10-19", "2026-10-20", "2026-10-21"), added.map { it.date })
    }

    @Test
    fun implausibleEntriesAreIgnored() {
        val start = listOf(WeightEntry("2026-10-20", 81f))
        assertEquals(start, WeightHistory.upsert(start, WeightEntry("2026-10-21", 0f)))
        assertEquals(start, WeightHistory.upsert(start, WeightEntry("2026-10-21", 900f)))
        assertEquals(start, WeightHistory.upsert(start, WeightEntry("yesterday", 80f)))
    }

    @Test
    fun serverEntriesOnlyFillDaysMissingOnTheDevice() {
        val local = listOf(WeightEntry("2026-10-21", 80f))
        val remote = listOf(WeightEntry("2026-10-21", 85f), WeightEntry("2026-10-01", 82f))
        assertEquals(
            listOf(WeightEntry("2026-10-01", 82f), WeightEntry("2026-10-21", 80f)),
            WeightHistory.fillMissingDays(local, remote),
        )
    }

    @Test
    fun recordedWeightsSurviveARestartWithOneEntryPerDay() = runBlocking {
        val storage = FakeKeyValueStorage()
        val repo = LocalUserRepository(storage)
        repo.recordWeight(WeightEntry("2026-10-21", 80f))
        repo.recordWeight(WeightEntry("2026-10-22", 79.5f))
        repo.recordWeight(WeightEntry("2026-10-22", 79.2f))

        val reloaded = LocalUserRepository(storage)
        assertEquals(
            listOf(WeightEntry("2026-10-21", 80f), WeightEntry("2026-10-22", 79.2f)),
            reloaded.getWeightEntries(),
        )
        assertEquals(2, reloaded.observeWeightEntries().first().size)
    }

    @Test
    fun mergeAndClearAll() = runBlocking {
        val storage = FakeKeyValueStorage()
        val repo = LocalUserRepository(storage)
        repo.recordWeight(WeightEntry("2026-10-22", 79f))
        repo.mergeWeightEntries(listOf(WeightEntry("2026-10-22", 90f), WeightEntry("2026-10-01", 81f)))
        assertEquals(listOf(81f, 79f), repo.getWeightEntries().map { it.weightKg })

        repo.clearAll()
        assertTrue(repo.getWeightEntries().isEmpty())
        assertTrue(LocalUserRepository(storage).getWeightEntries().isEmpty())
    }
}
