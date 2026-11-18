# Declarative widgets

A declarative widget is one JSON file that says: *call this API, pick these values out of the answer,
show them like this*. There is no code, so a definition can be added while Sekund is running:

- drop it in `<config>/widgets/` (see Settings → Files for the folder), or
- `PUT /v1/declarative-widgets/{id}` from the companion (see [the device API](../api/device-api.md)).

Each definition becomes a widget type like any other: add instances in Settings, give each its own
settings, place them in the layout. A broken definition is skipped and its problems are listed in
Settings; it never affects other widgets.

Use a declarative widget for "call an API, show the result". Anything with logic, sessions, websockets
or device features needs a compiled plugin (see [authoring.md](authoring.md)).

## A complete example

```jsonc
// bins.widget.json: next bin collection (a made-up API)
{
  "manifest": { "id": "bins", "name": "Bin day", "version": "1.0.0" },
  "locations": ["sidebar"],
  "hosts": ["https://bins.example.com/"],
  "settings": { "fields": [
    { "kind": "text", "key": "uprn", "label": "Property number", "required": true }
  ] },
  "source": {
    "url": "https://bins.example.com/api/collections?uprn={settings.uprn|urlencode}",
    "refresh": "6h",
    "map": {
      "fields": { "next": "{$.collections[0].date|date}" },
      "rows": { "path": "$.collections", "fields": {
        "bin": "{$.type|lookup:colours} {$.type}",
        "when": "{$.date|date}"
      } }
    }
  },
  "view": { "kind": "list", "title": "Bins (next: {next})", "rowTitle": "{bin}", "rowTrailing": "{when}", "maxRows": 3 },
  "lookups": { "colours": { "Recycling": "🟦", "General": "⬛", "Garden": "🟩", "*": "🗑️" } }
}
```

Comments and trailing commas are allowed.

## The parts of a definition

| Key | Required | What it is |
|---|---|---|
| `manifest` | yes | `id` (lowercase, digits, dashes; also the widget type), `name`, `version`, optional `description`, and `requires.auth` for any sign-in provider a source uses. |
| `kind` | no | `"widget"` (default) or `"photoSource"` (photos for the slideshow clock face). |
| `locations` | no | Widgets: where they may go, `"main"` and/or `"sidebar"`. Default `["sidebar"]`. |
| `hosts` | yes | The only addresses requests may go to. A URL outside these is refused. May use `{settings.x}` for self-hosted services. |
| `settings` | no | The settings form: `{ "fields": [...] }`, in the same format as compiled plugins' descriptors (`text`, `number`, `toggle`, `choice`, `duration`, `color`, `textList`, `objectList`, `account`, `instanceRefs`). |
| `source` | yes | Where the data comes from (below). |
| `sources` | no | Extra named sources, e.g. a forecast for the expanded view. |
| `view` | widgets | How the data is shown (below). |
| `expanded` | no | `{ "source": "<name in sources>", "view": {...} }`: a full-screen view opened by tapping the widget. Without `source` it reuses the main data. |
| `lookups` | no | Named tables for the `lookup` filter. The key `"*"` matches anything not listed. |

### `source`

| Key | What it is |
|---|---|
| `url` | A template. Use `|urlencode` on values that go in a query. |
| `method` | `GET` (default) or `POST`. |
| `headers` | Name → template. |
| `body` | A template sent as JSON (for `POST`). |
| `auth` | A sign-in provider id, e.g. `"openweather"`. Requests are signed by Sekund with the account picked in the definition's first `account` setting for that provider; the definition never sees the key or token. The provider must also be in `manifest.requires.auth`. |
| `refresh` | How often to fetch, e.g. `"15m"`. At least `1m`. People can override it per instance. |
| `forEach` | `{ "setting": "accounts" }`: one request per entry of a list setting, results joined. The entry is `{item}` (text list) or `{item.key}` (object list). |
| `paginate` | `{ "start": 1, "maxPages": 10, "next": "$.nextPage" }`: fetch page after page, joining rows. The page is `{page}`. Stops at an empty page, at `maxPages`, or when `next` (if given) is missing. |
| `map.fields` | Name → template, evaluated against the whole answer. |
| `map.rows` | `{ "path": "$.list", "fields": { name → template } }`: one row per list entry; inside, `$` is the entry. A single object counts as one row. |

### `view`

Every text in a view is a template over the mapped field names (`{temp}`), settings (`{settings.x}`),
and, for row templates, the row's fields.

| `kind` | Keys |
|---|---|
| `stat` | `value`, `label`, `icon` (text or emoji), `secondary: [{label, value}]` |
| `list` | `title`, `rowTitle`, `rowSubtitle`, `rowTrailing`, `rowIcon`, `maxRows` (default 10) |
| `keyValue` | `title`, `items: [{label, value}]`, and/or `rowLabel` + `rowValue` for one line per row |
| `image` | `url`, `caption` (fetched with the source's account if it has one, and only from `hosts`) |
| `progress` | `label`, `value`, `max` (default `1`), `detail` |

### Photo sources

With `"kind": "photoSource"`, `map.rows` must produce `url` for each photo, and may produce `id`,
`caption` and `takenAt`. The slideshow clock face fetches the URLs with the source's account.

## Templates

A placeholder is `{value|filter|filter:argument}`.

| Value | Means |
|---|---|
| `$.a.b[0]` | A path into the answer (or into the row, inside `map.rows`). `[-1]` is the last entry. |
| `settings.key` | The instance's setting. |
| `item`, `item.key` | The current `forEach` entry. |
| `page` | The current page while paginating. |
| `name` | In views: a field produced by `map`. |

| Filter | Does |
|---|---|
| `number:N` | N digits after the point (default 0). Halves round up. |
| `round` | Same as `number:0`. |
| `divide:N`, `multiply:N` | Arithmetic, e.g. pence to pounds with `divide:100`. |
| `lookup:table` | Replace using a table from `lookups`. |
| `upper`, `lower` | Change case. |
| `default:text` | Use `text` if the value is missing or empty. |
| `date` | A date or time as `Mon 3 Mar`. Accepts ISO dates and times, and Unix timestamps. |
| `time` | A time as `14:05` or `2:05 PM`, following the 12/24-hour setting. |
| `urlencode` | Make a value safe to put in a URL. |

A missing value shows as empty text in views. In `url`, `headers` and `body` it stops the request
instead, so a half-filled request is never sent.

## Safety

- Requests only go to `hosts`. With `auth`, credentials only go to the provider's own `baseUrls` as well.
- Definitions can't run code; views are the fixed set above.
- Every definition is checked when it's loaded. Problems are reported with the exact path, for
  example `source.map.fields.temp: Unknown filter 'rnd'`.
