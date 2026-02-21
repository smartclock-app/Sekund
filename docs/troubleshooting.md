# Troubleshooting

## App won't start / blank screen

- Check the app log at `<AppData>/Smart Clock.log` (or via the in-app editor log tab).
- Ensure all Tauri system dependencies are installed for your OS. See [Development Setup](development/setup.md).
- Delete `config.json` and restart. The app will recreate it with defaults.

## Config changes have no effect

- If you edited `config.json` manually (outside the in-app editor), you must **restart the app** for changes to take effect.
- Changes made through the in-app editor are applied immediately without a restart.
- Changes sent via the Remote Config API (`set_config` command) trigger an automatic reload.

## In-app editor shows validation errors

The editor validates `config.json` against the Zod schema. Common causes:

- An unknown widget name in `layout.main` or `layout.sidebar` (widget not loaded, or typo).
- An invalid enum value (e.g. `"orientation": "auto"` instead of `"landscape"` or `"portrait"`).
- A required field missing in a widget config object. Add it with the default value shown in the widget documentation.

## A widget is not appearing

1. Confirm the widget name is listed in `layout.main` or `layout.sidebar`.
2. Confirm the widget supports the zone you placed it in (see [Widgets](widgets/index.md)).
3. Check the app log for a message like `Error loading widget at /src/widgets/<name>/index.ts: …`. The widget may have failed to load due to a missing export.
4. Calendar extensions must be listed in `calendar.extensions`, not in `layout`.

## Remote Config API not reachable

- Ensure `remoteConfig.enabled` is `true`.
- Confirm the port (`remoteConfig.port`, default `8080`) is not blocked by a firewall.
- If `remoteConfig.useBonjour` is `true`, the service will be advertised on the local network as `_http._tcp` under the name set in `remoteConfig.bonjourName`.
- Test with: `curl http://<device-ip>:8080/api/command -d '{"command":"refresh","data":null}'`

## CSS variables not updating

- Edit `variables.css` in the AppData directory (or use the in-app editor's Variables tab), **not** `variables.template.css`.
- `variables.template.css` is overwritten on every app startup.
- A full restart is required when editing `variables.css` outside the in-app editor.

## App is not receiving updates

- The `updater` widget must be present in `layout.sidebar`.
- Updates are fetched from `https://github.com/0x5045414b/ClockBeta/releases/latest/download/latest.json`.
- On Android, automatic updates are not supported.

## Finding the log file

| OS | Log path |
|----|----------|
| macOS | `~/Library/Logs/uk.co.danpeak.smartclock.clock/Smart Clock.log` |
| Linux | `~/.local/share/uk.co.danpeak.smartclock.clock/logs/Smart Clock.log` |
| Windows | `%APPDATA%\uk.co.danpeak.smartclock.clock\logs\Smart Clock.log` |

The last 100 lines of the log are also accessible via the Remote Config API (`get_logs` command) and in the in-app editor log tab.
