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
| `GoogleCalendarPush.kt` | With a Sekund server that relays webhooks: Google watch channels on `context.webhooks`, renewed an hour before they expire (at most a week); a change notification refreshes straight away. |

Sign-in, token refresh and saving new tokens are done by the auth registry (`google.auth.json`), not here. With
a server that has a Google OAuth client, the account can also be signed in through the server (see docs/server.md).

Push refreshes the whole source rather than syncing incrementally with `syncToken`: simpler, and a refresh is a
handful of requests. Without a server (or if a notification is lost) polling carries on as before.
