package com.fitcal.app

import com.fitcal.app.ui.screens.dashboard.components.ringFraction
import com.fitcal.app.ui.screens.dashboard.components.ringScaleWithoutTarget
import kotlin.test.Test
import kotlin.test.assertEquals

/** Dashboard calorie ring: how full it is, with and without a calorie target. */
class CalorieRingTest {

    private val today = "2026-10-14"

    @Test
    fun withATargetTheRingIsTheShareOfTheGoal() {
        assertEquals(0.5f, ringFraction(consumed = 1000, goal = 2000, scaleKcal = 3000))
        assertEquals(1f, ringFraction(consumed = 2600, goal = 2000, scaleKcal = 0))   // capped when over
        assertEquals(0f, ringFraction(consumed = 0, goal = 2000, scaleKcal = 0))
    }

    @Test
    fun withoutATargetTheRingUsesTheRecentHigh() {
        assertEquals(0.75f, ringFraction(consumed = 1500, goal = 0, scaleKcal = 2000))
        assertEquals(1f, ringFraction(consumed = 2000, goal = 0, scaleKcal = 2000))
        assertEquals(0f, ringFraction(consumed = 800, goal = 0, scaleKcal = 0))      // nothing logged recently
    }

    @Test
    fun recentHighOnlyCountsTheLast30DaysUpToToday() {
        val totals = mapOf(
            "2026-10-14" to 1200,
            "2026-10-10" to 2100,
            "2026-09-15" to 2300,   // 29 days ago: counts
            "2026-09-14" to 3500,   // 30 days ago: too old
            "2026-10-20" to 4000,   // future date: ignored
        )
        assertEquals(2300, ringScaleWithoutTarget(totals, today))
        assertEquals(0, ringScaleWithoutTarget(emptyMap(), today))
    }
}
