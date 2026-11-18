package uk.dnpk.sekund.plugins.calendar

import uk.dnpk.sekund.sdk.calendar.CalendarEvent
import uk.dnpk.sekund.sdk.context.TimePreferences
import kotlin.test.Test
import kotlin.test.assertEquals

// Ported from the old app's EventItem.test.tsx. "Today" is Wednesday 18 June 2025.
class EventTimeTextTest {
    private val today = WEDNESDAY_10AM.date
    private val clock24 = TimePreferences(use24Hour = true)

    private fun describe(event: CalendarEvent, preferences: TimePreferences = clock24): EventWhen =
        EventTimeText.describe(event, today, preferences)

    @Test
    fun `same-day events show the day and the time range`() {
        val text = describe(event("1", start = at("2025-06-18T14:00"), end = at("2025-06-18T15:30")))

        assertEquals(EventWhen("Today (14:00 - 15:30)"), text)
    }

    @Test
    fun `tomorrow is called Tomorrow`() {
        assertEquals(EventWhen("Tomorrow (09:00 - 10:00)"), describe(event("1", start = at("2025-06-19T09:00"))))
    }

    @Test
    fun `other days show the weekday and ordinal`() {
        assertEquals(EventWhen("Wednesday 25th (09:00 - 10:00)"), describe(event("1", start = at("2025-06-25T09:00"))))
    }

    @Test
    fun `times follow the 12 hour preference`() {
        val text = describe(event("1", start = at("2025-06-18T14:00")), TimePreferences(use24Hour = false))

        assertEquals(EventWhen("Today (2:00 PM - 3:00 PM)"), text)
    }

    @Test
    fun `a one-day all-day event shows just the day`() {
        val text = describe(event("1", start = at("2025-06-20T00:00"), end = at("2025-06-21T00:00")))

        assertEquals(EventWhen("Friday 20th"), text)
    }

    @Test
    fun `an event flagged all-day is treated as whole days even without midnight times`() {
        val text = describe(event("1", start = at("2025-06-20T00:00"), end = at("2025-06-20T23:59"), allDay = true))

        assertEquals(EventWhen("Friday 20th"), text)
    }

    @Test
    fun `a multi-day all-day event shows its first and last day`() {
        val text = describe(event("1", start = at("2025-06-20T00:00"), end = at("2025-06-23T00:00")))

        assertEquals(EventWhen("Friday 20th — Sunday 22nd"), text)
    }

    @Test
    fun `a multi-day all-day event across months adds the month to the last day`() {
        val text = describe(event("1", start = at("2025-06-29T00:00"), end = at("2025-07-02T00:00")))

        assertEquals(EventWhen("Sunday 29th — Tuesday 1st Jul"), text)
    }

    @Test
    fun `an all-day event ending on the last day of the month doesn't add the month (old bug)`() {
        // Ends at midnight on 1 July, so its last day is 30 June. The old app compared with 1 July and said "Jun".
        val text = describe(event("1", start = at("2025-06-28T00:00"), end = at("2025-07-01T00:00")))

        assertEquals(EventWhen("Saturday 28th — Monday 30th"), text)
    }

    @Test
    fun `an event of a day or more shows start and end on two lines`() {
        val text = describe(event("1", start = at("2025-06-18T14:00"), end = at("2025-06-20T10:00")))

        assertEquals(EventWhen("Today (14:00) -", "Friday 20th (10:00)"), text)
    }

    @Test
    fun `an overnight event shorter than a day stays on one line`() {
        val text = describe(event("1", start = at("2025-06-18T22:00"), end = at("2025-06-19T02:00")))

        assertEquals(EventWhen("Today (22:00 - 02:00)"), text)
    }

    @Test
    fun `an event with no length shows only its start time`() {
        val text = describe(event("1", start = at("2025-06-18T09:30"), end = at("2025-06-18T09:30")))

        assertEquals(EventWhen("Today (09:30)"), text)
    }
}
