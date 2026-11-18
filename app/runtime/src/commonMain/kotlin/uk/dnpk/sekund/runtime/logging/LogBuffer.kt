package uk.dnpk.sekund.runtime.logging

import co.touchlab.kermit.LogWriter
import co.touchlab.kermit.Severity
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.update
import uk.dnpk.sekund.protocol.api.LogLine
import kotlin.time.Clock

/**
 * Keeps the most recent log lines in memory (for `GET /v1/logs`) and announces each new one (for the
 * events stream and the log file). Installed as a Kermit log writer, so everything logged anywhere in
 * the app passes through here.
 *
 * Only info and above is kept: debug lines would push the useful ones out.
 *
 * @param keep how many lines to remember.
 */
class LogBuffer(
    private val keep: Int = 500,
    private val clock: Clock = Clock.System,
) : LogWriter() {
    // StateFlow.update is safe to call from several threads at once, which logging needs.
    private val recent = MutableStateFlow<List<LogLine>>(emptyList())
    private val newLines = MutableSharedFlow<LogLine>(extraBufferCapacity = 256)

    /** Every line as it's logged. Slow listeners miss lines rather than slow the app down. */
    val lines: SharedFlow<LogLine> = newLines

    /** The last [count] lines, oldest first. */
    fun tail(count: Int): List<LogLine> = recent.value.takeLast(count.coerceAtLeast(0))

    override fun isLoggable(tag: String, severity: Severity): Boolean = severity >= Severity.Info

    override fun log(severity: Severity, message: String, tag: String, throwable: Throwable?) {
        val text = if (throwable == null) message else "$message (${throwable::class.simpleName}: ${throwable.message})"
        val line = LogLine(at = clock.now().toString(), level = severity.name.lowercase(), tag = tag, message = text)
        recent.update { lines -> (lines + line).takeLast(keep) }
        newLines.tryEmit(line)
    }
}
