package uk.dnpk.sekund.sdk.time

import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import uk.dnpk.sekund.sdk.context.TimePreferences
import kotlin.test.Test
import kotlin.test.assertEquals

class TimeFormatTest {
    @Test
    fun `ordinal suffixes`() {
        val expected = mapOf(1 to "st", 2 to "nd", 3 to "rd", 4 to "th", 11 to "th", 12 to "th", 13 to "th", 21 to "st", 22 to "nd", 23 to "rd", 31 to "st")
        for ((day, suffix) in expected) {
            assertEquals(suffix, TimeFormat.ordinalSuffix(day), "day $day")
        }
    }

    @Test
    fun `12 hour clock`() {
        val prefs = TimePreferences(use24Hour = false, showSeconds = true)

        assertEquals(ClockText("12:05", "09", "AM"), TimeFormat.clock(LocalDateTime(2026, 1, 1, 0, 5, 9), prefs))
        assertEquals(ClockText("12:00", "00", "PM"), TimeFormat.clock(LocalDateTime(2026, 1, 1, 12, 0, 0), prefs))
        assertEquals(ClockText("09:41", "00", "PM"), TimeFormat.clock(LocalDateTime(2026, 1, 1, 21, 41, 0), prefs))
    }

    @Test
    fun `24 hour clock without seconds`() {
        val prefs = TimePreferences(use24Hour = true, showSeconds = false)

        assertEquals(ClockText("21:41", null, null), TimeFormat.clock(LocalDateTime(2026, 1, 1, 21, 41, 30), prefs))
    }

    @Test
    fun `short times`() {
        val time = LocalDateTime(2026, 1, 1, 21, 5)

        assertEquals("9:05 PM", TimeFormat.shortTime(time, TimePreferences(use24Hour = false)))
        assertEquals("21:05", TimeFormat.shortTime(time, TimePreferences(use24Hour = true)))
    }

    @Test
    fun `long date`() {
        assertEquals(
            LongDateText("Monday", "3", "rd", "March", "2025"),
            TimeFormat.longDate(LocalDate(2025, 3, 3)),
        )
    }
}
