package uk.dnpk.sekund.plugins.google

import io.ktor.client.request.HttpRequestData
import io.ktor.http.HttpMethod
import io.ktor.http.content.TextContent
import kotlinx.coroutines.test.runTest
import uk.dnpk.sekund.sdk.context.WebhookCall
import uk.dnpk.sekund.sdk.context.get
import uk.dnpk.sekund.sdk.context.now
import uk.dnpk.sekund.sdk.settings.AccountRef
import uk.dnpk.sekund.sdk.testing.FakeApi
import uk.dnpk.sekund.sdk.testing.FakeInstanceContext
import uk.dnpk.sekund.sdk.testing.FakeTime
import uk.dnpk.sekund.sdk.testing.json
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.hours

class GoogleCalendarPushTest {
    private val settings = GoogleCalendarSettings(
        account = AccountRef(GooglePlugin.PROVIDER, "acc_1"),
        calendars = emptyList(),
        maxEventsPerCalendar = 50,
        showRepeatsOnce = true,
    )
    private val work = GoogleCalendarListEntry(id = "work@example.com", summary = "Work")

    /** Pretends to be Google: every watch request gets a channel that expires at [expiresAtMillis]. */
    private fun google(expiresAtMillis: Long) = FakeApi { request ->
        if (request.method == HttpMethod.Post && request.url.encodedPath.endsWith("/events/watch")) {
            json("""{"id":"ch","resourceId":"res-1","expiration":"$expiresAtMillis"}""")
        } else if (request.url.encodedPath == "/calendar/v3/channels/stop") {
            json("")
        } else {
            null
        }
    }

    private fun bodyOf(request: HttpRequestData) = (request.body as TextContent).text

    @Test
    fun `watching asks Google to call the webhook, and renews an hour before expiry`() = runTest {
        val now = FakeTime().now().toEpochMilliseconds()
        val api = google(expiresAtMillis = now + 48 * 3_600_000L)
        val context = FakeInstanceContext(settings, backgroundScope, api = api)
        val push = GoogleCalendarPush(context) { listOf(work) }

        val renewIn = push.watchAll("https://server/hooks/abc")

        assertEquals(47.hours, renewIn)
        val watch = api.requests.single()
        assertEquals("/calendar/v3/calendars/work@example.com/events/watch", watch.url.encodedPath)
        val body = bodyOf(watch)
        assertTrue(body.contains("\"address\":\"https://server/hooks/abc\""), body)
        assertTrue(body.contains("\"type\":\"web_hook\""), body)
        val token = context.storage.get<String>("pushToken")!!
        assertTrue(body.contains("\"token\":\"$token\""), body)

        // Renewing stops the old channel.
        push.watchAll("https://server/hooks/abc")
        assertTrue(api.paths.contains("/calendar/v3/channels/stop"))
    }

    @Test
    fun `only real change notifications from our channels refresh the calendar`() = runTest {
        val context = FakeInstanceContext(settings, backgroundScope, api = google(0))
        val push = GoogleCalendarPush(context) { listOf(work) }
        push.watchAll("https://server/hooks/abc")
        val token = context.storage.get<String>("pushToken")!!

        push.onCall(WebhookCall(mapOf("x-goog-channel-token" to token, "x-goog-resource-state" to "sync"), ""))
        push.onCall(WebhookCall(mapOf("x-goog-channel-token" to "someone else", "x-goog-resource-state" to "exists"), ""))
        assertEquals(0, context.refresher.refreshNowCalls)

        push.onCall(WebhookCall(mapOf("x-goog-channel-token" to token, "x-goog-resource-state" to "exists"), ""))
        assertEquals(1, context.refresher.refreshNowCalls)
    }
}
