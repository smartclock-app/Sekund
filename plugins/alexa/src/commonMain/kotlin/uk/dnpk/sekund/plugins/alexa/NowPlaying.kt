package uk.dnpk.sekund.plugins.alexa

import kotlin.time.Instant

/**
 * A song (or station) playing on one of the followed Echos.
 *
 * @property progressMs how far in it was at [seenAt]. Between fetches the widget moves it on itself
 *   ([progressAt]), so the bar and lyrics advance smoothly with only one request every 10 seconds.
 * @property isRadio live radio: no length, so no progress bar or lyrics.
 */
data class NowPlaying(
    val title: String,
    val artist: String?,
    val artUrl: String?,
    val provider: String?,
    val lengthMs: Long,
    val progressMs: Long,
    val seenAt: Instant,
    val isRadio: Boolean,
) {
    /** The position at [now], counted on from the last fetch, never past the end. */
    fun progressAt(now: Instant): Long {
        if (isRadio) return 0
        val moved = (now - seenAt).inWholeMilliseconds.coerceAtLeast(0)
        return (progressMs + moved).coerceAtMost(lengthMs)
    }
}

object NowPlayingPicker {
    /** Providers that are always live radio (from the old app), on top of the user's own list. */
    private val RADIO_PROVIDERS = setOf("", "Unknown Provider", "PLANET_RADIO", "TuneIn", "Global Player")

    /**
     * The first session that's PLAYING on one of [followed] (all devices if [followed] is empty).
     * Null if nothing is playing.
     */
    fun pick(
        sessions: List<MediaSession>,
        followed: List<AlexaDevice>,
        radioProviders: List<String>,
        now: Instant,
    ): NowPlaying? {
        for (session in sessions) {
            val data = session.nowPlayingData ?: continue
            if (data.playerState != "PLAYING") continue
            if (followed.isNotEmpty() && !playsOn(session, followed)) continue

            val title = data.infoText?.title ?: continue
            val provider = data.provider?.providerName
            val length = data.progress?.mediaLength ?: 0
            val isRadio = length <= 0 || (provider ?: "Unknown Provider") in RADIO_PROVIDERS || provider in radioProviders
            return NowPlaying(
                title = title,
                artist = data.infoText.subText1,
                artUrl = data.mainArt?.fullUrl,
                provider = provider,
                lengthMs = length.coerceAtLeast(0),
                progressMs = data.progress?.mediaProgress ?: 0,
                seenAt = now,
                isRadio = isRadio,
            )
        }
        return null
    }

    private fun playsOn(session: MediaSession, devices: List<AlexaDevice>): Boolean {
        for (endpoint in session.endpointList) {
            val id = endpoint.id ?: continue
            for (device in devices) {
                if (id.deviceSerialNumber == device.serialNumber && id.deviceType == device.deviceType) return true
            }
        }
        return false
    }
}
