package uk.dnpk.sekund.sdk.calendar

import androidx.compose.ui.graphics.Color
import kotlinx.datetime.LocalDateTime

/**
 * One event shown by calendar widgets.
 *
 * Times are in the device's time zone (calendar sources convert them), so the widget can format them directly.
 *
 * @property id unique within its source.
 * @property titleLines the title, one entry per line. Usually one line; Trakt uses two (show + episode).
 * @property allDay true for events that last whole days. For an all-day event, [end] is the start of the
 *   day *after* the last day, as most calendar APIs report it (a one-day event on the 3rd ends at 00:00 on the 4th).
 * @property color the coloured bar shown next to the event, or null to use the source's default.
 */
data class CalendarEvent(
    val id: String,
    val titleLines: List<String>,
    val start: LocalDateTime,
    val end: LocalDateTime,
    val allDay: Boolean,
    val color: Color? = null,
)
