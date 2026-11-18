package uk.dnpk.sekund.plugins.google

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import uk.dnpk.sekund.sdk.context.InstanceContext
import uk.dnpk.sekund.sdk.context.WebhookCall
import uk.dnpk.sekund.sdk.context.get
import uk.dnpk.sekund.sdk.context.now
import uk.dnpk.sekund.sdk.context.put
import kotlin.time.Duration
import kotlin.time.Duration.Companion.days
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.minutes
import kotlin.uuid.Uuid

/**
 * Instant calendar updates. While a Sekund server relays webhooks, this asks Google to call our
 * webhook URL whenever a shown calendar changes ("push notifications"), and refreshes straight away
 * when it does. Polling carries on as before, so nothing breaks without a server or if a
 * notification is lost.
 *
 * Google's channels last at most a week, so they're renewed an hour before they expire.
 *
 * @param calendars the calendars to watch (the same ones the source shows).
 */
class GoogleCalendarPush(
    private val context: InstanceContext<GoogleCalendarSettings>,
    private val calendars: suspend (GoogleCalendarApi) -> List<GoogleCalendarListEntry>,
) {
    /** The channels currently asked for, so the old ones can be stopped when they're renewed. */
    private var channels: List<GoogleChannel> = emptyList()

    /** Starts following the webhook URL and its calls, for the instance's lifetime. */
    fun start() {
        context.scope.launch {
            context.webhooks.calls(HOOK).collect { call -> onCall(call) }
        }
        context.scope.launch {
            // collectLatest: a new URL (or none) stops the previous watching loop.
            context.webhooks.url(HOOK).collectLatest { url ->
                if (url != null) keepWatching(url)
            }
        }
    }

    private suspend fun keepWatching(url: String) {
        while (true) {
            val wait = try {
                watchAll(url)
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                context.log.warn("Couldn't ask Google for change notifications: ${error.message}. Polling meanwhile.")
                30.minutes
            }
            delay(wait)
        }
    }

    /**
     * Asks Google to notify [url] about every shown calendar, stops the previous channels, and returns
     * how long until they need renewing.
     */
    suspend fun watchAll(url: String): Duration {
        val account = context.settings.account ?: return 1.hours // nothing to watch until an account is picked
        val api = GoogleCalendarApi(context.auth.client(account))
        val token = channelToken()

        val started = calendars(api).map { calendar -> api.watch(calendar.id, newChannelId(), url, token) }
        for (old in channels) {
            try {
                api.stop(old)
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                // It expires by itself anyway.
            }
        }
        channels = started
        context.log.info("Google will notify this device about changes to ${started.size} calendar(s)")
        return untilRenewal(started)
    }

    /** A call to our webhook: refresh, unless it's not about a change or not from our channel. */
    suspend fun onCall(call: WebhookCall) {
        if (call.headers["x-goog-channel-token"] != channelToken()) return // not from one of our channels
        if (call.headers["x-goog-resource-state"] == "sync") return // "your channel works", not a change
        context.refresher.refreshNow()
    }

    /** An hour before the first channel expires, but at least 5 minutes from now. */
    private fun untilRenewal(started: List<GoogleChannel>): Duration {
        val now = context.time.now().toEpochMilliseconds()
        val firstExpiry = started.mapNotNull { channel -> channel.expiration?.toLongOrNull() }.minOrNull()
            ?: return 6.days
        val renewIn = (firstExpiry - now).milliseconds - 1.hours
        return if (renewIn < 5.minutes) 5.minutes else renewIn
    }

    /**
     * A random secret that Google sends back with every notification (made once and kept), so a
     * notification can be told apart from anything else that reaches the URL.
     */
    private suspend fun channelToken(): String {
        val saved = context.storage.get<String>(TOKEN_KEY)
        if (saved != null) return saved
        val token = Uuid.random().toHexString()
        context.storage.put(TOKEN_KEY, token)
        return token
    }

    private fun newChannelId(): String = "sekund-" + Uuid.random().toHexString()

    companion object {
        /** The webhook's name (its URL is per instance). */
        const val HOOK = "calendar"
        private const val TOKEN_KEY = "pushToken"
    }
}
