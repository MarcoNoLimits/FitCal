package com.fitcal.app

import com.fitcal.app.ui.screens.dashboard.components.formatMl
import com.fitcal.app.ui.screens.dashboard.components.waterAfterGlassTap
import kotlin.test.Test
import kotlin.test.assertEquals

/** Dashboard water card: one glass per 250 ml. */
class WaterTrackerTest {

    @Test
    fun tappingAnEmptyGlassFillsUpToIt() {
        assertEquals(250, waterAfterGlassTap(0, 1))
        assertEquals(1000, waterAfterGlassTap(250, 4))
    }

    @Test
    fun tappingTheLastFullGlassEmptiesIt() {
        assertEquals(750, waterAfterGlassTap(1000, 4))
        assertEquals(0, waterAfterGlassTap(250, 1))
    }

    @Test
    fun tappingAnEarlierFullGlassGoesBackToIt() {
        assertEquals(500, waterAfterGlassTap(1500, 2))
    }

    @Test
    fun formatsThousands() {
        assertEquals("0", formatMl(0))
        assertEquals("750", formatMl(750))
        assertEquals("2,000", formatMl(2000))
        assertEquals("12,250", formatMl(12250))
    }
}
