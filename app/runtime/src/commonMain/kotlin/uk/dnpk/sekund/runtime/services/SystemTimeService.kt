package uk.dnpk.sekund.runtime.services

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import uk.dnpk.sekund.sdk.context.TimePreferences
import uk.dnpk.sekund.sdk.context.TimeService
import kotlin.time.Clock

/**
 * The real [TimeService]: one shared ticker for the whole app.
 *
 * It sleeps until the exact start of the next second, then updates. (The old app polled every second
 * from whenever it happened to start, so the display could lag the real second by up to a second.)
 */
class SystemTimeService(
    scope: CoroutineScope,
    override val preferences: StateFlow<TimePreferences>,
    private val clock: Clock = Clock.System,
) : TimeService {
    private val zone = MutableStateFlow(TimeZone.currentSystemDefault())
    private val second = MutableStateFlow(localNow())
    private val minute = MutableStateFlow(startOfMinute(second.value))

    override val timeZone: StateFlow<TimeZone> = zone
    override val everySecond: StateFlow<LocalDateTime> = second
    override val everyMinute: StateFlow<LocalDateTime> = minute

    init {
        scope.launch { tickForever() }
    }

    private suspend fun tickForever() {
        while (true) {
            val millisIntoSecond = clock.now().toEpochMilliseconds() % 1000
            delay(1000 - millisIntoSecond)

            // The time zone can change while running (travel, daylight saving rules), so re-check it.
            zone.value = TimeZone.currentSystemDefault()
            val now = localNow()
            second.value = now

            val nowMinute = startOfMinute(now)
            if (nowMinute != minute.value) {
                minute.value = nowMinute
            }
        }
    }

    private fun localNow(): LocalDateTime = clock.now().toLocalDateTime(zone.value)

    private fun startOfMinute(time: LocalDateTime): LocalDateTime =
        LocalDateTime(time.year, time.month, time.day, time.hour, time.minute, 0, 0)
}
