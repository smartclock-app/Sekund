package uk.dnpk.sekund.plugins.google

import uk.dnpk.sekund.sdk.settings.AccountRef
import uk.dnpk.sekund.sdk.settings.SettingsSchema
import uk.dnpk.sekund.sdk.settings.SettingsValues

/**
 * One Google Calendar source's settings.
 *
 * @property account the Google account to read, or null if none is picked yet.
 * @property calendars names of the calendars to include (as shown in Google Calendar). Empty = all of them.
 * @property maxEventsPerCalendar how many upcoming events to fetch from each calendar.
 * @property showRepeatsOnce if true, an event title appears only once per calendar (the next occurrence),
 *   so a weekly meeting doesn't fill the list. This was always on in the old app.
 */
data class GoogleCalendarSettings(
    val account: AccountRef?,
    val calendars: List<String>,
    val maxEventsPerCalendar: Int,
    val showRepeatsOnce: Boolean,
)

object GoogleCalendarSettingsSchema : SettingsSchema<GoogleCalendarSettings>() {
    private val account = account("account", label = "Google account", provider = GooglePlugin.PROVIDER)
    private val calendars = textList(
        "calendars",
        label = "Only these calendars",
        description = "Calendar names as shown in Google Calendar. Leave empty to show every calendar.",
    )
    private val maxEventsPerCalendar = number("maxEventsPerCalendar", label = "Events per calendar", default = 50, min = 1, max = 250)
    private val showRepeatsOnce = toggle(
        "showRepeatsOnce",
        label = "Show repeating events once",
        description = "Only the next occurrence of each event title in a calendar.",
        default = true,
    )

    override fun build(values: SettingsValues) = GoogleCalendarSettings(
        account = values[account],
        calendars = values[calendars],
        maxEventsPerCalendar = values[maxEventsPerCalendar],
        showRepeatsOnce = values[showRepeatsOnce],
    )
}
