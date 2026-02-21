# Trakt Calendar Extension

Fetch upcoming show and movie releases from [Trakt](https://trakt.tv/) and display them in the built-in calendar widget.

## Details

| Property | Value |
|----------|-------|
| **Type** | `calendarExtension` |
| **Widget Name** | `trakt` |

## Activation

Calendar extensions are not placed in `layout`. Add `"trakt"` to `calendar.extensions`:

```json
{
  "calendar": {
    "extensions": ["trakt"]
  },
  "widgets": {
    "trakt": {
      "auth": {
        "clientId": "your-client-id",
        "clientSecret": "your-client-secret",
        "accessToken": "your-access-token",
        "refreshToken": "your-refresh-token",
        "redirectUri": "urn:ietf:wg:oauth:2.0:oob"
      },
      "listId": "",
      "includeWatchlist": false,
      "includeEpisodesAsShow": false,
      "prefix": "",
      "color": "#FFF5511D",
      "maxItems": 50
    }
  }
}
```

## Options Reference

| Field | Type | Default | Description |
|-------|------|---------|-------------|
| `auth.clientId` | string | `""` | Trakt API client ID |
| `auth.clientSecret` | string | `""` | Trakt API client secret |
| `auth.accessToken` | string | `""` | OAuth access token |
| `auth.refreshToken` | string | `""` | OAuth refresh token |
| `auth.redirectUri` | URL or `""` | `""` | OAuth redirect URI |
| `listId` | string | `""` | Trakt list slug/ID to pull items from |
| `includeWatchlist` | boolean | `false` | Include items from your Trakt watchlist |
| `includeEpisodesAsShow` | boolean | `false` | Group episode entries under the parent show |
| `prefix` | string | `""` | String prepended to each calendar event title |
| `color` | string | `"#FFF5511D"` | RGBA hex color for calendar event chips |
| `maxItems` | number | `50` | Maximum number of items to fetch |

## Setup Notes

To obtain the required credentials:

1. Go to [https://trakt.tv/oauth/applications](https://trakt.tv/oauth/applications) and create a new API application.
2. Copy the **Client ID** and **Client Secret** into `auth.clientId` and `auth.clientSecret`.
3. Set `auth.redirectUri` to `urn:ietf:wg:oauth:2.0:oob` for a device/PIN flow, or to a valid redirect URI if you use the browser flow.
4. Exchange an authorisation code for an `accessToken` and `refreshToken` using the [Trakt OAuth flow](https://trakt.docs.apiary.io/#reference/authentication-oauth).
5. Paste the tokens into the widget config.

The widget will automatically refresh the `accessToken` using the `refreshToken` when it expires.
