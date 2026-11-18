package uk.dnpk.sekund.plugins.example

import uk.dnpk.sekund.sdk.settings.SettingsSchema
import uk.dnpk.sekund.sdk.settings.SettingsValues

/**
 * One quote widget's settings.
 *
 * @property url an address that returns `{"quote": "...", "author": "..."}`.
 * @property showAuthor whether to show who said it.
 */
data class QuoteSettings(
    val url: String,
    val showAuthor: Boolean,
)

/** Declares the settings once; Sekund generates the settings screen and reads config.json from it. */
object QuoteSettingsSchema : SettingsSchema<QuoteSettings>() {
    private val url = text(
        "url",
        label = "Quote API",
        description = "Returns JSON like {\"quote\": \"...\", \"author\": \"...\"}.",
        default = "https://quotes.example.com/today",
        required = true,
    )
    private val showAuthor = toggle("showAuthor", label = "Show the author", default = true)

    override fun build(values: SettingsValues) = QuoteSettings(
        url = values[url].trim(),
        showAuthor = values[showAuthor],
    )
}
