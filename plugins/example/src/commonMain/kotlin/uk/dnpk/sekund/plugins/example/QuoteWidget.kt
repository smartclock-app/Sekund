package uk.dnpk.sekund.plugins.example

import androidx.compose.foundation.layout.Column
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import io.ktor.client.request.get
import io.ktor.client.statement.bodyAsText
import io.ktor.http.isSuccess
import kotlinx.coroutines.flow.StateFlow
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import uk.dnpk.sekund.sdk.context.InstanceContext
import uk.dnpk.sekund.sdk.context.Loadable
import uk.dnpk.sekund.sdk.context.RefreshPolicy
import uk.dnpk.sekund.sdk.widget.Widget
import uk.dnpk.sekund.sdk.widget.WidgetLocation
import uk.dnpk.sekund.ui.components.ErrorCard
import uk.dnpk.sekund.ui.components.SekundCard
import uk.dnpk.sekund.ui.theme.Sekund
import kotlin.time.Duration.Companion.hours

/** What the quote API returns. Extra fields are ignored. */
@Serializable
data class Quote(val quote: String, val author: String = "")

/**
 * Shows the latest quote. All the work is set up in the constructor: Sekund creates one QuoteWidget per
 * configured instance and throws it away (cancelling `context.scope`) when the instance is removed.
 */
class QuoteWidget(private val context: InstanceContext<QuoteSettings>) : Widget {
    private val json = Json { ignoreUnknownKeys = true }

    /** The quote, refreshed every 6 hours (Sekund adds retries, offline handling and pausing). */
    val quote: StateFlow<Loadable<Quote>> = context.refresher.poll(RefreshPolicy(every = 6.hours)) { fetchQuote() }

    private suspend fun fetchQuote(): Quote {
        val response = context.http.get(context.settings.url)
        if (!response.status.isSuccess()) {
            // The message ends up on the widget's error card, so make it readable.
            throw IllegalStateException("The quote API answered ${response.status.value}")
        }
        return json.decodeFromString<Quote>(response.bodyAsText())
    }

    @Composable
    override fun Content(location: WidgetLocation) {
        when (val state = quote.collectAsState().value) {
            is Loadable.Loading -> {} // Draw nothing until the first quote arrives.
            is Loadable.Failed -> ErrorCard(title = "No quote", message = state.message)
            is Loadable.Ready -> QuoteCard(state.data, context.settings.showAuthor)
        }
    }
}

@Composable
private fun QuoteCard(quote: Quote, showAuthor: Boolean) {
    SekundCard {
        Column {
            Text(text = "“${quote.quote}”", color = Sekund.theme.colors.onCard)
            if (showAuthor && quote.author.isNotBlank()) {
                Text(text = "— ${quote.author}", color = Sekund.theme.colors.onCardMuted)
            }
        }
    }
}
