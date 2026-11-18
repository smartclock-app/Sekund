package uk.dnpk.sekund.runtime.desktop

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.concurrent.TimeUnit

/** What happened when a command ran. [exitCode] is null if it was stopped for taking too long. */
data class CommandResult(val exitCode: Int?, val output: String) {
    val succeeded: Boolean
        get() = exitCode == 0
}

/** Runs a shell command. An interface so tests can check which commands would run without running them. */
fun interface CommandRunner {
    suspend fun run(command: String): CommandResult
}

/**
 * Runs commands with the system shell: `sh -c` on Linux and macOS, `cmd /c` on Windows.
 *
 * @param timeoutSeconds how long a command may take before it's stopped. Screen commands are quick;
 *   one that hangs (e.g. waiting for a password) shouldn't hang the caller.
 */
class ShellCommandRunner(private val timeoutSeconds: Long = 10) : CommandRunner {
    override suspend fun run(command: String): CommandResult = withContext(Dispatchers.IO) {
        val process = ProcessBuilder(shellFor(command))
            .redirectErrorStream(true) // one stream with both normal output and errors, for the log
            .start()
        val finished = process.waitFor(timeoutSeconds, TimeUnit.SECONDS)
        if (!finished) {
            process.destroyForcibly()
            return@withContext CommandResult(exitCode = null, output = "Stopped after $timeoutSeconds seconds")
        }
        val output = process.inputStream.bufferedReader().readText().trim()
        CommandResult(exitCode = process.exitValue(), output = output)
    }

    private fun shellFor(command: String): List<String> {
        val isWindows = System.getProperty("os.name").lowercase().contains("win")
        if (isWindows) return listOf("cmd", "/c", command)
        return listOf("sh", "-c", command)
    }
}
