package uk.dnpk.sekund.plugins.trakt

import androidx.compose.ui.graphics.Color
import uk.dnpk.sekund.sdk.settings.AccountRef
import uk.dnpk.sekund.sdk.settings.SettingsSchema
import uk.dnpk.sekund.sdk.settings.SettingsValues

/**
 * One Trakt source's settings. The keys match what the legacy importer writes.
 *
 * @property listId the slug or id of one of the account's lists (from its trakt.tv address). Empty = no list.
 * @property includeWatchlist also show what's on the watchlist.
 * @property episodesAsShow list entries that are single episodes count as their whole show.
 * @property prefix an extra first title line on every event, e.g. "📺". Empty = none.
 * @property maxItems the most dates to show (several shows airing the same day count as one).
 */
data class TraktSettings(
    val account: AccountRef?,
    val listId: String,
    val includeWatchlist: Boolean,
    val episodesAsShow: Boolean,
    val prefix: String,
    val color: Color,
    val maxItems: Int,
)

object TraktSettingsSchema : SettingsSchema<TraktSettings>() {
    private val account = account("account", label = "Trakt account", provider = TraktPlugin.PROVIDER)
    private val listId = text(
        "listId",
        label = "List",
        description = "The list's name as it appears in its trakt.tv address. Leave empty to only use the watchlist.",
    )
    private val includeWatchlist = toggle("includeWatchlist", label = "Include watchlist", default = false)
    private val episodesAsShow = toggle(
        "episodesAsShow",
        label = "Treat episodes as their show",
        description = "Single episodes on the list count as the whole show.",
        default = false,
    )
    private val prefix = text("prefix", label = "First line", description = "Shown above the titles, e.g. 📺 or TV.")
    private val color = color("color", label = "Colour", default = Color(0xFFF5511D))
    private val maxItems = number("maxItems", label = "Most dates shown", default = 50, min = 1, max = 200)

    override fun build(values: SettingsValues) = TraktSettings(
        account = values[account],
        listId = values[listId].trim(),
        includeWatchlist = values[includeWatchlist],
        episodesAsShow = values[episodesAsShow],
        prefix = values[prefix].trim(),
        color = values[color],
        maxItems = values[maxItems],
    )
}
