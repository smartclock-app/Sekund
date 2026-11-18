# plugins/homeassistant

The **Home Assistant cameras** widget (`homeassistant`): while a sensor is `on` (a doorbell, a motion
sensor...), its camera is shown full screen as an overlay. It disappears `showCameraFor` (default 5 s) after
the sensor turns off, or straight away when tapped. The widget draws nothing in its layout slot; problems
(no account, can't connect, token refused, unknown sensor) appear as an alert at the top of the sidebar.

| File | What it does |
|---|---|
| `HomeAssistantPlugin.kt` | Registers the widget. Needs a `homeassistant` account. |
| `HomeAssistantSettings.kt` | Account, cameras (camera, trigger sensor, optional stream address, shape), how long to keep showing. |
| `HaProtocol.kt` | Pure: the WebSocket conversation (auth, `subscribe_trigger`, events), with no networking. |
| `HomeAssistantConnection.kt` | Keeps the WebSocket open, reconnects with backoff, shows alerts. |
| `ActiveCameras.kt` | Pure-ish: which cameras are up as sensors change, including the delay before hiding. |
| `CameraOverlay.kt`, `HomeAssistantWidget.kt` | The overlay (cameras side by side) and the wiring. |

**Cameras** play through Home Assistant's MJPEG proxy (`/api/camera_proxy_stream/<camera>`), signed in with
the account; this works on every platform without extra libraries. A camera's optional *stream address*
replaces it with another MJPEG stream (e.g. go2rtc); if that address isn't on the Home Assistant server,
the account's token isn't sent to it.

**The token**: Home Assistant's WebSocket wants the token in its first message rather than on an HTTP
header, so this plugin reads it with `context.auth.connectionToken`. That only works because
`homeassistant.auth.json` opts in with `"connectionToken": true`.
