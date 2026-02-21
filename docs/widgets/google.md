# Google Calendar Extension

Fetch events from Google Calendar and display them in the built-in calendar widget.

## Details

| Property | Value |
|----------|-------|
| **Type** | `calendarExtension` |
| **Widget Name** | `google` |

## Activation

Calendar extensions are not placed in `layout`. Add `"google"` to `calendar.extensions`:

```json
{
  "calendar": {
    "extensions": ["google"]
  },
  "widgets": {
    "google": {
      "clientId": "your-client-id.apps.googleusercontent.com",
      "clientSecret": "your-client-secret",
      "accessToken": "ya29...",
      "refreshToken": "1//...",
      "tokenExpiry": "2024-01-01T00:00:00.000Z"
    }
  }
}
```

## Options Reference

| Field | Type | Default | Description |
|-------|------|---------|-------------|
| `clientId` | string | `""` | Google OAuth 2.0 client ID |
| `clientSecret` | string | `""` | Google OAuth 2.0 client secret |
| `accessToken` | string | `""` | OAuth access token |
| `refreshToken` | string | `""` | OAuth refresh token |
| `tokenExpiry` | ISO 8601 datetime | current time | Expiry time of the access token |

## Setup Notes

To obtain the required credentials:

1. Go to the [Google Cloud Console](https://console.cloud.google.com/) and create a project.
2. Enable the **Google Calendar API** for that project.
3. Create an **OAuth 2.0 Client ID** (Desktop application type) and note the `clientId` and `clientSecret`.
4. Run an OAuth flow (e.g. using the [OAuth Playground](https://developers.google.com/oauthplayground/)) requesting the `https://www.googleapis.com/auth/calendar.readonly` scope to obtain an `accessToken` and `refreshToken`.
5. Paste the tokens and their expiry into the widget config.

The widget will automatically refresh the `accessToken` using the `refreshToken` when it expires.
