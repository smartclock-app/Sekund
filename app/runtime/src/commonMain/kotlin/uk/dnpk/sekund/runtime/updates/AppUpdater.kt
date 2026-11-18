package uk.dnpk.sekund.runtime.updates

import co.touchlab.kermit.Logger
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import uk.dnpk.sekund.protocol.config.AppConfig
import uk.dnpk.sekund.protocol.config.UpdatesConfig
import kotlin.time.Duration
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds

/**
 * Keeps Sekund up to date. Part of the app itself (not a plugin), so updates keep happening whatever the
 * layout contains.
 *
 * Checks shortly after start and then every `updates.checkEvery`. When a new version is found it's shown
 * on a sidebar card ([status]); it's installed when someone taps Install (or runs the `update.install`
 * action), or straight away if `updates.installAutomatically` is on.
 */
class AppUpdater(
    private val service: UpdateService,
    private val config: StateFlow<AppConfig>,
    private val scope: CoroutineScope,
) {
    private val log = Logger.withTag("Updates")

    private val current = MutableStateFlow(UpdateStatus())

    /** What the sidebar card shows. */
    val status: StateFlow<UpdateStatus> = current

    /** One check at a time, whether from the schedule or the `update.check` action. */
    private val checkLock = Mutex()
    private var installJob: Job? = null

    /** Versions already installed by themselves, so a failing automatic install isn't retried forever. */
    private val autoInstalled = mutableSetOf<String>()

    val currentVersion: String get() = service.currentVersion

    /** Starts the schedule. Restarts it whenever the `updates` settings change. */
    fun start() {
        scope.launch {
            config.map { appConfig -> appConfig.updates }
                .distinctUntilChanged()
                .collectLatest { settings -> checkOnSchedule(settings) }
        }
    }

    private suspend fun checkOnSchedule(settings: UpdatesConfig) {
        val every = checkInterval(settings.checkEvery)
        delay(FIRST_CHECK_AFTER) // let the app start up first
        while (true) {
            checkNow()
            delay(every)
        }
    }

    /** Looks for a new version now (and installs it if the settings say so). Never throws. */
    suspend fun checkNow() {
        checkLock.withLock {
            try {
                val result = service.check()
                current.update { status -> status.copy(check = result, checkProblem = null) }
                if (result is UpdateCheck.Unavailable) log.i { "Not updating: ${result.reason}" }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                log.w(error) { "Update check failed" }
                current.update { status -> status.copy(checkProblem = error.message ?: "The update check failed") }
            }
        }
        installByItselfIfWanted()
    }

    /** Downloads and installs the version on offer. False if there's none; true if started or already running. */
    fun installNow(): Boolean {
        val update = current.value.available() ?: return false
        if (installJob?.isActive == true) return true
        installJob = scope.launch { install(update) }
        return true
    }

    private fun installByItselfIfWanted() {
        if (!config.value.updates.installAutomatically) return
        val update = current.value.available() ?: return
        if (!autoInstalled.add(update.version)) return
        log.i { "Installing ${update.version} by itself" }
        installNow()
    }

    private suspend fun install(update: AvailableUpdate) {
        try {
            setInstall(InstallState.Downloading(null))
            val downloaded = service.download(update) { fraction -> setInstall(InstallState.Downloading(fraction)) }
            setInstall(InstallState.Installing)
            val result = service.install(downloaded)
            val state = when (result) {
                is InstallResult.Installing -> InstallState.Installing
                is InstallResult.WaitingForUser -> InstallState.WaitingForUser
                is InstallResult.NotSupported -> InstallState.NotSupported(result.reason)
            }
            setInstall(state)
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (error: Exception) {
            log.w(error) { "Update to ${update.version} failed" }
            setInstall(InstallState.Failed(error.message ?: "The update failed"))
        }
    }

    private fun setInstall(state: InstallState) {
        current.update { status -> status.copy(install = state) }
    }

    companion object {
        val FIRST_CHECK_AFTER: Duration = 30.seconds
        val MINIMUM_INTERVAL: Duration = 5.minutes
        val DEFAULT_INTERVAL: Duration = 6.hours

        /** `checkEvery` as a duration: unreadable values give 6 hours, too-short ones 5 minutes. */
        fun checkInterval(text: String): Duration {
            val parsed = Duration.parseOrNull(text) ?: return DEFAULT_INTERVAL
            return maxOf(parsed, MINIMUM_INTERVAL)
        }
    }
}

/**
 * Everything the update card needs.
 *
 * @property check the latest successful check, or null before the first one.
 * @property checkProblem why the latest check failed, if it did (the previous [check] is kept).
 */
data class UpdateStatus(
    val check: UpdateCheck? = null,
    val checkProblem: String? = null,
    val install: InstallState = InstallState.Idle,
) {
    /** The version on offer, if there is one. */
    fun available(): AvailableUpdate? {
        val latest = check
        if (latest is UpdateCheck.Available) return latest.update
        return null
    }
}

/** Where an install has got to. */
sealed class InstallState {
    data object Idle : InstallState()

    /** [fraction] is 0.0 to 1.0, or null while the size is unknown. */
    data class Downloading(val fraction: Float?) : InstallState()

    data object Installing : InstallState()

    /** The system installer is open on screen. */
    data object WaitingForUser : InstallState()

    data class NotSupported(val reason: String) : InstallState()

    data class Failed(val message: String) : InstallState()
}
