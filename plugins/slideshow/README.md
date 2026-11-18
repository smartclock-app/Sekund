# plugins/slideshow

The **slideshow clock face** (`slideshow`): photos from photo sources filling the main square, with the
time and date along the bottom. Photo sources are a separate extension point; the built-in one is the
Immich album definition in `plugins/declarative-builtins` (`immich.photos.json`).

| File | What it does |
|---|---|
| `SlideshowPlugin.kt` | Registers the clock face. |
| `SlideshowSettings.kt` | Sources, how long each photo shows, keep photos offline, shuffle. |
| `Playlist.kt` | Pure: the order photos are shown in, carrying on from the current photo when sources refresh. |
| `SlideshowPlayer.kt` | No UI: follows the sources, picks and downloads the next photo on a timer or on "skip", skips broken photos, and manages the offline copies. |
| `SlideshowFace.kt` | Draws it: crossfading photos, time and date on a gradient. Offers the `skip` action (`POST /v1/actions/<id>.skip`). |

Photos are downloaded by their *source* (`PhotoSource.download`), because only the source holds the account
the photo server wants. Offline copies live in the instance's file cache (`<cache>/instances/<id>/`).
