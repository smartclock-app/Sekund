# Configuration

Sekund is configured via a single JSON file (`config.json`) stored in the AppData directory. The schema is validated by [Zod](https://zod.dev/) on every startup, and any missing or invalid fields are silently replaced with their defaults.

> [!WARNING]  
> Only primitive values will be silently replaced. Any object or array will throw an error and that widget will fail to load.

## Config File Location

The config directory lives under the Tauri AppData base directory for the identifier `uk.dnpk.sekund`:

| OS            | Path                                                     |
| ------------- | -------------------------------------------------------- |
| macOS         | `~/Library/Application Support/uk.dnpk.sekund/`          |
| Linux         | `~/.local/share/uk.dnpk.sekund/`                         |
| Windows       | `%APPDATA%\uk.dnpk.sekund\`                              |
| Android / iOS | Cannot be edited directly and must use the in-app editor |

## Editing the Config

### In-app editor

Long-press (or right-click) anywhere on the clock face to open the built-in Monaco editor. Changes to `config.json` are validated against the Zod schema in real time. Saving a valid config applies changes immediately without restarting the app.

### Manual edit

Edit `config.json` directly in the AppData directory. A restart is required for manually-edited changes to take effect.

### Remote Config API

If `remoteConfig.enabled` is `true` the app exposes an HTTP server on `remoteConfig.port` (default `8080`). Send a POST to `/api/command` with a JSON body:

```json
{ "command": "<command>", "data": <payload> }
```

Built-in commands:

| Command         | Description                                            |
| --------------- | ------------------------------------------------------ |
| `get_config`    | Returns the current `config.json` as JSON              |
| `set_config`    | Replaces config with `data` and triggers a page reload |
| `get_variables` | Returns the current `variables.css` content            |
| `set_variables` | Replaces `variables.css` with `data` and reloads       |
| `get_logs`      | Returns the last 100 lines of the app log              |
| `refresh`       | Triggers a page reload                                 |
| `display_on`    | Turns the display on (Android only)                     |
| `display_off`   | Turns the display off (Android only)                    |
| `get_display_status` | Returns `{ adminActive: boolean }` (Android only)  |

Additional commands can be registered by widgets via `OnInit` (see [Adding Widgets](development/adding-widgets.md)).

#### Display Control (Android)

`display_on` and `display_off` are currently implemented for Android only. `display_off` turns the screen off via Android's Device Admin `lockNow()` API — the same effect as a single press of the physical power button — and `display_on` wakes it back up with a forced wake lock.

Turning the display off requires Sekund to be granted the **device admin** permission once, on the device itself. Since this only ever needs doing once per device, the app checks on every startup and, if not yet granted, automatically opens the system "Activate this device admin app?" prompt — just confirm it the first time the app runs on a given device. This cannot be granted remotely, so if the prompt is dismissed instead of confirmed, it opens again on the next launch/reload; the long-press menu's **Enable Display Control** button can also re-trigger it manually at any time. `get_display_status` can be polled to check whether it's currently active. Until it's granted, `display_off` returns an error explaining that the permission is missing.

For the smoothest remote experience, don't set a PIN/pattern/password lock on the device — with no secure lock configured, `display_on` wakes straight back to the Sekund clock face instead of a lock screen.

#### Kiosk Mode / Device Owner (Android)

If a device is dedicated entirely to Sekund, it can be provisioned as Android's **Device Owner** rather than just a plain Device Admin. Device Owner unlocks a few things beyond the display control above:

- Sekund's Device Admin permission (used for `display_off`) is active automatically — no on-device prompt at all, since a device owner's admin receiver is always active.
- The screen can be pinned to just Sekund plus Settings and whatever browser(s) are installed, via Android's Lock Task API — no home button, recents, or way to reach any other app.
- App updates (via the updater widget) install silently through a `PackageInstaller` session instead of the "Install unknown app?" confirmation dialog, so a device owner can update fully unattended. On a non-device-owner install, the same update flow falls back to the normal `ACTION_INSTALL_PACKAGE` confirmation dialog.
- Android's own adaptive brightness (`Settings.System.SCREEN_BRIGHTNESS_MODE` set to automatic) is turned on, so the screen dims/brightens using the device's ambient light sensor and Android's own tuned curve — no custom sensor code needed. This call itself doesn't check for a light sensor; on hardware without one it's simply a no-op as far as brightness changes go.
- Sekund silently registers itself as the permanent default Home app via `DevicePolicyManager.addPersistentPreferredActivity()` — no "Complete action using" chooser, and no manual "set as launcher" step in Settings either.
- The lock screen is disabled entirely via `DevicePolicyManager.setKeyguardDisabled()`, so waking the display always lands directly on the clock face.
- Automatic time and timezone (`Settings.Global.AUTO_TIME`/`AUTO_TIME_ZONE`) are turned on via `DevicePolicyManager.setGlobalSetting()`, so the clock stays correct without anyone touching Settings.

The status bar (notification shade / quick settings) is deliberately **not** disabled, even though Device Owner supports it (`setStatusBarDisabled`) and Android documents it as the standard kiosk-device setting — this is a conscious choice so Quick Settings (and USB/wireless debugging within it) stays reachable without going through the full Settings app.

Device Owner can **only** be granted via one `adb` command run before any account exists on the device (or after a factory reset — it cannot be granted through a Settings screen or after sign-in):

```sh
adb shell dpm set-device-owner uk.dnpk.sekund/.SekundDeviceAdminReceiver
```

Once that's done, Sekund detects device owner status automatically on every startup and applies all of the above. Nothing else needs configuring — the browser package(s) to whitelist are resolved at runtime from whatever's installed, and Settings (`com.android.settings`) is always included. The long-press menu gains two extra items on Android: **Settings** (opens the system Settings app) and **Exit Kiosk Mode** (calls `disable_kiosk_mode`, useful for maintenance — kiosk mode re-engages on the next reload/restart since it's re-checked every startup).

A device with Device Owner set up this way needs essentially zero manual configuration beyond the one `adb` command above — the app installs, registers itself as the launcher, pins the screen, and configures brightness entirely on its own, which is the point: making the device feel like Sekund *is* its OS rather than an app running on top of one.

On a device that _isn't_ set up as device owner, all of the above is skipped silently and Sekund behaves exactly as described in the plain Device Admin section above — kiosk mode and silent installs are opportunistic, not required.

Kiosk mode blocks the Quick Settings pull-down shade, but not the full Settings app (it's one of the whitelisted packages) — so Developer Options, and USB/wireless debugging specifically, stay reachable by hand via the menu's **Settings** button. There's no in-app toggle for debugging: `adb`-related settings persist across reboots (they're stored in the settings database, not reset at boot), and debugging has to already be enabled to run the `dpm set-device-owner` command above in the first place, so there's nothing left to wire up.

There is currently no `power_off` command: Android provides no public API for a non-rooted app (even with device admin) to shut the device down, only to reboot it as a device-owner app. If you need true remote power control, external hardware (e.g. a smart plug) driven by your own automation is the only reliable option for now. Other platforms (Raspberry Pi, desktop) are not yet supported for display or power control.

## Schema & Backups

On every startup the app:

1. Checks whether a file named `schema-<version>.json` exists in the AppData directory.
2. If it does **not** exist (i.e. first run after an upgrade), it backs up the current `config.json` to `config-backups/config-<timestamp>.json`.  
   Up to **5** backups are retained; older ones are deleted automatically.
3. Writes/overwrites `schema-<version>.json` with a freshly-generated JSON Schema derived from the Zod schema.

## Top-level Config Reference

```jsonc
{
  "$schema": "schema-<version>.json",   // auto-maintained
  "orientation": "landscape",           // "portrait" | "landscape"
  "checkNetwork": true,                 // show offline indicator when true
  "remoteConfig": { ... },              // see Remote Config section below
  "clock": { ... },                     // see Clock section below
  "calendar": { ... },                  // see Calendar section below
  "clockTheme": "default",              // name of a ClockTheme widget, or "default"
  "widgets": { ... },                   // per-widget config objects, keyed by widget name
  "layout": {
    "main": [],                         // ordered list of widget names for the main area
    "sidebar": ["updater"]              // ordered list of widget names for the sidebar
  }
}
```

### `remoteConfig`

| Field               | Type    | Default    | Description                                                                                 |
| ------------------- | ------- | ---------- | ------------------------------------------------------------------------------------------- |
| `enabled`           | boolean | `true`     | Enable the HTTP remote-config server                                                        |
| `port`              | number  | `8080`     | Port the HTTP server listens on                                                             |
| `password`          | string  | `""`       | Password for the HTTP API (currently unused — authentication mechanism not yet implemented) |
| `useBonjour`        | boolean | `true`     | Advertise the server via mDNS/Bonjour                                                       |
| `bonjourName`       | string  | `"Sekund"` | mDNS service name                                                                           |
| `toggleDisplayPath` | string  | `""`       | Filesystem path of a script/command used to toggle the display on/off (currently unused)    |

### `clock`

| Field         | Type               | Default | Description     |
| ------------- | ------------------ | ------- | --------------- |
| `format`      | `"12h"` \| `"24h"` | `"12h"` | Time format     |
| `showSeconds` | boolean            | `true`  | Display seconds |

### `calendar`

| Field         | Type        | Default | Description                                |
| ------------- | ----------- | ------- | ------------------------------------------ |
| `maxEvents`   | number (≥1) | `50`    | Maximum number of events to display        |
| `titles.odd`  | string      | `""`    | Label shown on odd-numbered weeks          |
| `titles.even` | string      | `""`    | Label shown on even-numbered weeks         |
| `eventFilter` | string[]    | `[]`    | List of event title substrings to hide     |
| `extensions`  | string[]    | `[]`    | CalendarExtension widget names to activate |

### `widgets`

An object where each key is a widget name and the value is the widget-specific config object. Unknown keys are ignored and silently deleted; missing keys are filled with defaults. See individual [widget pages](widgets/index.md) for field references.

### `layout`

| Field     | Type     | Default       | Description                               |
| --------- | -------- | ------------- | ----------------------------------------- |
| `main`    | string[] | `[]`          | Ordered widget names for the main zone    |
| `sidebar` | string[] | `["updater"]` | Ordered widget names for the sidebar zone |

## CSS Variables

Sekund supports full CSS variable theming via two files in the AppData directory:

| File                     | Description                                                                                                                                                |
| ------------------------ | ---------------------------------------------------------------------------------------------------------------------------------------------------------- |
| `variables.template.css` | **Auto-generated** on every startup from each widget's `Variables.css` and the root `src/assets/variables.css`. Do not edit — it is overwritten each time. |
| `variables.css`          | Your customisations. Created automatically on first run. Edit this file to override any variable.                                                          |

Both files are loaded and injected into the page at startup. `variables.css` takes precedence because it is appended after the template.

The in-app editor's **Variables** tab lets you edit `variables.css` directly.

> [!NOTE]  
> `variables.css` is not updated when the template changes. Any new variables will have to be added manually to override the defaults.
> The in-app editor provides auto-complete for valid variable names.
