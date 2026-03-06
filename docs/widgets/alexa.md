# Alexa Widget

Display information from Amazon Alexa devices including now-playing media, alarms, timers, and notes.

## Details

| Property | Value |
|----------|-------|
| **Type** | `widget` |
| **Allowed Locations** | `sidebar` |
| **Widget Name** | `alexa` |

## Configuration

Add `"alexa"` to `layout.sidebar` and configure it under `widgets.alexa`:

```json
{
  "layout": {
    "sidebar": ["calendar", "alexa", "updater"]
  },
  "widgets": {
    "alexa": {
      "userId": "your-alexa-user-id",
      "token": "your-alexa-token",
      "cookies": {},
      "devices": ["device-serial-1"],
      "radioProviders": [],
      "noteColumns": 3,
      "features": {
        "nowplaying": true,
        "alarms": true,
        "timers": true,
        "notes": false
      }
    }
  }
}
```

## Options Reference

| Field | Type | Default | Description |
|-------|------|---------|-------------|
| `userId` | string | `""` | Alexa user ID |
| `token` | string | `""` | Alexa API token |
| `cookies` | object | `{}` | Key-value cookie pairs for authentication |
| `devices` | string[] | `[]` | List of device serial numbers to monitor |
| `radioProviders` | string[] | `[]` | List of radio provider names to show |
| `noteColumns` | number | `3` | Number of columns when displaying notes |
| `features.nowplaying` | boolean | `true` | Show currently-playing media |
| `features.alarms` | boolean | `true` | Show active alarms |
| `features.timers` | boolean | `true` | Show running timers |
| `features.notes` | boolean | `false` | Show Alexa notes/sticky notes |
