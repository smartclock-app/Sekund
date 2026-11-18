# plugins/declarative

The engine behind **declarative widgets and photo sources**: JSON definitions that call an API, pick
values out of the answer and show them with a built-in template. No code runs, so definitions can be
installed while the app is running.

The format is documented for authors in [docs/plugins/declarative-widgets.md](../../docs/plugins/declarative-widgets.md).
The data classes and the validator live in `:core:protocol` (`protocol/declarative/`), so the companion
and server can check definitions too.

## How it fits together

```
<config>/widgets/*.json ─┐
built-in definitions ────┤  runtime: DeclarativeWidgets (load, validate, install, remove)
PUT /v1/declarative-…  ──┘        │
                                  ▼  ::DeclarativePlugin (passed in by the app)
                         DeclarativePlugin ── one widget / photo source contribution
                                  │
                         DeclarativeWidget / DeclarativePhotoSource (one per instance)
                                  │
                         SourceRunner ── requests, forEach, paginate, hosts check, map
                         TemplateEngine ── "{$.main.temp|round}°" → "12°"
                         views/ ── stat, list, keyValue, image, progress
```

## Main types

| Type | What it does |
|---|---|
| `DeclarativePlugin` | Turns one definition into an ordinary `SekundPlugin`. |
| `SourceRunner` | Makes a source's requests (signed in via `context.auth` if it has `auth`) and maps the answers. |
| `TemplateEngine` / `TemplateScope` | Fills in `{...}` placeholders and applies `|filters`. |
| `JsonPathLite` | The small JSONPath subset: `$.a.b[0]`. |
| `views/DeclarativeView` | Draws a mapped result with one of the built-in templates. |

## Extending

- **A new filter:** add a branch in `TemplateEngine.apply` and its name to `DeclarativeParser.KNOWN_FILTERS`.
- **A new view template:** add a subclass of `ViewDefinition` (protocol), a composable in `views/`, a branch
  in `DeclarativeView`, and its templates in `DeclarativeParser.templatesOf`. The compiler points out
  every `when` that needs the new case.
