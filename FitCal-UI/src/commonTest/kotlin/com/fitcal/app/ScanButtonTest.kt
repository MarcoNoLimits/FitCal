package com.fitcal.app

import com.fitcal.app.ui.screens.dashboard.components.ScanButtonPlacement
import com.fitcal.app.ui.screens.dashboard.components.placeScanButton
import com.fitcal.app.ui.screens.dashboard.components.scanPromptForHour
import com.fitcal.app.ui.screens.dashboard.components.scansLeftLabel
import kotlin.test.Test
import kotlin.test.assertEquals

/** Dashboard scan button: docking in the scan card, and the card's wording. */
class ScanButtonTest {

    private val dock = 1800f
    private val top = 150f

    @Test
    fun staysDockedWhileTheScanCardIsBelowTheDock() {
        assertEquals(ScanButtonPlacement(dock, landed = false), placeScanButton(2400f, dock, top))
    }

    @Test
    fun landsInTheSlotAndFollowsItOnceTheCardReachesTheDock() {
        assertEquals(ScanButtonPlacement(dock, landed = true), placeScanButton(dock, dock, top))
        assertEquals(ScanButtonPlacement(900f, landed = true), placeScanButton(900f, dock, top))
        assertEquals(ScanButtonPlacement(top, landed = true), placeScanButton(top, dock, top))
    }

    @Test
    fun returnsToTheDockOnceTheCardScrollsAwayAtTheTop() {
        assertEquals(ScanButtonPlacement(dock, landed = false), placeScanButton(top - 1f, dock, top))
    }

    @Test
    fun staysDockedBeforeTheCardHasBeenLaidOut() {
        assertEquals(ScanButtonPlacement(dock, landed = false), placeScanButton(null, dock, top))
    }

    @Test
    fun promptFollowsTheTimeOfDay() {
        assertEquals("Snap your breakfast", scanPromptForHour(7))
        assertEquals("Snap your lunch", scanPromptForHour(13))
        assertEquals("Snap a snack", scanPromptForHour(16))
        assertEquals("Snap your dinner", scanPromptForHour(19))
        assertEquals("Snap a snack", scanPromptForHour(23))
        assertEquals("Snap a snack", scanPromptForHour(2))
    }

    @Test
    fun scansLeftReadsNaturally() {
        assertEquals("3 scans left today", scansLeftLabel(3))
        assertEquals("1 scan left today", scansLeftLabel(1))
        assertEquals("No free scans left today", scansLeftLabel(0))
        assertEquals("Unlimited scans", scansLeftLabel(999)) // Premium
    }
}
