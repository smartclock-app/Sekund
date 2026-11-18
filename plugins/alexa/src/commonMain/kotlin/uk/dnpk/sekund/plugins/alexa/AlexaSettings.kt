package uk.dnpk.sekund.plugins.alexa

import uk.dnpk.sekund.sdk.settings.AccountRef
import uk.dnpk.sekund.sdk.settings.SettingsSchema
import uk.dnpk.sekund.sdk.settings.SettingsValues

/**
 * The Alexa widget's settings. Keys match what the legacy importer writes.
 *
 * @property devices names of the Echo devices to follow, as shown in the Alexa app. Empty = all of them.
 * @property radioProviders extra provider names to treat as live radio (no progress bar or lyrics).
 */
data class AlexaSettings(
    val account: AccountRef?,
    val showNowPlaying: Boolean,
    val showLyrics: Boolean,
    val showTimers: Boolean,
    val showAlarms: Boolean,
    val devices: List<String>,
    val radioProviders: List<String>,
)

object AlexaSettingsSchema : SettingsSchema<AlexaSettings>() {
    private val account = account("account", label = "Amazon account", provider = AlexaPlugin.PROVIDER)
    private val showNowPlaying = toggle("showNowPlaying", label = "Show what's playing", default = true)
    private val showLyrics = toggle("showLyrics", label = "Show synced lyrics", description = "From lrclib.net, when a song has them.", default = true)
    private val showTimers = toggle("showTimers", label = "Show timers", default = true)
    private val showAlarms = toggle("showAlarms", label = "Show alarms and reminders", description = "Those due in the next 12 hours.", default = true)
    private val devices = textList("devices", label = "Echo devices", description = "Names as shown in the Alexa app. Leave empty for all.")
    private val radioProviders = textList(
        "radioProviders",
        label = "Radio services",
        description = "Extra service names to treat as live radio (no progress bar or lyrics).",
    )

    override fun build(values: SettingsValues) = AlexaSettings(
        account = values[account],
        showNowPlaying = values[showNowPlaying],
        showLyrics = values[showLyrics],
        showTimers = values[showTimers],
        showAlarms = values[showAlarms],
        devices = values[devices],
        radioProviders = values[radioProviders],
    )
}
