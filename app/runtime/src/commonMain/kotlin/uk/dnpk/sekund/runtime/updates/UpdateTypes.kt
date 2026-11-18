package uk.dnpk.sekund.runtime.updates

/** The answer to [UpdateService.check]. */
sealed class UpdateCheck {
    /** Nothing newer. [latest] is the newest released version. */
    data class UpToDate(val latest: String) : UpdateCheck()

    data class Available(val update: AvailableUpdate) : UpdateCheck()

    /** This build can't update itself, e.g. a development build, or no download for this platform. */
    data class Unavailable(val reason: String) : UpdateCheck()
}

/** A newer release with a download for this device. [notes] is a short summary, if the release has one. */
data class AvailableUpdate(
    val version: String,
    val notes: String?,
    val url: String,
    val sha256: String,
    val sizeBytes: Long?,
)

/** A verified download, ready for [UpdateService.install]. */
data class DownloadedUpdate(val version: String, val file: String)

/** What installing did. */
sealed class InstallResult {
    /** Installing without asking (e.g. Android as device owner). Sekund will restart by itself. */
    data object Installing : InstallResult()

    /** The system's installer was opened; someone has to finish it on screen. */
    data object WaitingForUser : InstallResult()

    /** This platform can't install updates yet. The download is kept. */
    data class NotSupported(val reason: String) : InstallResult()
}

/** A manifest or download didn't pass verification, so it wasn't used. */
class UpdateRejectedException(message: String) : IllegalStateException(message)
