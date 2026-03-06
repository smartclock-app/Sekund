# Home Assistant Widget

Display camera streams and react to Home Assistant entity triggers.

## Details

| Property | Value |
|----------|-------|
| **Type** | `widget` |
| **Allowed Locations** | `sidebar` |
| **Widget Name** | `homeassistant` |

## Configuration

Add `"homeassistant"` to `layout.sidebar` and configure it under `widgets.homeassistant`:

```json
{
  "layout": {
    "sidebar": ["calendar", "homeassistant", "updater"]
  },
  "widgets": {
    "homeassistant": {
      "url": "http://homeassistant.local:8123",
      "token": "your-long-lived-access-token",
      "cameraWaitTime": 5,
      "cameras": [
        {
          "id": "camera.front_door",
          "trigger": "binary_sensor.front_door_motion",
          "streamUri": "rtsp://user:pass@192.168.1.10:554/stream",
          "aspectRatio": 1.7777777777777777
        }
      ]
    }
  }
}
```

## Options Reference

| Field | Type | Default | Description |
|-------|------|---------|-------------|
| `url` | URL or `""` | `""` | Base URL of the Home Assistant instance |
| `token` | string | `""` | Long-lived access token |
| `cameraWaitTime` | number | `5` | Seconds to display a camera feed after it is triggered |
| `cameras` | array | `[]` | List of camera definitions |
| `cameras[].id` | string | `""` | Home Assistant entity ID for the camera |
| `cameras[].trigger` | string | `""` | Entity ID that triggers this camera to appear |
| `cameras[].streamUri` | URL or `""` | `""` | RTSP or other stream URI |
| `cameras[].aspectRatio` | number | `1.7778` (16/9) | Aspect ratio of the camera feed |
