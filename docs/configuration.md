# Configuration

Smart Clock is configured via a single JSON file (`config.json`) stored in the AppData directory. The schema is validated by [Zod](https://zod.dev/) on every startup, and any missing or invalid fields are silently replaced with their defaults.

## Config File Location

The config directory lives under the Tauri AppData base directory for the identifier `uk.co.danpeak.smartclock.clock`:

| OS | Path |
|----|------|
| macOS | `~/Library/Application Support/uk.co.danpeak.smartclock.clock/` |
| Linux | `~/.local/share/uk.co.danpeak.smartclock.clock/` |
| Windows | `%APPDATA%\uk.co.danpeak.smartclock.clock\` |

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

| Command | Description |
|---------|-------------|
| `get_config` | Returns the current `config.json` as JSON |
| `set_config` | Replaces config with `data` and triggers a page reload |
| `get_variables` | Returns the current `variables.css` content |
| `set_variables` | Replaces `variables.css` with `data` and reloads |
| `get_logs` | Returns the last 100 lines of the app log |
| `refresh` | Triggers a page reload |

Additional commands can be registered by widgets via `OnInit` (see [Adding Widgets](development/adding-widgets.md)).

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
  "layout": {
    "main": [],                         // ordered list of widget names for the main area
    "sidebar": ["updater"]              // ordered list of widget names for the sidebar
  },
  "widgets": { ... }                    // per-widget config objects, keyed by widget name
}
```

### `remoteConfig`

| Field | Type | Default | Description |
|-------|------|---------|-------------|
| `enabled` | boolean | `true` | Enable the HTTP remote-config server |
| `port` | number | `8080` | Port the HTTP server listens on |
| `password` | string | `""` | Password for the HTTP API (currently unused — authentication mechanism not yet implemented) |
| `useBonjour` | boolean | `true` | Advertise the server via mDNS/Bonjour |
| `bonjourName` | string | `"Smart Clock"` | mDNS service name |
| `toggleDisplayPath` | string | `""` | Filesystem path of a script/command used to toggle the display on/off (currently unused) |

### `clock`

| Field | Type | Default | Description |
|-------|------|---------|-------------|
| `format` | `"12h"` \| `"24h"` | `"12h"` | Time format |
| `showSeconds` | boolean | `true` | Display seconds |

### `calendar`

| Field | Type | Default | Description |
|-------|------|---------|-------------|
| `maxEvents` | number (≥1) | `50` | Maximum number of events to display |
| `titles.odd` | string | `""` | Label shown on odd-numbered weeks |
| `titles.even` | string | `""` | Label shown on even-numbered weeks |
| `eventFilter` | string[] | `[]` | List of event title substrings to hide |
| `extensions` | string[] | `[]` | CalendarExtension widget names to activate |

### `layout`

| Field | Type | Default | Description |
|-------|------|---------|-------------|
| `main` | string[] | `[]` | Ordered widget names for the main zone |
| `sidebar` | string[] | `["updater"]` | Ordered widget names for the sidebar zone |

### `widgets`

An object where each key is a widget name and the value is the widget-specific config object. Unknown keys are ignored; missing keys are filled with defaults. See individual [widget pages](widgets/index.md) for field references.

## CSS Variables

Smart Clock supports full CSS variable theming via two files in the AppData directory:

| File | Description |
|------|-------------|
| `variables.template.css` | **Auto-generated** on every startup from each widget's `Variables.css` and the root `src/assets/variables.css`. Do not edit — it is overwritten each time. |
| `variables.css` | Your customisations. Created automatically with all variables commented out. Edit this file to override any variable. |

Both files are loaded and injected into the page at startup. `variables.css` takes precedence because it is appended after the template.

The in-app editor's **Variables** tab lets you edit `variables.css` directly.
