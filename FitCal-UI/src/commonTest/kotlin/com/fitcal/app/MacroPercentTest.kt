package com.fitcal.app

import com.fitcal.app.ui.components.macroPercentOfGoal
import kotlin.test.Test
import kotlin.test.assertEquals

/** The review screen's macro percentages come from the user's own goals, not fixed targets. */
class MacroPercentTest {

    @Test
    fun percentIsRelativeToTheUsersGoal() {
        assertEquals(20, macroPercentOfGoal(30f, 150))
        assertEquals(60, macroPercentOfGoal(30f, 50))
    }

    @Test
    fun sameMealGivesDifferentPercentForDifferentGoals() {
        val protein = 45f
        assertEquals(30, macroPercentOfGoal(protein, 150))
        assertEquals(50, macroPercentOfGoal(protein, 90))
    }

    @Test
    fun missingGoalGivesZeroInsteadOfDividingByZero() {
        assertEquals(0, macroPercentOfGoal(30f, 0))
        assertEquals(0, macroPercentOfGoal(30f, -10))
    }

    @Test
    fun overGoalIsReportedAboveHundred() {
        assertEquals(150, macroPercentOfGoal(97.5f, 65))
    }
}
