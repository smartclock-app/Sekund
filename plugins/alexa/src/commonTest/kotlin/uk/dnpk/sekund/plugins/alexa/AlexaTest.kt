package uk.dnpk.sekund.plugins.alexa

import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.request.HttpRequestBuilder
import io.ktor.client.request.forms.FormDataContent
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toInstant
import uk.dnpk.sekund.sdk.auth.CustomAuthRejectedException
import uk.dnpk.sekund.sdk.auth.CustomSession
import uk.dnpk.sekund.sdk.context.Loadable
import uk.dnpk.sekund.sdk.settings.AccountRef
import uk.dnpk.sekund.sdk.testing.FakeApi
import uk.dnpk.sekund.sdk.testing.FakeInstanceContext
import uk.dnpk.sekund.sdk.testing.FakeTime
import uk.dnpk.sekund.sdk.testing.json
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds

class AlexaTest {
    private val now = LocalDateTime(2025, 6, 18, 10, 0).toInstant(TimeZone.UTC)
    private val settings = AlexaSettings(
        account = AccountRef("amazon", "acc_1"),
        showNowPlaying = true,
        showLyrics = true,
        showTimers = true,
        showAlarms = true,
        devices = emptyList(),
        radioProviders = emptyList(),
    )
    private val kitchen = AlexaDevice("Kitchen Echo", "A1", "SER1", "ECHO")
    private val lounge = AlexaDevice("Lounge Echo", "A2", "SER2", "ECHO")

    // ---- Lyrics --------------------------------------------------------------------------------

    @Test
    fun lyricsAreParsedAndFollowTheSong() {
        val lyrics = Lyrics.parse(
            """
            [ar:Someone]
            [00:05.00] First
            [00:10.50][01:00.00] Chorus
            [00:20.00]
            [00:30.00] Third
            """.trimIndent(),
        )!!

        assertEquals(listOf(5_000L, 10_500L, 30_000L, 60_000L), lyrics.lines.map { line -> line.startMs })
        assertEquals(LyricsWindow(previous = null, current = "First", next = "Chorus"), lyrics.windowAt(1_000))
        assertEquals(LyricsWindow(previous = "First", current = "Chorus", next = "Third"), lyrics.windowAt(12_000))
        assertNull(Lyrics.parse("Just plain words, no timings"))
    }

    // ---- What's playing ------------------------------------------------------------------------

    private fun session(device: AlexaDevice, state: String, title: String, provider: String = "Spotify", length: Long = 200_000) = MediaSession(
        endpointList = listOf(MediaEndpoint(MediaEndpointId(device.serialNumber, device.deviceType))),
        nowPlayingData = NowPlayingData(
            playerState = state,
            infoText = InfoText(title = title, subText1 = "Artist"),
            progress = MediaProgress(mediaLength = length, mediaProgress = 30_000),
            provider = MediaProvider(provider),
        ),
    )

    @Test
    fun theFirstSongPlayingOnAFollowedDeviceIsPicked() {
        val sessions = listOf(session(kitchen, "PAUSED", "Paused song"), session(kitchen, "PLAYING", "Kitchen song"), session(lounge, "PLAYING", "Lounge song"))

        assertEquals("Kitchen song", NowPlayingPicker.pick(sessions, emptyList(), emptyList(), now)?.title)
        assertEquals("Lounge song", NowPlayingPicker.pick(sessions, listOf(lounge), emptyList(), now)?.title)
    }

    @Test
    fun radioHasNoProgressAndSongsMoveOnBetweenFetches() {
        val radio = NowPlayingPicker.pick(listOf(session(kitchen, "PLAYING", "Station", provider = "TuneIn")), emptyList(), emptyList(), now)!!
        val custom = NowPlayingPicker.pick(listOf(session(kitchen, "PLAYING", "Station", provider = "My Radio")), emptyList(), listOf("My Radio"), now)!!
        val song = NowPlayingPicker.pick(listOf(session(kitchen, "PLAYING", "Song")), emptyList(), emptyList(), now)!!

        assertEquals(true, radio.isRadio)
        assertEquals(true, custom.isRadio)
        assertEquals(35_000, song.progressAt(now + 5.seconds))
        assertEquals(200_000, song.progressAt(now + 10.minutes)) // never past the end
    }

    // ---- Timers and alarms ---------------------------------------------------------------------

    @Test
    fun activeTimersAndAlarmsDueWithin12HoursAreShown() {
        val notifications = listOf(
            AlexaNotification(id = "t1", type = "Timer", status = "ON", deviceSerialNumber = "SER1", triggerTime = (now + 5.minutes).toEpochMilliseconds(), timerLabel = "Pasta"),
            AlexaNotification(id = "t2", type = "Timer", status = "OFF", deviceSerialNumber = "SER1", remainingTime = 1000),
            AlexaNotification(id = "a1", type = "Alarm", status = "ON", deviceSerialNumber = "SER2", originalDate = "2025-06-18", originalTime = "18:30:00.000"),
            AlexaNotification(id = "a2", type = "Alarm", status = "ON", deviceSerialNumber = "SER1", originalDate = "2025-06-19", originalTime = "07:00:00.000"),
            AlexaNotification(id = "r1", type = "Reminder", status = "ON", deviceSerialNumber = "SER1", originalDate = "2025-06-18", originalTime = "09:00:00.000", snoozedToTime = "10:15:00.000", reminderLabel = "Bins"),
        )

        val all = RemindersPicker.pick(notifications, emptySet(), settings, now, TimeZone.UTC)
        val kitchenOnly = RemindersPicker.pick(notifications, setOf("SER1"), settings, now, TimeZone.UTC)

        assertEquals(listOf(AlexaTimer("t1", "Pasta", now + 5.minutes)), all.timers)
        assertEquals(listOf("Bins", "Alarm"), all.alarms.map { alarm -> alarm.label }) // 10:15 snoozed, then 18:30; tomorrow 07:00 is > 12h
        assertEquals(listOf("Bins"), kitchenOnly.alarms.map { alarm -> alarm.label })
    }

    // ---- Amazon sign-in ------------------------------------------------------------------------

    @Test
    fun theRefreshTokenIsSwappedForCookiesAndACsrfToken() = runTest {
        var sentForm = ""
        val http = HttpClient(
            MockEngine { request ->
                when (request.url.toString()) {
                    "https://api.amazon.de/ap/exchangetoken/cookies" -> {
                        sentForm = (request.body as FormDataContent).formData.entries().joinToString { (key, values) -> "$key=${values.single()}" }
                        respond(
                            """{"response":{"tokens":{"cookies":{".amazon.de":[{"Name":"session-id","Value":"\"abc\""},{"Name":"at-acb","Value":"xyz"}]}}}}""",
                            HttpStatusCode.OK, headersOf(HttpHeaders.ContentType, "application/json"),
                        )
                    }
                    "https://alexa.amazon.de/api/language" -> respond("{}", HttpStatusCode.OK, headersOf(HttpHeaders.SetCookie, "csrf=12345; Path=/"))
                    else -> respond("", HttpStatusCode.NotFound)
                }
            },
        )

        val session = AmazonCookieHandler.refresh(http, mapOf("domain" to "www.amazon.de"), CustomSession(secret = "Atnr|token"))
        val request = HttpRequestBuilder()
        AmazonCookieHandler.sign(request, mapOf("domain" to "amazon.de"), session)

        assertEquals(true, sentForm.contains("source_token=Atnr|token") && sentForm.contains("domain=www.amazon.de"), sentForm)
        assertEquals("session-id=abc; at-acb=xyz; csrf=12345", request.headers[HttpHeaders.Cookie])
        assertEquals("12345", request.headers["csrf"])
    }

    @Test
    fun aRefusedTokenOrMissingSiteNeedsTheUser() = runTest {
        val http = HttpClient(MockEngine { respond("", HttpStatusCode.Unauthorized) })

        assertFailsWith<CustomAuthRejectedException> { AmazonCookieHandler.refresh(http, mapOf("domain" to "amazon.co.uk"), CustomSession("old")) }
        assertFailsWith<CustomAuthRejectedException> { AmazonCookieHandler.refresh(http, emptyMap(), CustomSession("token")) }
    }

    // ---- Loading -------------------------------------------------------------------------------

    private fun alexaApi(playing: Boolean) = FakeApi { request ->
        when (request.url.encodedPath) {
            "/api/devices-v2/device" -> json("""{"devices":[{"accountName":"Kitchen Echo","deviceType":"A1","serialNumber":"SER1"}]}""")
            "/api/users/me" -> json("""{"id":"CUST"}""")
            "/api/np/list-media-sessions" -> json(
                if (!playing) """{"mediaSessionList":[]}"""
                else """{"mediaSessionList":[{"endpointList":[{"id":{"deviceSerialNumber":"SER1","deviceType":"A1"}}],
                    "nowPlayingData":{"playerState":"PLAYING","infoText":{"title":"Song","subText1":"Band"},
                    "progress":{"mediaLength":200000,"mediaProgress":1000},"provider":{"providerName":"Spotify"}}}]}""",
            )
            "/api/notifications" -> json("""{"notifications":[]}""")
            "/api/search" -> json("""[{"syncedLyrics":"[00:01.00] Hello"}]""")
            else -> null
        }
    }

    @Test
    fun whileNothingPlaysItOnlyChecksEveryTwoMinutes() = runTest {
        val api = alexaApi(playing = false)
        val time = FakeTime()
        val context = FakeInstanceContext(settings.copy(showLyrics = false), backgroundScope, api = api, time = time)
        context.auth.accountFields["domain"] = "amazon.co.uk"
        val data = AlexaData(context)

        context.refresher.runAll()
        context.refresher.runAll() // 0 s later: skipped
        time.set(LocalDateTime(2025, 6, 18, 10, 2, 1))
        context.refresher.runAll() // 2 minutes later: checks again

        assertEquals(2, api.paths.count { path -> path == "/api/np/list-media-sessions" })
        assertEquals(Loadable.Ready(null, time.everySecond.value.toInstant(TimeZone.UTC)), data.nowPlaying.value)
    }

    @Test
    fun lyricsAreLookedUpOncePerSong() = runTest {
        val api = alexaApi(playing = true)
        val context = FakeInstanceContext(settings, backgroundScope, api = api)
        context.auth.accountFields["domain"] = "amazon.co.uk"
        val data = AlexaData(context)

        context.refresher.runAll()
        testScheduler.runCurrent()
        context.refresher.runAll()
        testScheduler.runCurrent()

        assertEquals("Song", assertIs<Loadable.Ready<NowPlaying?>>(data.nowPlaying.value).data?.title)
        assertEquals("Hello", data.lyrics.value?.windowAt(5_000)?.current)
        assertEquals(1, api.paths.count { path -> path == "/api/search" })
        assertEquals(1, context.files.files.size) // the lyrics were saved
    }
}
