package com.fitcal.app.ui.screens.dashboard.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowLeft
import androidx.compose.material.icons.filled.KeyboardArrowRight
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.fitcal.app.plan.addDays
import com.fitcal.app.plan.dateFromEpochDay
import com.fitcal.app.plan.daysBetween
import com.fitcal.app.plan.epochDay
import com.fitcal.app.plan.formatPlanDate
import com.fitcal.app.ui.components.PressableBox
import com.fitcal.app.ui.theme.*

private const val DAYS_SHOWN = 7
private val WEEKDAYS = listOf("Mon", "Tue", "Wed", "Thu", "Fri", "Sat", "Sun")
private val MONTHS = listOf("Jan", "Feb", "Mar", "Apr", "May", "Jun", "Jul", "Aug", "Sep", "Oct", "Nov", "Dec")

data class CalendarDay(val key: String, val label: String, val dayNumber: String, val isToday: Boolean)

/** The 7 days ending on [endKey] (oldest first). Today is labelled "Today", the day before "Yest". */
fun calendarWindow(endKey: String, today: String): List<CalendarDay> {
    val end = epochDay(endKey) ?: epochDay(today) ?: return emptyList()
    val yesterday = addDays(today, -1)
    return (DAYS_SHOWN - 1 downTo 0).map { back ->
        val day = end - back
        val key = dateFromEpochDay(day)
        val weekday = WEEKDAYS[(((day + 3) % 7 + 7) % 7).toInt()]   // 1970-01-01 was a Thursday
        CalendarDay(
            key = key,
            label = when (key) {
                today -> "Today"
                yesterday -> "Yest"
                else -> weekday
            },
            dayNumber = key.substring(8, 10).trimStart('0'),
            isToday = key == today,
        )
    }
}

/** "Last 7 days" for the window ending today, otherwise its range, e.g. "29 Sep – 5 Oct". */
fun calendarWindowTitle(endKey: String, today: String): String {
    if (endKey == today) return "Last 7 days"
    val start = addDays(endKey, -(DAYS_SHOWN - 1)) ?: return formatPlanDate(endKey)
    val sameYear = start.substring(0, 4) == endKey.substring(0, 4) && endKey.substring(0, 4) == today.substring(0, 4)
    fun short(key: String) = "${key.substring(8, 10).trimStart('0')} ${MONTHS[key.substring(5, 7).toInt() - 1]}"
    return if (sameYear) "${short(start)} – ${short(endKey)}" else "${formatPlanDate(start)} – ${formatPlanDate(endKey)}"
}

/** End of the window that shows [selected]: today's window, or whole 7-day pages back from it. */
fun windowEndFor(selected: String, today: String): String {
    val back = daysBetween(selected, today) ?: return today
    if (back <= 0) return today
    val pages = back / DAYS_SHOWN
    return addDays(today, -(pages * DAYS_SHOWN).toInt()) ?: today
}

/**
 * Fits the screen width with no scrolling, so today is always visible (rightmost) on open.
 * Arrows page back and forth a week at a time (never into the future); "Today" jumps back.
 */
@Composable
fun CalendarStrip(
    selectedDate: String,
    today: String,
    onDateSelected: (String) -> Unit
) {
    var windowEnd by remember(today) { mutableStateOf(windowEndFor(selectedDate, today)) }
    // Keep the selected day on screen if it's changed from elsewhere.
    LaunchedEffect(selectedDate, today) {
        val inWindow = (daysBetween(selectedDate, windowEnd) ?: -1) in 0 until DAYS_SHOWN
        if (!inWindow) windowEnd = windowEndFor(selectedDate, today)
    }
    val days = remember(windowEnd, today) { calendarWindow(windowEnd, today) }
    val atToday = windowEnd == today

    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        // Fixed height: the "Today" TextButton (48dp touch target) appears only away from today,
        // and without this the row grew from 44 to 48dp, nudging everything below it.
        Row(
            modifier = Modifier.fillMaxWidth().height(48.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(
                onClick = { addDays(windowEnd, -DAYS_SHOWN)?.let { windowEnd = it } },
                modifier = Modifier.size(44.dp)
            ) {
                Icon(Icons.Default.KeyboardArrowLeft, contentDescription = "Previous 7 days", tint = TextColor)
            }
            Text(
                text = calendarWindowTitle(windowEnd, today),
                style = BrandTypography.BodySmall,
                fontWeight = FontWeight.Bold,
                color = TextColor,
                modifier = Modifier.weight(1f)
            )
            // Whenever today isn't selected or isn't on screen, offer a way straight back.
            if (selectedDate != today || !atToday) {
                TextButton(onClick = {
                    windowEnd = today
                    onDateSelected(today)
                }) {
                    Text("Today", color = PrimaryAccent, fontWeight = FontWeight.Bold)
                }
            }
            IconButton(
                onClick = {
                    val next = addDays(windowEnd, DAYS_SHOWN) ?: today
                    windowEnd = if ((daysBetween(next, today) ?: 0) < 0) today else next
                },
                enabled = !atToday,
                modifier = Modifier.size(44.dp)
            ) {
                Icon(
                    Icons.Default.KeyboardArrowRight,
                    contentDescription = "Next 7 days",
                    tint = if (atToday) BorderColor else TextColor
                )
            }
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            days.forEach { day ->
                val isSelected = day.key == selectedDate
                // F3.1: PressableBox replaces .clickable on calendar day boxes
                PressableBox(
                    onTap = { onDateSelected(day.key) },
                    modifier = Modifier
                        .weight(1f)
                        // F0.3: height at least 44dp for touch target compliance
                        .heightIn(min = 44.dp)
                        .clip(RoundedCornerShape(RadiusM))
                        .background(if (isSelected) PrimaryAccent else Color.White)
                        .border(
                            width = if (day.isToday && !isSelected) 1.5.dp else 1.dp,
                            color = if (isSelected || day.isToday) PrimaryAccent else BorderColor,
                            shape = RoundedCornerShape(RadiusM)
                        )
                        .padding(vertical = 10.dp)
                        .semantics {
                            selected = isSelected
                            contentDescription = if (day.isToday) "Today, ${formatPlanDate(day.key)}" else formatPlanDate(day.key)
                        }
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(4.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = day.label,
                            style = BrandTypography.Eyebrow,
                            maxLines = 1,
                            softWrap = false,
                            color = when {
                                isSelected -> Color.White
                                day.isToday -> PrimaryAccent
                                else -> MutedTextColor
                            }
                        )
                        Text(
                            text = day.dayNumber,
                            style = BrandTypography.KpiNumeral,
                            color = if (isSelected) Color.White else TextColor
                        )
                    }
                }
            }
        }
    }
}
