package uk.dnpk.sekund.runtime.desktop

import kotlinx.io.buffered
import kotlinx.io.files.FileSystem
import kotlinx.io.files.Path
import kotlinx.io.files.SystemFileSystem
import kotlinx.io.readString
import kotlinx.serialization.SerializationException
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * The two shell commands that turn the screen on and off on desktop and the Raspberry Pi.
 *
 * Each is run by the system shell (`sh -c` or `cmd /c`), so it can be anything you'd type in a terminal.
 */
data class DisplayCommands(val on: String, val off: String) {
    companion object {
        /** The name of the file in the config folder: `<config>/display.json`. */
        const val FILE_NAME: String = "display.json"

        /** The preset names [fromSettings] understands, for error messages and docs. */
        val PRESETS: List<String> = listOf("wlr-randr", "vcgencmd", "xset", "ddcutil", "macos")

        /** Used by the `wlr-randr` preset when no output is given: the Raspberry Pi's first HDMI port. */
        const val DEFAULT_OUTPUT: String = "HDMI-A-1"

        private val json = Json { ignoreUnknownKeys = true }

        /**
         * Reads `display.json`. A missing file means "no display control" (null).
         *
         * Throws [IllegalStateException] with a readable message if the file is broken, so the person who
         * wrote it finds out (in the log) instead of display control silently not working.
         */
        fun load(file: Path, fileSystem: FileSystem = SystemFileSystem): DisplayCommands? {
            if (!fileSystem.exists(file)) return null
            val text = fileSystem.source(file).buffered().use { source -> source.readString() }
            val settings = try {
                json.decodeFromString(DisplaySettings.serializer(), text)
            } catch (error: SerializationException) {
                throw IllegalStateException("$file is not valid: ${error.message}", error)
            }
            return fromSettings(settings)
        }

        /**
         * Turns the file's contents into commands: a preset's commands, with `on` / `off` replacing them
         * when given. Without a preset, both `on` and `off` are needed.
         */
        fun fromSettings(settings: DisplaySettings): DisplayCommands {
            val preset = settings.preset
            if (preset == null) {
                val on = settings.on
                val off = settings.off
                check(!on.isNullOrBlank() && !off.isNullOrBlank()) {
                    "display.json needs a \"preset\" (one of ${PRESETS.joinToString()}) or both \"on\" and \"off\" commands"
                }
                return DisplayCommands(on = on, off = off)
            }

            val fromPreset = presetCommands(preset, settings.output ?: DEFAULT_OUTPUT)
            return DisplayCommands(
                on = settings.on ?: fromPreset.on,
                off = settings.off ?: fromPreset.off,
            )
        }

        private fun presetCommands(preset: String, output: String): DisplayCommands = when (preset) {
            // Wayland compositors (Raspberry Pi OS desktop, cage, sway...): switch the output itself off.
            "wlr-randr" -> DisplayCommands(
                on = "wlr-randr --output $output --on",
                off = "wlr-randr --output $output --off",
            )
            // Older Raspberry Pi OS (the legacy firmware graphics driver).
            "vcgencmd" -> DisplayCommands(
                on = "vcgencmd display_power 1",
                off = "vcgencmd display_power 0",
            )
            // X11 desktops: the monitor's power saving.
            "xset" -> DisplayCommands(
                on = "xset dpms force on",
                off = "xset dpms force off",
            )
            // Any monitor that speaks DDC/CI over its cable. VCP code D6 is "power mode": 01 = on, 04 = off.
            "ddcutil" -> DisplayCommands(
                on = "ddcutil setvcp d6 01",
                off = "ddcutil setvcp d6 04",
            )
            // macOS: put the displays to sleep; caffeinate -u wakes them like a key press would.
            "macos" -> DisplayCommands(
                on = "caffeinate -u -t 2",
                off = "pmset displaysleepnow",
            )
            else -> throw IllegalStateException("Unknown display preset \"$preset\". Use one of ${PRESETS.joinToString()}, or give \"on\" and \"off\" commands")
        }
    }
}

/**
 * The contents of `<config>/display.json`.
 *
 * Kept out of config.json on purpose: config.json can be changed over the network by a paired companion,
 * and these are commands the device runs. Only someone who can edit files on the device can set them.
 *
 * Examples:
 * ```json
 * { "preset": "wlr-randr", "output": "HDMI-A-2" }
 * { "on": "/usr/local/bin/screen on", "off": "/usr/local/bin/screen off" }
 * ```
 *
 * @property preset a ready-made pair of commands (see [DisplayCommands.PRESETS]).
 * @property output the screen the `wlr-randr` preset switches (run `wlr-randr` to list them).
 * @property on overrides (or, without a preset, sets) the command that turns the screen on.
 * @property off overrides (or, without a preset, sets) the command that turns the screen off.
 */
@Serializable
data class DisplaySettings(
    val preset: String? = null,
    val output: String? = null,
    val on: String? = null,
    val off: String? = null,
)
