package uk.dnpk.sekund.plugins.example

import io.ktor.http.HttpStatusCode
import kotlinx.coroutines.test.runTest
import uk.dnpk.sekund.sdk.context.Loadable
import uk.dnpk.sekund.sdk.testing.FakeApi
import uk.dnpk.sekund.sdk.testing.FakeInstanceContext
import uk.dnpk.sekund.sdk.testing.json
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

// The test from docs/plugins/authoring.md: a fake API, a fake context, and a manual refresh.
class QuoteWidgetTest {
    private val settings = QuoteSettings(url = "https://quotes.test/today", showAuthor = true)

    @Test
    fun `loads the quote from the configured address`() = runTest {
        val api = FakeApi { request ->
            if (request.url.toString() == "https://quotes.test/today") {
                json("""{"quote": "Simplicity is prerequisite for reliability.", "author": "Dijkstra", "id": 7}""")
            } else {
                null // Anything else answers 404.
            }
        }
        val context = FakeInstanceContext(settings, backgroundScope, api = api)
        val widget = QuoteWidget(context)

        context.refresher.runAll()

        val state = assertIs<Loadable.Ready<Quote>>(widget.quote.value)
        assertEquals(Quote("Simplicity is prerequisite for reliability.", "Dijkstra"), state.data)
    }

    @Test
    fun `an error answer becomes a readable failure`() = runTest {
        val api = FakeApi { json("{}", status = HttpStatusCode.ServiceUnavailable) }
        val context = FakeInstanceContext(settings, backgroundScope, api = api)
        val widget = QuoteWidget(context)

        context.refresher.runAll()

        val state = assertIs<Loadable.Failed>(widget.quote.value)
        assertEquals("The quote API answered 503", state.message)
    }
}
