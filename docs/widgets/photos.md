# Photos Widget (Clock Theme)

Replace the default clock face with a full-screen photo slideshow. Photos can be sourced from an [Immich](https://immich.app/) instance or from a static list of URLs.

## Details

| Property | Value |
|----------|-------|
| **Type** | `clockTheme` |
| **Widget Name** | `photos` |

## Activation

Set `clockTheme` to `"photos"` in the top-level config:

```json
{
  "clockTheme": "photos",
  "widgets": {
    "photos": {
      "immichUrl": "https://immich.example.com",
      "immichAccessToken": "your-api-key",
      "immichAlbumId": "album-uuid",
      "refreshInterval": 2
    }
  }
}
```

## Options Reference

| Field | Type | Default | Description |
|-------|------|---------|-------------|
| `refreshInterval` | number (≥1) | `1` | Multiple of the 30-second photo-change interval. `1` = every 30 s, `2` = every 60 s, etc. |
| `immichUrl` | URL or `""` | `""` | Base URL of the Immich instance |
| `immichAccessToken` | string | `""` | Immich API key / access token |
| `immichAlbumId` | string | `""` | UUID of the Immich album to display |
| `immichShareKey` | string | `""` | Immich shared-link key (alternative to `immichAlbumId`) |
| `useStaticLinks` | boolean | `false` | Use the `images` array instead of fetching from Immich |
| `images` | (URL or `""`)[] | `[]` | Static image URLs used when `useStaticLinks` is `true` |

## Remote Config Commands

The `photos` widget registers an additional remote-config command:

| Command | Description |
|---------|-------------|
| `skip_photo` | Immediately advances to the next photo |

See [Remote Config](../configuration.md#remote-config-api) for how to send commands.
