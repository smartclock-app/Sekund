package uk.dnpk.sekund.runtime.legacy

/** A config.json as the old Tauri app wrote it (every widget present, only some in use). */
const val LEGACY_CONFIG = """
{
  "${'$'}schema": "schema-0.9.0.json",
  "orientation": "portrait",
  "checkNetwork": true,
  "remoteConfig": { "enabled": false, "port": 8081, "password": "hunter2", "useBonjour": true, "bonjourName": "Kitchen", "toggleDisplayPath": "" },
  "clock": { "format": "24h", "showSeconds": false },
  "calendar": { "maxEvents": 30, "titles": { "odd": "Bins", "even": "Recycling" }, "eventFilter": ["^Private"], "extensions": ["google", "trakt"] },
  "widgets": {
    "weather": { "apiKey": "ow-key", "postcode": "LS1 4AP", "country": "GB", "units": "metric" },
    "google": { "clientId": "g-client", "clientSecret": "g-secret", "accessToken": "g-access", "refreshToken": "g-refresh", "tokenExpiry": "2025-03-03T10:00:00.000Z" },
    "trakt": { "auth": { "clientId": "t-client", "clientSecret": "t-secret", "accessToken": "t-access", "refreshToken": "t-refresh", "redirectUri": "" },
               "listId": "123", "includeWatchlist": true, "includeEpisodesAsShow": false, "prefix": "TV: ", "color": "#FFF5511D", "maxItems": 20 },
    "photos": { "refreshInterval": 2, "immichUrl": "http://immich.local:2283", "immichAccessToken": "im-key", "immichAlbumId": "album-1",
                "immichShareKey": "share", "useStaticLinks": false, "savePhotosToDisk": true, "images": [] },
    "actualbudget": { "actualHttpApiUrl": "", "budgets": [], "syncInterval": 60 },
    "homeassistant": { "url": "", "token": "", "cameras": [], "cameraWaitTime": 5 },
    "alexa": { "features": { "nowplaying": true, "alarms": true, "timers": true, "notes": false }, "userId": "", "token": "",
               "cookies": {}, "devices": [], "radioProviders": [], "noteColumns": 3 },
    "updater": { "updateInterval": 30 }
  },
  "clockTheme": "photos",
  "layout": { "main": ["weather"], "sidebar": ["calendar", "updater"] }
}
"""
