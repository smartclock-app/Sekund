package uk.dnpk.sekund.plugins.google

import androidx.compose.ui.graphics.Color
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.TimeZone
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue
import kotlin.time.Instant

class GoogleCalendarTest {
    private val work = GoogleCalendarListEntry(id = "work@example.com", summary = "Work", backgroundColor = "#33B679")
    private val london = TimeZone.of("Europe/London")

    private fun timed(id: String, title: String?, start: String, end: String) =
        GoogleEvent(id = id, summary = title, start = GoogleEventTime(dateTime = start), end = GoogleEventTime(dateTime = end))

    @Test
    fun `timed events are converted into the device's time zone`() {
        val events = listOf(timed("1", "Standup", "2025-06-18T09:00:00Z", "2025-06-18T09:15:00Z"))

        val mapped = GoogleEventMapper.map(work, events, london, showRepeatsOnce = true).single()

        assertEquals(LocalDateTime(2025, 6, 18, 10, 0), mapped.start) // BST is UTC+1
        assertEquals(LocalDateTime(2025, 6, 18, 10, 15), mapped.end)
        assertEquals(false, mapped.allDay)
        assertEquals("work@example.com/1", mapped.id)
        assertEquals(Color(0xFF33B679), mapped.color)
    }

    @Test
    fun `all-day events start and end at midnight`() {
        val holiday = GoogleEvent(id = "h", summary = "Holiday", start = GoogleEventTime(date = "2025-06-20"), end = GoogleEventTime(date = "2025-06-23"))

        val mapped = GoogleEventMapper.map(work, listOf(holiday), london, showRepeatsOnce = true).single()

        assertTrue(mapped.allDay)
        assertEquals(LocalDateTime(2025, 6, 20, 0, 0), mapped.start)
        assertEquals(LocalDateTime(2025, 6, 23, 0, 0), mapped.end)
    }

    @Test
    fun `repeating titles appear once unless turned off, and cancelled or broken events are skipped`() {
        val events = listOf(
            timed("1", "Gym", "2025-06-18T18:00:00Z", "2025-06-18T19:00:00Z"),
            timed("2", "Gym", "2025-06-20T18:00:00Z", "2025-06-20T19:00:00Z"),
            timed("3", "Dentist", "2025-06-19T09:00:00Z", "2025-06-19T10:00:00Z").copy(status = "cancelled"),
            timed("4", "Broken", "not a date", "2025-06-19T10:00:00Z"),
            timed("5", null, "2025-06-21T09:00:00Z", "2025-06-21T10:00:00Z"),
        )

        val once = GoogleEventMapper.map(work, events, london, showRepeatsOnce = true)
        val all = GoogleEventMapper.map(work, events, london, showRepeatsOnce = false)

        assertEquals(listOf("Gym", "No title"), once.map { event -> event.titleLines.single() })
        assertEquals(3, all.size)
    }

    @Test
    fun `the api follows calendar list pages and asks for upcoming single events`() = runTest {
        val requested = mutableListOf<String>()
        val client = HttpClient(
            MockEngine { request ->
                requested.add(request.url.toString())
                val body = when {
                    request.url.encodedPath.endsWith("calendarList") && request.url.parameters["pageToken"] == null ->
                        """{"items":[{"id":"a","summary":"A"}],"nextPageToken":"p2"}"""
                    request.url.encodedPath.endsWith("calendarList") -> """{"items":[{"id":"b","summary":"B","summaryOverride":"Bee"}]}"""
                    else -> """{"items":[{"id":"e1","summary":"Lunch","start":{"dateTime":"2025-06-18T12:00:00+01:00"},"end":{"dateTime":"2025-06-18T13:00:00+01:00"},"extra":1}]}"""
                }
                respond(body, HttpStatusCode.OK, headersOf(HttpHeaders.ContentType, "application/json"))
            },
        )
        val api = GoogleCalendarApi(client)

        val calendars = api.calendars()
        val events = api.upcomingEvents("team#1@group.calendar.google.com", Instant.parse("2025-06-18T09:00:00Z"), max = 20)

        assertEquals(listOf("A", "Bee"), calendars.map { calendar -> calendar.displayName })
        assertEquals("Lunch", events.single().summary)
        val eventsUrl = requested.last()
        assertTrue(eventsUrl.contains("/calendars/team%231@group.calendar.google.com/events"), eventsUrl)
        assertTrue(eventsUrl.contains("singleEvents=true") && eventsUrl.contains("orderBy=startTime") && eventsUrl.contains("maxResults=20"), eventsUrl)
    }

    @Test
    fun `an error answer becomes a readable failure`() = runTest {
        val client = HttpClient(MockEngine { respond("""{"error":"forbidden"}""", HttpStatusCode.Forbidden) })

        val error = assertFailsWith<IllegalStateException> { GoogleCalendarApi(client).calendars() }

        assertTrue(error.message!!.startsWith("Google answered 403 for the calendar list"))
    }
}
