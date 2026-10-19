package com.fitcal.app

import com.fitcal.app.ui.screens.dashboard.components.calendarWindow
import com.fitcal.app.ui.screens.dashboard.components.calendarWindowTitle
import com.fitcal.app.ui.screens.dashboard.components.windowEndFor
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** Dashboard week strip: today visible on open, paging a week at a time. */
class CalendarStripTest {

    private val today = "2026-10-13"   // a Tuesday

    @Test
    fun windowEndingTodayShowsTodayLastWithWeekdays() {
        val days = calendarWindow(today, today)
        assertEquals(7, days.size)
        assertEquals("2026-10-07", days.first().key)
        assertEquals(today, days.last().key)
        assertTrue(days.last().isToday)
        assertEquals(listOf("Wed", "Thu", "Fri", "Sat", "Sun", "Yest", "Today"), days.map { it.label })
        assertEquals("13", days.last().dayNumber)
        assertEquals("7", days.first().dayNumber)
    }

    @Test
    fun earlierWindowsUseWeekdayNames() {
        val days = calendarWindow("2026-10-06", today)
        assertEquals(listOf("Wed", "Thu", "Fri", "Sat", "Sun", "Mon", "Tue"), days.map { it.label })
        assertTrue(days.none { it.isToday })
    }

    @Test
    fun windowCrossesMonthAndYearBoundaries() {
        val days = calendarWindow("2027-01-02", "2027-01-02")
        assertEquals("2026-12-27", days.first().key)
        assertEquals(listOf("Sun", "Mon", "Tue", "Wed", "Thu", "Yest", "Today"), days.map { it.label })
    }

    @Test
    fun titles() {
        assertEquals("Last 7 days", calendarWindowTitle(today, today))
        assertEquals("30 Sep – 6 Oct", calendarWindowTitle("2026-10-06", today))
        assertEquals("27 Dec 2025 – 2 Jan 2026", calendarWindowTitle("2026-01-02", today))
    }

    @Test
    fun selectedDayDecidesWhichPageIsShown() {
        assertEquals(today, windowEndFor(today, today))
        assertEquals(today, windowEndFor("2026-10-07", today))        // 6 days back: same page
        assertEquals("2026-10-06", windowEndFor("2026-10-06", today)) // 7 days back: previous page
        assertEquals("2026-09-29", windowEndFor("2026-09-25", today))
        assertEquals(today, windowEndFor("2026-10-20", today))        // future: stay on today
    }
}
