package uk.dnpk.sekund.plugins.calendar

import kotlinx.datetime.LocalDateTime
import uk.dnpk.sekund.sdk.calendar.CalendarEvent
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

// Ported from the old app's fetchEvents.test.ts, plus tests for the bugs fixed in the port.
class EventGroupingTest {

    private fun group(
        events: List<CalendarEvent>,
        now: LocalDateTime = WEDNESDAY_10AM,
        settings: CalendarSettings = settings(),
        filters: List<String> = emptyList(),
    ): List<EventGroup> = EventGrouping.group(events, now, settings, filters.map { text -> TitleFilter(text) })

    private fun titles(groups: List<EventGroup>): List<String> = groups.map { group -> group.title }

    private fun ids(groups: List<EventGroup>): List<String> = groups.flatMap { group -> group.events.map { event -> event.id } }

    @Test
    fun `no events gives no groups`() {
        assertEquals(emptyList(), group(emptyList()))
    }

    @Test
    fun `events this week are under This Week`() {
        val groups = group(listOf(event("1", start = at("2025-06-19T09:00"))))

        assertEquals(listOf("This Week"), titles(groups))
    }

    @Test
    fun `events already under way are under This Week, even if they started last week`() {
        val holiday = event("1", start = at("2025-06-10T00:00"), end = at("2025-06-25T00:00"), allDay = true)

        assertEquals(listOf("This Week"), titles(group(listOf(holiday))))
    }

    @Test
    fun `finished events are left out`() {
        val earlierToday = event("1", start = at("2025-06-18T08:00"))

        assertEquals(emptyList(), group(listOf(earlierToday)))
    }

    @Test
    fun `an event with no length stays until the end of its day`() {
        val airedThisMorning = event("1", start = at("2025-06-18T09:00"), end = at("2025-06-18T09:00"))
        val airedYesterday = event("2", start = at("2025-06-17T21:00"), end = at("2025-06-17T21:00"))

        assertEquals(listOf("1"), ids(group(listOf(airedThisMorning, airedYesterday))))
    }

    @Test
    fun `events next week are under Next Week`() {
        val groups = group(listOf(event("1", start = at("2025-06-24T09:00"))))

        assertEquals(listOf("Next Week"), titles(groups))
    }

    @Test
    fun `later events are under their month and year`() {
        val groups = group(listOf(event("1", start = at("2025-07-09T09:00")), event("2", start = at("2026-01-05T09:00"))))

        assertEquals(listOf("July 2025", "January 2026"), titles(groups))
    }

    @Test
    fun `next week is recognised across new year (old bug)`() {
        // Wednesday 24 Dec 2025 is ISO week 52; Tuesday 30 Dec is ISO week 1 of 2026. The old app compared
        // week numbers, so this landed under "December 2025" instead of "Next Week".
        val groups = group(listOf(event("1", start = at("2025-12-30T09:00"))), now = at("2025-12-24T10:00"))

        assertEquals(listOf("Next Week"), titles(groups))
    }

    @Test
    fun `next week is recognised when this week starts in December`() {
        // Thursday 1 Jan 2026 is in the week of Monday 29 Dec 2025.
        val groups = group(
            listOf(event("1", start = at("2026-01-02T09:00")), event("2", start = at("2026-01-06T09:00"))),
            now = at("2026-01-01T10:00"),
        )

        assertEquals(listOf("This Week", "Next Week"), titles(groups))
    }

    @Test
    fun `sorted by start time`() {
        val later = event("2", start = at("2025-06-18T12:00"))
        val earlier = event("1", start = at("2025-06-18T11:00"))

        assertEquals(listOf("1", "2"), ids(group(listOf(later, earlier))))
    }

    @Test
    fun `events starting together are sorted by title, ignoring case`() {
        val start = at("2025-06-18T11:00")
        val events = listOf(event("2", start = start, title = "Zebra"), event("1", start = start, title = "apple"))

        assertEquals(listOf("1", "2"), ids(group(events)))
    }

    @Test
    fun `multi-line titles are sorted by their joined text`() {
        val start = at("2025-06-18T11:00")
        val events = listOf(event("2", start = start, title = "Zebra"), event("1", start = start, titleLines = listOf("Apple", "Pie")))

        assertEquals(listOf("1", "2"), ids(group(events)))
    }

    @Test
    fun `only maxEvents events are shown`() {
        // Ten events today, at 10:00, 11:00 ... 19:00.
        val events = (0 until 10).map { index -> event("$index", start = at("2025-06-18T1$index:00")) }

        assertEquals(3, ids(group(events, settings = settings(maxEvents = 3))).size)
    }

    @Test
    fun `week headings get the odd or even label of their own week (old bug)`() {
        // Week of Mon 16 June 2025 is 76 weeks after Mon 1 Jan 2024: even. The next week is odd.
        // The old app never showed these labels, and worked parity out per event (wrong within a week).
        val events = listOf(event("1", start = at("2025-06-20T09:00")), event("2", start = at("2025-06-23T09:00")))

        val groups = group(events, settings = settings(oddWeekTitle = "Recycling", evenWeekTitle = "Bins"))

        assertEquals(listOf("This Week - Bins", "Next Week - Recycling"), titles(groups))
    }

    @Test
    fun `week parity counts from 1 January 2024 in both directions`() {
        assertTrue(WeekParity.isEven(at("2024-01-01T00:00").date))
        assertTrue(WeekParity.isEven(at("2024-01-07T23:00").date)) // Sunday of the same week
        assertEquals(false, WeekParity.isEven(at("2024-01-08T00:00").date))
        assertEquals(false, WeekParity.isEven(at("2023-12-31T00:00").date)) // Sunday of the week before
        assertEquals(false, WeekParity.isEven(at("2023-12-25T00:00").date)) // Monday of the week before
        assertTrue(WeekParity.isEven(at("2023-12-18T00:00").date)) // two weeks before
        assertTrue(WeekParity.isEven(at("2023-12-24T00:00").date))
    }

    @Test
    fun `filters hide events by regular expression or exact title`() {
        val start = at("2025-06-18T11:00")
        val events = listOf(
            event("1", start = start, title = "Private: dentist"),
            // Read as regexes, "C++ meetup" and "Lunch (Bob)" don't match their own text; the exact check catches them.
            event("2", start = start, title = "C++ meetup"),
            event("3", start = start, title = "Lunch (Bob)"),
            event("4", start = start, title = "Football"),
            event("5", start = start, titleLines = listOf("The Show", "S01E02 Holiday special")),
        )

        val groups = group(events, filters = listOf("^Private", "C++ meetup", "Lunch (Bob)", "[unclosed", "Holiday"))

        assertEquals(listOf("4"), ids(groups))
    }

    @Test
    fun `an invalid regular expression still matches exactly`() {
        val filter = TitleFilter("[Work")

        assertTrue(filter.isPlainTextOnly)
        assertTrue(filter.hides(event("1", start = WEDNESDAY_10AM, title = "[Work")))
        assertEquals(false, filter.hides(event("2", start = WEDNESDAY_10AM, title = "[Work] standup")))
    }
}
