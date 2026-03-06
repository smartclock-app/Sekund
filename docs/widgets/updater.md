# Updater Widget

Check for updates from GitHub Releases.

## Details

| Property | Value |
|----------|-------|
| **Type** | `widget` |
| **Allowed Locations** | `sidebar` |
| **Widget Name** | `updater` |

## Configuration

`updater` is included in the default sidebar layout. Add it to `layout.sidebar` to enable it:

```json
{
  "layout": {
    "sidebar": ["calendar", "updater"]
  },
  "widgets": {
    "updater": {
      "updateInterval": 5
    }
  }
}
```

## Options Reference

| Field | Type | Default | Description |
|-------|------|---------|-------------|
| `updateInterval` | number | `5` | How often (in minutes) to check for updates |

## Notes

- Updates are distributed via GitHub Releases using the Tauri updater plugin.
- The update endpoint is configured in `src-tauri/tauri.conf.json` and points to `https://github.com/0x5045414b/ClockBeta/releases/latest/download/latest.json`.
- This widget is currently only available on Android builds (the Tauri updater plugin is planned to be used for desktop targets).
