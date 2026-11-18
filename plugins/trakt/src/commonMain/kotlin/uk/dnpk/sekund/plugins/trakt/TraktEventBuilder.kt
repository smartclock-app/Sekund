package uk.dnpk.sekund.plugins.trakt

import kotlinx.datetime.DatePeriod
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.LocalTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.plus
import kotlinx.datetime.atStartOfDayIn
import kotlinx.datetime.toLocalDateTime
import uk.dnpk.sekund.sdk.calendar.CalendarEvent
import kotlin.time.Duration.Companion.hours
import kotlin.time.Instant

/**
 * Turns cached items into calendar events, the way the old app did:
 * - items airing at the same moment share one event, titles sorted (first line: the prefix, if set),
 * - something airing within the next 24 hours is shown at its exact time,
 * - anything later is an all-day event on its day,
 * - things that aired before today, or have no date, are left out,
 * - at most `maxItems` events.
 */
object TraktEventBuilder {

    fun build(items: List<CachedItem>, now: Instant, timeZone: TimeZone, settings: TraktSettings): List<CalendarEvent> {
        val today = now.toLocalDateTime(timeZone).date
        val soon = now + 24.hours

        // Start time -> names, in time order (a LinkedHashMap keeps insertion order).
        val byStart = LinkedHashMap<LocalDateTime, MutableList<String>>()
        val timed = mutableSetOf<LocalDateTime>()
        val dated = mutableListOf<DatedItem>()
        for (item in items) {
            val airs = airTime(item, timeZone) ?: continue
            dated.add(DatedItem(item.name, airs))
        }
        dated.sortBy { entry -> entry.airs }

        for (entry in dated) {
            val airs = entry.airs
            val local = airs.toLocalDateTime(timeZone)
            if (local.date < today) continue

            val start: LocalDateTime
            if (airs < soon) {
                start = local
                timed.add(start)
            } else {
                start = LocalDateTime(local.date, LocalTime(0, 0))
            }

            val names = byStart[start]
            if (names == null) {
                if (byStart.size >= settings.maxItems) break
                byStart[start] = mutableListOf(entry.name)
            } else {
                names.add(entry.name)
            }
        }

        val events = mutableListOf<CalendarEvent>()
        for ((start, names) in byStart) {
            val isTimed = start in timed
            val titleLines = mutableListOf<String>()
            if (settings.prefix.isNotEmpty()) titleLines.add(settings.prefix)
            titleLines.addAll(names.sorted())
            events.add(
                CalendarEvent(
                    id = "trakt/$start",
                    titleLines = titleLines,
                    start = start,
                    // Exact times have no length ("airs at 21:00"); later ones fill their whole day.
                    end = if (isTimed) start else LocalDateTime(start.date.plus(DatePeriod(days = 1)), LocalTime(0, 0)),
                    allDay = !isTimed,
                    color = settings.color,
                ),
            )
        }
        return events
    }

    /**
     * When [item] airs as an instant. Episode times are UTC timestamps; film release dates are plain dates,
     * taken as local midnight. Null if there's no date or it can't be read.
     */
    fun airTime(item: CachedItem, timeZone: TimeZone): Instant? {
        val text = item.airDate ?: return null
        try {
            if (text.length == 10) return LocalDate.parse(text).atStartOfDayIn(timeZone)
            return Instant.parse(text)
        } catch (error: IllegalArgumentException) {
            return null
        }
    }

    /** An item's name and when it airs. */
    private class DatedItem(val name: String, val airs: Instant)
}
