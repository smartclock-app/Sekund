package uk.dnpk.sekund.runtime.logging

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import kotlinx.io.IOException
import kotlinx.io.buffered
import kotlinx.io.files.FileSystem
import kotlinx.io.files.Path
import kotlinx.io.files.SystemFileSystem
import kotlinx.io.writeString
import uk.dnpk.sekund.protocol.api.LogLine

/**
 * Writes the log to `<logs>/sekund.log`, so it survives a crash or restart. When the file passes
 * [maxBytes] it's renamed to `sekund.log.1` (replacing the previous one) and a new file is started,
 * so the logs never use more than about twice [maxBytes].
 *
 * All writing happens in one coroutine, so lines never interleave.
 */
class LogFile(
    private val folder: Path,
    private val maxBytes: Long = 1_000_000,
    private val fileSystem: FileSystem = SystemFileSystem,
) {
    private val file = Path(folder, "sekund.log")
    private val previous = Path(folder, "sekund.log.1")

    /** Starts copying every line from [buffer] into the file. */
    fun follow(buffer: LogBuffer, scope: CoroutineScope) {
        scope.launch {
            buffer.lines.collect { line -> append(line) }
        }
    }

    private fun append(line: LogLine) {
        try {
            fileSystem.createDirectories(folder)
            rollIfTooBig()
            fileSystem.sink(file, append = true).buffered().use { sink ->
                sink.writeString("${line.at} ${line.level.uppercase()} [${line.tag}] ${line.message}\n")
            }
        } catch (_: IOException) {
            // Nowhere to report a failure to write the log; carry on without it.
        }
    }

    private fun rollIfTooBig() {
        val size = fileSystem.metadataOrNull(file)?.size ?: return
        if (size < maxBytes) return
        fileSystem.delete(previous, mustExist = false)
        fileSystem.atomicMove(file, previous)
    }
}
