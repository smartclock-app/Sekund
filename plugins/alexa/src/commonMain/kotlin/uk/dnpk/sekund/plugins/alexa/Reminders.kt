package uk.dnpk.sekund.plugins.alexa

import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.LocalTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toInstant
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Instant

/** A running timer. The widget counts down to [endsAt] itself, every second. */
data class AlexaTimer(val id: String, val label: String, val endsAt: Instant)

/** An alarm or reminder due soon. */
data class AlexaAlarm(val id: String, val label: String, val at: LocalDateTime)

/** The timers and alarms to show, soonest first. */
data class Reminders(val timers: List<AlexaTimer>, val alarms: List<AlexaAlarm>)

object RemindersPicker {
    /**
     * Active timers and the alarms/reminders due within 12 hours, on the followed devices (all devices if
     * [followedSerials] is empty), as the settings ask.
     */
    fun pick(
        notifications: List<AlexaNotification>,
        followedSerials: Set<String>,
        settings: AlexaSettings,
        now: Instant,
        timeZone: TimeZone,
    ): Reminders {
        val timers = mutableListOf<AlexaTimer>()
        val alarms = mutableListOf<AlexaAlarm>()

        for (notification in notifications) {
            if (notification.status != "ON") continue
            if (followedSerials.isNotEmpty() && notification.deviceSerialNumber !in followedSerials) continue

            when (notification.type) {
                "Timer" -> {
                    if (!settings.showTimers) continue
                    val endsAt = timerEnd(notification, now) ?: continue
                    timers.add(AlexaTimer(notification.id, notification.timerLabel ?: "Timer", endsAt))
                }
                "Alarm", "MusicAlarm", "Reminder" -> {
                    if (!settings.showAlarms) continue
                    val at = alarmTime(notification) ?: continue
                    val inHowLong = at.toInstant(timeZone) - now
                    if (inHowLong < 0.hours || inHowLong > 12.hours) continue
                    val label = notification.reminderLabel ?: notification.alarmLabel ?: if (notification.type == "Reminder") "Reminder" else "Alarm"
                    alarms.add(AlexaAlarm(notification.id, label, at))
                }
            }
        }
        return Reminders(timers.sortedBy { timer -> timer.endsAt }, alarms.sortedBy { alarm -> alarm.at })
    }

    /** When a timer goes off: its trigger time if given, otherwise now plus the time remaining. */
    private fun timerEnd(notification: AlexaNotification, now: Instant): Instant? {
        val trigger = notification.triggerTime
        if (trigger != null && trigger > 0) return Instant.fromEpochMilliseconds(trigger)
        val remaining = notification.remainingTime ?: return null
        return now + remaining.milliseconds
    }

    /** "2025-06-18" + "07:30:00.000" (or the snoozed time) as a local date-time. */
    private fun alarmTime(notification: AlexaNotification): LocalDateTime? {
        val date = notification.originalDate ?: return null
        val time = notification.snoozedToTime ?: notification.originalTime ?: return null
        try {
            return LocalDateTime(LocalDate.parse(date), LocalTime.parse(time))
        } catch (error: IllegalArgumentException) {
            return null
        }
    }
}
