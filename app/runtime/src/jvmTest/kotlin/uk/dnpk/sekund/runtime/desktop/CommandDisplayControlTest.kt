package uk.dnpk.sekund.runtime.desktop

import kotlinx.coroutines.test.runTest
import kotlinx.io.buffered
import kotlinx.io.files.Path
import kotlinx.io.files.SystemFileSystem
import kotlinx.io.writeString
import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class CommandDisplayControlTest {
    @Test
    fun `a preset gives its commands, and output picks the screen`() {
        val commands = DisplayCommands.fromSettings(DisplaySettings(preset = "wlr-randr", output = "HDMI-A-2"))
        assertEquals("wlr-randr --output HDMI-A-2 --off", commands.off)
        assertEquals("wlr-randr --output HDMI-A-2 --on", commands.on)
    }

    @Test
    fun `on and off replace the preset's commands one at a time`() {
        val commands = DisplayCommands.fromSettings(DisplaySettings(preset = "vcgencmd", on = "my-wake-script"))
        assertEquals("my-wake-script", commands.on)
        assertEquals("vcgencmd display_power 0", commands.off)
    }

    @Test
    fun `without a preset both commands are needed`() {
        val error = assertFailsWith<IllegalStateException> { DisplayCommands.fromSettings(DisplaySettings(off = "screen off")) }
        assertTrue(error.message!!.contains("\"on\" and \"off\""))
    }

    @Test
    fun `an unknown preset is an error that lists the real ones`() {
        val error = assertFailsWith<IllegalStateException> { DisplayCommands.fromSettings(DisplaySettings(preset = "hdmi")) }
        assertTrue(error.message!!.contains("wlr-randr"))
    }

    @Test
    fun `no display json means no display control`() {
        val folder = Path(Files.createTempDirectory("sekund-display").toString())
        assertNull(DisplayCommands.load(Path(folder, DisplayCommands.FILE_NAME)))
    }

    @Test
    fun `reads display json`() {
        val folder = Path(Files.createTempDirectory("sekund-display").toString())
        val file = Path(folder, DisplayCommands.FILE_NAME)
        SystemFileSystem.sink(file).buffered().use { sink -> sink.writeString("""{ "preset": "xset" }""") }
        assertEquals(DisplayCommands(on = "xset dpms force on", off = "xset dpms force off"), DisplayCommands.load(file))
    }

    @Test
    fun `runs the commands and remembers the state`() = runTest {
        val ran = mutableListOf<String>()
        val display = CommandDisplayControl(DisplayCommands(on = "screen on", off = "screen off")) { command ->
            ran.add(command)
            CommandResult(exitCode = 0, output = "")
        }
        assertTrue(display.isOn.value)

        display.turnOff()
        assertFalse(display.isOn.value)
        display.turnOn()
        assertTrue(display.isOn.value)
        assertEquals(listOf("screen off", "screen on"), ran)
    }

    @Test
    fun `a failing command is a readable error and leaves the state alone`() = runTest {
        val display = CommandDisplayControl(DisplayCommands(on = "screen on", off = "screen off")) { _ ->
            CommandResult(exitCode = 1, output = "no such output")
        }
        val error = assertFailsWith<IllegalStateException> { display.turnOff() }
        assertTrue(error.message!!.contains("no such output"))
        assertTrue(display.isOn.value)
    }

    @Test
    fun `the shell runner really runs commands`() = runTest {
        // `echo` works in both sh and cmd.
        val result = ShellCommandRunner().run("echo hello")
        assertTrue(result.succeeded)
        assertEquals("hello", result.output)
    }
}
