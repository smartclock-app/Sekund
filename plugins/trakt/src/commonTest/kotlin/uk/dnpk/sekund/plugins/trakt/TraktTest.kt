package uk.dnpk.sekund.plugins.trakt

import androidx.compose.ui.graphics.Color
import io.ktor.http.HttpStatusCode
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toInstant
import uk.dnpk.sekund.sdk.calendar.CalendarEvent
import uk.dnpk.sekund.sdk.context.Loadable
import uk.dnpk.sekund.sdk.context.get
import uk.dnpk.sekund.sdk.context.now
import uk.dnpk.sekund.sdk.context.put
import uk.dnpk.sekund.sdk.settings.AccountRef
import uk.dnpk.sekund.sdk.testing.FakeApi
import uk.dnpk.sekund.sdk.testing.FakeInstanceContext
import uk.dnpk.sekund.sdk.testing.json
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.time.Duration.Companion.hours
import kotlin.time.Instant

class TraktTest {
    private val settings = TraktSettings(
        account = AccountRef("trakt", "acc_1"),
        listId = "tv",
        includeWatchlist = false,
        episodesAsShow = false,
        prefix = "",
        color = Color(0xFFF5511D),
        maxItems = 50,
    )
    private val now = LocalDateTime(2025, 6, 18, 10, 0).toInstant(TimeZone.UTC) // Wednesday 10:00 UTC

    private fun cached(name: String, airDate: String?) = CachedItem("show:$name", name, airDate, now)

    private fun build(items: List<CachedItem>, settings: TraktSettings = this.settings) =
        TraktEventBuilder.build(items, now, TimeZone.UTC, settings)

    // ---- Which list entries are followed --------------------------------------------------------

    @Test
    fun `films and shows are followed once each, episodes only when asked`() {
        val entries = listOf(
            TraktListEntry("show", show = TraktTitle("Severance", TraktIds("severance"))),
            TraktListEntry("movie", movie = TraktTitle("Dune", TraktIds("dune-part-two"))),
            TraktListEntry("episode", show = TraktTitle("Slow Horses", TraktIds("slow-horses"))),
            TraktListEntry("show", show = TraktTitle("Severance", TraktIds("severance"))),
            TraktListEntry("person"),
        )

        assertEquals(listOf("show:severance", "movie:dune-part-two"), TraktItems.from(entries, episodesAsShow = false).map { item -> item.key })
        assertEquals(3, TraktItems.from(entries, episodesAsShow = true).size)
    }

    // ---- Turning items into events (the old app's rules) ----------------------------------------

    @Test
    fun `something airing in the next 24 hours shows its exact time, later ones are all-day`() {
        val events = build(listOf(cached("Tonight", "2025-06-18T21:00:00.000Z"), cached("Saturday", "2025-06-21T01:00:00.000Z")))

        assertEquals(LocalDateTime(2025, 6, 18, 21, 0), events[0].start)
        assertEquals(events[0].start, events[0].end)
        assertEquals(false, events[0].allDay)
        assertEquals(LocalDateTime(2025, 6, 21, 0, 0), events[1].start)
        assertEquals(LocalDateTime(2025, 6, 22, 0, 0), events[1].end)
        assertEquals(true, events[1].allDay)
    }

    @Test
    fun `items on the same day share one event with sorted titles under the prefix`() {
        val items = listOf(cached("Zorro", "2025-06-21T03:00:00.000Z"), cached("Andor", "2025-06-21T20:00:00.000Z"))

        val event = build(items, settings.copy(prefix = "📺")).single()

        assertEquals(listOf("📺", "Andor", "Zorro"), event.titleLines)
        assertEquals(Color(0xFFF5511D), event.color)
    }

    @Test
    fun `earlier days, missing dates and anything past maxItems are left out`() {
        val items = listOf(
            cached("Yesterday", "2025-06-17T21:00:00.000Z"),
            cached("Earlier today", "2025-06-18T01:00:00.000Z"), // still today: kept
            cached("Nothing scheduled", null),
            cached("Friday", "2025-06-20T21:00:00.000Z"),
            cached("Next week", "2025-06-25T21:00:00.000Z"),
        )

        val events = build(items, settings.copy(maxItems = 2))

        assertEquals(listOf(listOf("Earlier today"), listOf("Friday")), events.map { event -> event.titleLines })
    }

    @Test
    fun `film release dates are days in the device's time zone`() {
        val film = CachedItem("movie:dune", "Dune", "2025-07-04", now)

        val event = build(listOf(film)).single()

        assertEquals(LocalDateTime(2025, 7, 4, 0, 0), event.start)
        assertEquals(true, event.allDay)
    }

    // ---- The source: fetching, caching, offline -------------------------------------------------

    private fun traktApi(failList: Boolean = false) = FakeApi { request ->
        when (request.url.encodedPath) {
            "/users/me/lists/tv/items" ->
                if (failList) json("{}", HttpStatusCode.ServiceUnavailable)
                else json("""[{"type":"show","show":{"title":"Andor","ids":{"slug":"andor"}}},{"type":"movie","movie":{"title":"Dune","ids":{"slug":"dune"}}}]""")
            "/shows/andor" -> json("""{"title":"Andor","ids":{"slug":"andor"}}""")
            "/shows/andor/last_episode" -> json("""{"first_aired":"2025-06-11T01:00:00.000Z"}""") // last week: not today
            "/shows/andor/next_episode" -> json("""{"first_aired":"2025-06-18T20:00:00.000Z"}""")
            "/movies/dune" -> json("""{"title":"Dune","released":"2025-07-04"}""")
            else -> null
        }
    }

    private fun titlesOf(state: Loadable<List<CalendarEvent>>): List<List<String>> =
        assertIs<Loadable.Ready<List<CalendarEvent>>>(state).data.map { event -> event.titleLines }

    @Test
    fun `the source reads the list, fetches details and caches them for a day`() = runTest {
        val api = traktApi()
        val context = FakeInstanceContext(settings, backgroundScope, api = api)
        val source = TraktCalendarSource(context)

        context.refresher.runAll()

        assertEquals(listOf(listOf("Andor"), listOf("Dune")), titlesOf(source.events.value))
        assertEquals(1, context.storage.get<List<CachedItem>>("items")!!.count { item -> item.key == "show:andor" })

        // A second refresh re-reads only the list: the details are less than a day old.
        val before = api.requests.size
        context.refresher.runAll()
        assertEquals(listOf("/users/me/lists/tv/items"), api.paths.drop(before))
    }

    @Test
    fun `details older than a day are fetched again`() = runTest {
        val api = traktApi()
        val context = FakeInstanceContext(settings, backgroundScope, api = api)
        val stale = CachedItem("show:andor", "Andor", "2025-06-01T20:00:00.000Z", context.time.now() - 25.hours)
        context.storage.put("items", listOf(stale))
        val source = TraktCalendarSource(context)

        context.refresher.runAll()

        assertEquals(true, "/shows/andor/next_episode" in api.paths)
        assertEquals(listOf(listOf("Andor"), listOf("Dune")), titlesOf(source.events.value))
    }

    @Test
    fun `when Trakt is down the cached items are shown`() = runTest {
        val context = FakeInstanceContext(settings, backgroundScope, api = traktApi(failList = true))
        context.storage.put("items", listOf(CachedItem("show:andor", "Andor", "2025-06-20T20:00:00.000Z", Instant.fromEpochSeconds(0))))
        val source = TraktCalendarSource(context)

        context.refresher.runAll()

        assertEquals(listOf(listOf("Andor")), titlesOf(source.events.value))
        assertEquals(true, context.log.lines.any { line -> line.startsWith("WARN Couldn't reach Trakt") })
    }

    @Test
    fun `without a list or the watchlist the source says what to do`() = runTest {
        val context = FakeInstanceContext(settings.copy(listId = ""), backgroundScope)
        val source = TraktCalendarSource(context)

        context.refresher.runAll()

        assertEquals(Loadable.Failed("Choose a list or turn on the watchlist in this source's settings"), source.events.value)
    }
}
