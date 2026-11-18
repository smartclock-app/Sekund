# plugins/alexa

The **Alexa** widget (`alexa`): what an Echo is playing (art, progress, synced lyrics), running timers and
alarms/reminders due in the next 12 hours. It also provides Amazon's sign-in code.

Amazon has no public API for this; it uses the Alexa website's own (unofficial) API, so it can break if
Amazon changes it.

| File | What it does |
|---|---|
| `AlexaPlugin.kt` | Registers the widget and the `amazon-cookie` sign-in handler. |
| `AmazonCookieHandler.kt` | Sign-in: refresh token → cookies + CSRF token; signs requests. Used by the auth registry (`amazon.auth.json`). |
| `AlexaSettings.kt` | Account, which parts to show, devices, extra radio services. |
| `AlexaApi.kt` | Devices, account id, media sessions, notifications, with typed answers. |
| `NowPlaying.kt` | Pure: picks the playing session; moves the progress on locally between fetches. |
| `Reminders.kt` | Pure: active timers and alarms due soon, on the followed devices. |
| `Lyrics.kt`, `LyricsLibrary.kt` | LRC parsing, and lookups on lrclib.net cached as files (including "none"). |
| `AlexaData.kt` | Loading: now playing every 10 s while playing / 2 min idle, timers and alarms every minute, lyrics per song. |
| `AlexaWidget.kt` | The cards. |

Connect the account under Accounts with an Amazon refresh token (`Atnr|...`) and the Amazon site
(e.g. `amazon.co.uk`). Not done yet: media buttons (pause/skip), and a full-screen overlay when a timer rings.
