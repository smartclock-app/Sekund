package uk.dnpk.sekund.plugins.google

import io.ktor.client.HttpClient
import io.ktor.client.request.get
import io.ktor.client.request.parameter
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.content.TextContent
import io.ktor.http.encodeURLPathPart
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlin.time.Instant

// The parts of Google Calendar API v3 that Sekund uses.
// Docs: https://developers.google.com/workspace/calendar/api/v3/reference

/** One calendar in the account's calendar list. */
@Serializable
data class GoogleCalendarListEntry(
    val id: String,
    val summary: String = "",
    /** The name the user gave it, if they renamed someone else's calendar. Preferred over [summary]. */
    val summaryOverride: String? = null,
    val backgroundColor: String? = null,
) {
    /** The name as shown in Google Calendar. */
    val displayName: String
        get() = summaryOverride ?: summary
}

@Serializable
private data class CalendarListPage(val items: List<GoogleCalendarListEntry> = emptyList(), val nextPageToken: String? = null)

/** One event. Timed events have [GoogleEventTime.dateTime]; all-day events have [GoogleEventTime.date]. */
@Serializable
data class GoogleEvent(
    val id: String,
    val summary: String? = null,
    val start: GoogleEventTime = GoogleEventTime(),
    val end: GoogleEventTime = GoogleEventTime(),
    /** "cancelled" for deleted occurrences of a repeating event. */
    val status: String? = null,
)

/**
 * @property date "2025-06-20" for all-day events.
 * @property dateTime "2025-06-20T14:00:00+01:00" for timed events.
 */
@Serializable
data class GoogleEventTime(val date: String? = null, val dateTime: String? = null)

@Serializable
private data class EventsPage(val items: List<GoogleEvent> = emptyList())

/**
 * A push notification channel: Google calls [WatchRequest.address] whenever the calendar changes, until
 * [expiration] (epoch milliseconds, as text).
 * Docs: https://developers.google.com/workspace/calendar/api/guides/push
 */
@Serializable
data class GoogleChannel(
    val id: String,
    val resourceId: String = "",
    val expiration: String? = null,
)

/**
 * @property token sent back to us with every notification (`X-Goog-Channel-Token`), so we can tell real
 *   ones from anything else that reaches the URL.
 * @property params `ttl` is the channel's lifetime in seconds; Google caps it (a week for events).
 */
@Serializable
private data class WatchRequest(
    val id: String,
    val type: String,
    val address: String,
    val token: String,
    val params: Map<String, String>,
)

@Serializable
private data class StopRequest(val id: String, val resourceId: String)

/**
 * Talks to Google Calendar. [client] must be signed in as the account (from `context.auth.client(...)`),
 * so this class never sees a token.
 */
class GoogleCalendarApi(private val client: HttpClient) {
    private val json = Json { ignoreUnknownKeys = true }

    /** Every calendar in the account's list (following pages, of which there's rarely more than one). */
    suspend fun calendars(): List<GoogleCalendarListEntry> {
        val all = mutableListOf<GoogleCalendarListEntry>()
        var pageToken: String? = null
        do {
            val response = client.get("$BASE/users/me/calendarList") {
                parameter("maxResults", 250)
                if (pageToken != null) parameter("pageToken", pageToken)
            }
            val page = json.decodeFromString<CalendarListPage>(textOf(response, "the calendar list"))
            all.addAll(page.items)
            pageToken = page.nextPageToken
        } while (pageToken != null)
        return all
    }

    /** The next [max] events of calendar [calendarId] that haven't ended by [from], soonest first. */
    suspend fun upcomingEvents(calendarId: String, from: Instant, max: Int): List<GoogleEvent> {
        val response = client.get("$BASE/calendars/${calendarId.encodeURLPathPart()}/events") {
            parameter("timeMin", from.toString())
            // Expands repeating events into their occurrences, which is what makes orderBy=startTime allowed.
            parameter("singleEvents", true)
            parameter("orderBy", "startTime")
            parameter("maxResults", max)
        }
        return json.decodeFromString<EventsPage>(textOf(response, "calendar '$calendarId'")).items
    }

    /** Asks Google to call [address] whenever calendar [calendarId]'s events change. */
    suspend fun watch(calendarId: String, channelId: String, address: String, token: String): GoogleChannel {
        val request = WatchRequest(channelId, "web_hook", address, token, mapOf("ttl" to WEEK_SECONDS.toString()))
        val response = client.post("$BASE/calendars/${calendarId.encodeURLPathPart()}/events/watch") {
            setBody(TextContent(json.encodeToString(WatchRequest.serializer(), request), ContentType.Application.Json))
        }
        return json.decodeFromString<GoogleChannel>(textOf(response, "watching calendar '$calendarId'"))
    }

    /** Stops a channel from [watch] (best effort: it expires by itself anyway). */
    suspend fun stop(channel: GoogleChannel) {
        val request = StopRequest(channel.id, channel.resourceId)
        client.post("$BASE/channels/stop") {
            setBody(TextContent(json.encodeToString(StopRequest.serializer(), request), ContentType.Application.Json))
        }
    }

    private suspend fun textOf(response: HttpResponse, what: String): String {
        val text = response.bodyAsText()
        if (response.status.value !in 200..299) {
            throw IllegalStateException("Google answered ${response.status.value} for $what: ${text.take(200)}")
        }
        return text
    }

    companion object {
        const val BASE = "https://www.googleapis.com/calendar/v3"
        const val WEEK_SECONDS = 7 * 24 * 60 * 60
    }
}
