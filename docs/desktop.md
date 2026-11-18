# Sekund on desktop and the Raspberry Pi

The same app runs on macOS, Windows and Linux, including a Raspberry Pi 4 or 5 (64-bit Raspberry Pi OS).
Each package carries its own Java runtime, so nothing else needs installing.

| Platform | Package | Build it on |
|---|---|---|
| Raspberry Pi / Linux arm64 | `sekund_<version>_arm64.deb` | arm64 Linux (a Pi, an ARM CI runner, or Docker on an Apple Silicon Mac) |
| Linux x64 | `sekund_<version>_amd64.deb` | x64 Linux |
| macOS | `Sekund-<version>.dmg` | macOS |
| Windows | `Sekund-<version>.msi` | Windows |

`jpackage` can only build for the OS and CPU it runs on, hence the last column.

## Options

| Option | Does |
|---|---|
| `--kiosk` | Start in kiosk mode (below). `--fullscreen` is the old name |
| `--data-dir=<folder>` | Keep every file in one folder (the `SEKUND_HOME` environment variable does the same) |

## Kiosk mode

The window fills the screen with no frame and stays above other windows. Long-press → **Exit kiosk** turns it
back into an ordinary window, and **Enter kiosk** goes back. The companion can do the same with `kiosk.exit` /
`kiosk.enter`. **Browser** leaves kiosk mode first, so the browser isn't hidden behind Sekund.

This doesn't stop someone with a keyboard from switching apps. For a display that only ever shows Sekund, run it
in `cage` on the Raspberry Pi (below).

## Turning the screen on and off

Create `display.json` in the config folder (see [Files](#files)) to get the `display.on` / `display.off`
actions. Pick a preset:

```json
{ "preset": "wlr-randr", "output": "HDMI-A-1" }
```

| Preset | For | Commands |
|---|---|---|
| `wlr-randr` | Wayland: Raspberry Pi OS desktop (labwc, wayfire), sway, cage | `wlr-randr --output <output> --off` / `--on`. Run `wlr-randr` to see output names. The default is `HDMI-A-1` |
| `vcgencmd` | Older Raspberry Pi OS with the legacy (non-KMS) graphics driver | `vcgencmd display_power 0` / `1` |
| `xset` | X11 desktops | `xset dpms force off` / `on` |
| `ddcutil` | Monitors that accept DDC/CI commands over the cable, on any Linux | `ddcutil setvcp d6 04` / `01`. Some monitors won't wake up from this; test it first |
| `macos` | macOS | `pmset displaysleepnow` / `caffeinate -u -t 2` |

Or give your own commands, run by `sh -c` (`cmd /c` on Windows). `on` or `off` next to a preset replaces just
that one:

```json
{ "on": "/home/sekund/screen.sh on", "off": "/home/sekund/screen.sh off" }
```

Notes:

- The file is read when Sekund starts; restart it after a change. A broken file is reported in the log, and
  Sekund starts without display control.
- The commands are deliberately **not** in `config.json`: a paired companion can change `config.json` over the
  network, and it shouldn't be able to choose commands the device runs.
- Sekund can't ask every screen whether it's on, so it assumes the screen is as it last set it.
- A command that fails (or takes more than 10 seconds) makes the action fail with the command's output, so the
  companion shows why.
- A plugin that reports someone nearby (`PresenceDetected`) turns the screen back on.

## Raspberry Pi set-up

This turns a Pi into a dedicated display: it boots straight into Sekund with no desktop.

1. Flash **Raspberry Pi OS Lite (64-bit)** with Raspberry Pi Imager. Set up Wi-Fi and SSH in the Imager.
2. Install Sekund and `cage`:

   ```sh
   sudo apt install ./sekund_1.0.0_arm64.deb cage
   ```

   Sekund lands in `/opt/sekund` (the program is `/opt/sekund/bin/Sekund`).
3. Create a user for it, allowed to use the screen and input devices:

   ```sh
   sudo useradd --create-home --groups video,render,input sekund
   ```

4. Install and start the service from [`packaging/linux/sekund-kiosk.service`](../packaging/linux/sekund-kiosk.service):

   ```sh
   sudo cp sekund-kiosk.service /etc/systemd/system/
   sudo systemctl enable --now sekund-kiosk
   ```

   It runs `cage -- /opt/sekund/bin/Sekund --kiosk` on the first console, instead of the login prompt, and
   restarts Sekund if it stops (including **Quit** from the menu).
5. For display control, put a `display.json` in `/home/sekund/.config/sekund/`. Under cage, try
   `{ "preset": "wlr-randr" }` first: newer versions of cage support it (check that `wlr-randr` lists the
   output when run inside cage). Otherwise use `ddcutil` if the screen supports it.

Logs: `journalctl -u sekund-kiosk -f`, plus Sekund's own log in `/home/sekund/.local/state/sekund/logs`.
Ctrl+Alt+F2 switches to a text console if a keyboard is attached. With no mouse plugged in, there's no pointer.

### On the Raspberry Pi OS desktop instead

To keep the desktop, start Sekund when you log in: create `~/.config/autostart/sekund.desktop` with

```ini
[Desktop Entry]
Type=Application
Name=Sekund
Exec=/opt/sekund/bin/Sekund --kiosk
```

## Files

| OS | Config (`config.json`, `display.json`, widgets) | Data | Logs |
|---|---|---|---|
| Linux | `~/.config/sekund` | `~/.local/share/sekund` | `~/.local/state/sekund/logs` |
| macOS | `~/Library/Application Support/Sekund` | same folder, `data/` | `~/Library/Logs/Sekund` |
| Windows | `%APPDATA%\Sekund` | `%LOCALAPPDATA%\Sekund\data` | `%LOCALAPPDATA%\Sekund\logs` |

The secrets key is kept in the OS keychain, or in an owner-only file in the data folder where there is none
(e.g. on a Pi with no desktop).

## Network

There's no system view of the network to follow on desktop, so Sekund checks `connectivitycheck.gstatic.com`
itself, once a minute, and more often while it's offline. The **Offline** badge shows while it fails.

## Updates

A verified update is opened with the system, which shows its installer (see [updates.md](updates.md)). On a Pi
running cage there's nothing to open a `.deb` with, so for now install updates over SSH with
`sudo apt install ./sekund_<version>_arm64.deb` and `sudo systemctl restart sekund-kiosk`.

## Building the packages

Each release has the Linux packages ready-made (`sekund_<version>_arm64.deb` and `_amd64.deb`, see
[releasing.md](releasing.md)). macOS and Windows packages aren't released; build them yourself:

```sh
./gradlew :app:desktopApp:packageDeb       # on Linux: the .deb for this machine's CPU
./gradlew :app:desktopApp:packageDmg       # on macOS
./gradlew :app:desktopApp:packageMsi       # on Windows
./gradlew :app:desktopApp:runDistributable # try the packaged app without installing it
```

The version comes from `sekund.version` in `gradle.properties`. On Linux the build needs `fakeroot` and
`binutils` (`sudo apt install fakeroot binutils`).

To build the arm64 `.deb` on an Apple Silicon Mac with Docker, from a copy of the repo (so the Mac's own build
folders aren't overwritten):

```sh
docker run --rm -v "$PWD":/src -v ~/Library/Android/sdk:/android-sdk:ro -e ANDROID_HOME=/android-sdk -w /src \
  eclipse-temurin:21-jdk bash -c \
  'apt-get update && apt-get install -y fakeroot binutils && ./gradlew --no-daemon :app:desktopApp:packageDeb'
```

The Android SDK is only mounted because Gradle reads it while configuring the Android modules.

`jpackage` copies the package's dependencies (`libpng16-16t64`, ...) from the system it builds on, and their names
differ between Debian releases. Build on the same release the Pi runs: `eclipse-temurin:21-jdk` (Ubuntu 24.04)
gives the names Raspberry Pi OS 13 (trixie) uses. For the older bookworm, build on a Debian 12 image.
The package ends up in `app/desktopApp/build/compose/binaries/main/deb/`.
