# plugins/trakt

**Trakt** (`trakt`) as a calendar source: when the shows and films on a Trakt list and/or the watchlist
next air.

| File | What it does |
|---|---|
| `TraktPlugin.kt` | Registers the source; declares the `trakt` sign-in (device code, see `trakt.auth.json`). |
| `TraktSettings.kt` | Account, list, watchlist, episodes-as-show, first line (prefix), colour, max dates. Same keys as the legacy importer. |
| `TraktApi.kt` | The Trakt calls used, with typed answers. |
| `TraktItems.kt` | Which list entries are followed; the cached details (`CachedItem`). |
| `TraktEventBuilder.kt` | Pure: cached items → calendar events (exact time within 24 h, otherwise all-day; same day = one event). |
| `TraktCalendarSource.kt` | Re-reads the list every 30 min; refetches an item's details only when they're a day old; falls back to the cache when Trakt can't be reached. |

Changes from the old app: details are refreshed daily (they used to be fetched only when the list changed,
so air dates went stale), sign-in and token refresh are the auth registry's job, and the cache is a
readable JSON file in the instance's storage instead of an SQLite table.
