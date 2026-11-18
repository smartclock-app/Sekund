package uk.dnpk.sekund.plugins.calendar

import kotlinx.datetime.DatePeriod
import kotlinx.datetime.LocalDate
import kotlinx.datetime.daysUntil
import kotlinx.datetime.isoDayNumber
import kotlinx.datetime.minus

/**
 * Week maths for the calendar: which Monday a week starts on, and whether a week is "odd" or "even".
 *
 * Odd/even is counted in whole weeks from a fixed Monday, [REFERENCE_MONDAY] (1 January 2024), which is
 * week 0 and so even. Counting from a fixed date (rather than using ISO week numbers) keeps a strict
 * every-other-week rhythm across new year, which is what fortnightly schedules like bin collections need.
 */
object WeekParity {
    /** Week 0, an even week. Must be a Monday. */
    val REFERENCE_MONDAY = LocalDate(2024, 1, 1)

    /** The Monday of the week [date] is in (weeks run Monday to Sunday). */
    fun mondayOf(date: LocalDate): LocalDate {
        val daysSinceMonday = date.dayOfWeek.isoDayNumber - 1 // Monday = 1 ... Sunday = 7
        return date.minus(DatePeriod(days = daysSinceMonday))
    }

    /** True if the week containing [date] is an even week. */
    fun isEven(date: LocalDate): Boolean {
        val weeks = REFERENCE_MONDAY.daysUntil(mondayOf(date)) / 7
        // mod (not %) so weeks before the reference date work too: (-1).mod(2) == 1, whereas -1 % 2 == -1.
        return weeks.mod(2) == 0
    }
}
