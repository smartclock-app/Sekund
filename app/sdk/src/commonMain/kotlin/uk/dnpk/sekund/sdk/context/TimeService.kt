package uk.dnpk.sekund.sdk.context

import kotlinx.coroutines.flow.StateFlow
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toInstant
import kotlin.time.Instant

/**
 * The time, as Sekund sees it. Use this instead of reading the system clock yourself: it ticks exactly
 * when the second (or minute) changes, and tests can replace it with a fake clock.
 */
interface TimeService {
    /** The current local time, updated at the start of every second. */
    val everySecond: StateFlow<LocalDateTime>

    /** The current local time, updated at the start of every minute. Cheaper if you don't show seconds. */
    val everyMinute: StateFlow<LocalDateTime>

    /** The device's time zone. */
    val timeZone: StateFlow<TimeZone>

    /** How the person wants times shown (12/24 hour, seconds on/off). */
    val preferences: StateFlow<TimePreferences>
}

/**
 * The current moment as an [Instant], according to this time service (so tests can control it).
 * Use this instead of `Clock.System.now()` in plugins.
 */
fun TimeService.now(): Instant = everySecond.value.toInstant(timeZone.value)

/** The person's time display preferences, set in Sekund's core settings. */
data class TimePreferences(
    val use24Hour: Boolean = false,
    val showSeconds: Boolean = true,
)
