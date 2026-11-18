package uk.dnpk.sekund.plugins.alexa

import io.ktor.client.HttpClient
import io.ktor.client.request.get
import io.ktor.client.request.parameter
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsText
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

// The unofficial Alexa web API (what alexa.amazon.<site> itself calls). It isn't documented and Amazon
// may change it; every field is optional so small changes don't break the widget.

@Serializable
data class AlexaDevice(
    val accountName: String = "",
    val deviceType: String = "",
    val serialNumber: String = "",
    val deviceFamily: String = "",
)

@Serializable
private data class DeviceList(val devices: List<AlexaDevice> = emptyList())

@Serializable
private data class Me(val id: String = "")

@Serializable
data class MediaSessions(val mediaSessionList: List<MediaSession> = emptyList())

@Serializable
data class MediaSession(
    val endpointList: List<MediaEndpoint> = emptyList(),
    val nowPlayingData: NowPlayingData? = null,
)

@Serializable
data class MediaEndpoint(val id: MediaEndpointId? = null)

@Serializable
data class MediaEndpointId(val deviceSerialNumber: String? = null, val deviceType: String? = null)

@Serializable
data class NowPlayingData(
    /** "PLAYING", "PAUSED", "IDLE"... */
    val playerState: String? = null,
    val infoText: InfoText? = null,
    val mainArt: MainArt? = null,
    val progress: MediaProgress? = null,
    val provider: MediaProvider? = null,
)

/** [title] is the song; [subText1] usually the artist; [subText2] the album. */
@Serializable
data class InfoText(val title: String? = null, val subText1: String? = null, val subText2: String? = null)

@Serializable
data class MainArt(val fullUrl: String? = null)

/** Both in milliseconds. A length of 0 or less means live (radio). */
@Serializable
data class MediaProgress(val mediaLength: Long? = null, val mediaProgress: Long? = null)

@Serializable
data class MediaProvider(val providerName: String? = null)

@Serializable
data class NotificationList(val notifications: List<AlexaNotification> = emptyList())

/**
 * A timer, alarm or reminder.
 *
 * @property type "Timer", "Alarm", "MusicAlarm" or "Reminder".
 * @property status "ON" while active.
 * @property triggerTime timers: when it goes off, in epoch milliseconds.
 * @property originalDate / originalTime alarms: local date and time, e.g. "2025-06-18" and "07:30:00.000".
 * @property snoozedToTime a snoozed alarm's new time, same format as [originalTime].
 */
@Serializable
data class AlexaNotification(
    val id: String = "",
    val type: String = "",
    val status: String = "",
    val deviceSerialNumber: String? = null,
    val triggerTime: Long? = null,
    val remainingTime: Long? = null,
    val originalDate: String? = null,
    val originalTime: String? = null,
    val snoozedToTime: String? = null,
    val timerLabel: String? = null,
    val alarmLabel: String? = null,
    val reminderLabel: String? = null,
)

/** Talks to the Alexa web API of [domain] (e.g. "amazon.co.uk"), signed in through [client]. */
class AlexaApi(private val client: HttpClient, domain: String) {
    private val base = "https://alexa.$domain/api"
    private val json = Json { ignoreUnknownKeys = true }

    suspend fun devices(): List<AlexaDevice> =
        json.decodeFromString<DeviceList>(textOf(client.get("$base/devices-v2/device?cached=false"), "devices")).devices

    /** The account's customer id, needed to ask what's playing. */
    suspend fun customerId(): String = json.decodeFromString<Me>(textOf(client.get("$base/users/me"), "the account")).id

    /** Every media session of the account. [device] is needed by the API but doesn't limit the answer. */
    suspend fun mediaSessions(device: AlexaDevice, customerId: String): List<MediaSession> {
        val response = client.get("$base/np/list-media-sessions") {
            parameter("deviceSerialNumber", device.serialNumber)
            parameter("deviceType", device.deviceType)
            parameter("mediaOwnerCustomerId", customerId)
        }
        return json.decodeFromString<MediaSessions>(textOf(response, "what's playing")).mediaSessionList
    }

    suspend fun notifications(): List<AlexaNotification> =
        json.decodeFromString<NotificationList>(textOf(client.get("$base/notifications"), "timers and alarms")).notifications

    private suspend fun textOf(response: HttpResponse, what: String): String {
        val text = response.bodyAsText()
        if (response.status.value !in 200..299) throw IllegalStateException("Alexa answered ${response.status.value} for $what")
        return text
    }
}
