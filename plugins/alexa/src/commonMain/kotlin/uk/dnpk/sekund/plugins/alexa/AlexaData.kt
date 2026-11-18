package uk.dnpk.sekund.plugins.alexa

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import uk.dnpk.sekund.sdk.context.InstanceContext
import uk.dnpk.sekund.sdk.context.Loadable
import uk.dnpk.sekund.sdk.context.RefreshPolicy
import uk.dnpk.sekund.sdk.context.dataOrNull
import uk.dnpk.sekund.sdk.context.now
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds
import kotlin.time.Instant

/**
 * Everything the Alexa widget shows, loaded in the background (no UI here):
 *
 * - [nowPlaying]: checked every 10 seconds while something plays, every 2 minutes otherwise,
 * - [lyrics]: looked up whenever the song changes,
 * - [reminders]: timers and alarms, every minute (countdowns tick locally).
 */
class AlexaData(private val context: InstanceContext<AlexaSettings>) {
    private val settings = context.settings
    private val lyricsLibrary = LyricsLibrary(context.http, context.files, context.log)

    // Looked up once, then reused: the device list rarely changes, the customer id never does.
    private var devices: List<AlexaDevice> = emptyList()
    private var devicesFetchedAt: Instant? = null
    private var customerId: String? = null

    /** When we last asked what's playing, to slow down while nothing is. */
    private var lastNowPlayingCheck: Instant? = null

    val nowPlaying: StateFlow<Loadable<NowPlaying?>> =
        if (settings.showNowPlaying) {
            // A 10 s schedule; fetchNowPlaying itself skips the request while idle (see IDLE_CHECK).
            context.refresher.poll(RefreshPolicy(every = 10.seconds, minimum = 10.seconds)) { fetchNowPlaying() }
        } else {
            MutableStateFlow(Loadable.Ready(null, context.time.now()))
        }

    val reminders: StateFlow<Loadable<Reminders>> =
        if (settings.showTimers || settings.showAlarms) {
            context.refresher.poll(RefreshPolicy(every = 1.minutes)) { fetchReminders() }
        } else {
            MutableStateFlow(Loadable.Ready(Reminders(emptyList(), emptyList()), context.time.now()))
        }

    private val currentLyrics = MutableStateFlow<Lyrics?>(null)

    /** Synced lyrics of the song playing, or null. */
    val lyrics: StateFlow<Lyrics?> = currentLyrics

    init {
        if (settings.showNowPlaying && settings.showLyrics) {
            context.scope.launch {
                // Look lyrics up once per song: only when the title or artist changes.
                nowPlaying
                    .map { state -> songOf(state.dataOrNull()) }
                    .distinctUntilChanged()
                    .collect { song ->
                        currentLyrics.value = null
                        if (song != null) currentLyrics.value = lyricsLibrary.find(song.title, song.artist)
                    }
            }
        }
    }

    private data class Song(val title: String, val artist: String?)

    private fun songOf(playing: NowPlaying?): Song? {
        if (playing == null || playing.isRadio) return null
        return Song(playing.title, playing.artist)
    }

    private fun api(): AlexaApi {
        val account = settings.account ?: throw IllegalStateException("Pick an Amazon account in this widget's settings")
        val domain = AmazonCookieHandler.domainOf(context.auth.fields(account))
        return AlexaApi(context.auth.client(account), domain)
    }

    private suspend fun fetchNowPlaying(): NowPlaying? {
        val now = context.time.now()
        val previous = nowPlaying.value.dataOrNull()
        val lastCheck = lastNowPlayingCheck
        if (previous == null && lastCheck != null && now - lastCheck < IDLE_CHECK) return null // idle: check less often
        lastNowPlayingCheck = now

        val api = api()
        val followed = followedDevices(api)
        val anyDevice = followed.firstOrNull() ?: devices.firstOrNull() ?: return null
        var customer = customerId
        if (customer == null) {
            customer = api.customerId()
            customerId = customer
        }
        val sessions = api.mediaSessions(anyDevice, customer)
        return NowPlayingPicker.pick(sessions, followed, settings.radioProviders, now)
    }

    private suspend fun fetchReminders(): Reminders {
        val api = api()
        val followedSerials = followedDevices(api).map { device -> device.serialNumber }.toSet()
        return RemindersPicker.pick(api.notifications(), followedSerials, settings, context.time.now(), context.time.timeZone.value)
    }

    /** The devices named in the settings (empty = follow all). Refreshes the device list hourly. */
    private suspend fun followedDevices(api: AlexaApi): List<AlexaDevice> {
        val now = context.time.now()
        val fetchedAt = devicesFetchedAt
        if (fetchedAt == null || now - fetchedAt > 1.hours) {
            devices = api.devices()
            devicesFetchedAt = now
        }
        if (settings.devices.isEmpty()) return emptyList()
        val wanted = settings.devices.map { name -> name.trim().lowercase() }.toSet()
        return devices.filter { device -> device.accountName.lowercase() in wanted }
    }

    private companion object {
        /** How often to check while nothing is playing. */
        val IDLE_CHECK = 2.minutes
    }
}
