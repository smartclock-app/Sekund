package uk.dnpk.sekund.runtime.desktop

import co.touchlab.kermit.Logger
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.io.files.Path
import uk.dnpk.sekund.runtime.platform.DisplayControl

/**
 * Screen on and off on desktop and the Raspberry Pi, by running the commands in `display.json`
 * (see [DisplayCommands]).
 *
 * There's no reliable way to ask every kind of screen whether it's on, so [isOn] is what Sekund last
 * set it to. It starts as on, because Sekund is on screen when it starts.
 */
class CommandDisplayControl(
    private val commands: DisplayCommands,
    private val runner: CommandRunner = ShellCommandRunner(),
) : DisplayControl {
    private val log = Logger.withTag("Display")
    private val screenOn = MutableStateFlow(true)

    override val isOn: StateFlow<Boolean> = screenOn

    /** Nothing to grant on desktop: if the commands are wrong, running them says so. */
    override fun isReady(): Boolean = true

    override fun requestSetup() {
        // Nothing to set up (see isReady).
    }

    override suspend fun turnOn() {
        runOrThrow(commands.on, "on")
        screenOn.value = true
    }

    override suspend fun turnOff() {
        runOrThrow(commands.off, "off")
        screenOn.value = false
    }

    private suspend fun runOrThrow(command: String, what: String) {
        val result = runner.run(command)
        if (!result.succeeded) {
            val why = if (result.exitCode == null) result.output else "exit code ${result.exitCode}: ${result.output}"
            throw IllegalStateException("Couldn't turn the screen $what with `$command` ($why)")
        }
        log.i { "Screen $what" }
    }

    companion object {
        /**
         * Display control from `<configDir>/display.json`, or null without one. A broken file is logged and
         * also gives null: the app should still start, just without display control. Read once at startup.
         */
        fun fromConfigFolder(configDir: Path): CommandDisplayControl? {
            val file = Path(configDir, DisplayCommands.FILE_NAME)
            try {
                val commands = DisplayCommands.load(file) ?: return null
                return CommandDisplayControl(commands)
            } catch (error: IllegalStateException) {
                Logger.withTag("Display").e(error) { "Display control is off: ${error.message}" }
                return null
            }
        }
    }
}
