# plugins/google

Google services. For now one contribution: **Google Calendar** (`google-calendar`), a calendar source that
the calendar widget can show.

| File | What it does |
|---|---|
| `GooglePlugin.kt` | Registers the source and declares the `google` sign-in (calendar.readonly scope). |
| `GoogleCalendarSettings.kt` | Account, which calendars (by name; empty = all), events per calendar, show repeats once. |
| `GoogleCalendarApi.kt` | The few Calendar API v3 calls used, with typed answers. |
| `GoogleEventMapper.kt` | Pure conversion to `CalendarEvent`: time zones, all-day events, colours, repeats. |
| `GoogleCalendarSource.kt` | Polls every 5 minutes, fetching all calendars in parallel. |

Sign-in, token refresh and saving new tokens are done by the auth registry (`google.auth.json`), not here.
Push updates (Google watch channels relayed by the server) arrive with the server in phase 9; until then
this polls.
