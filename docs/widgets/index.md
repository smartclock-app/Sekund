# Widgets

Sekund supports three kinds of widgets:

| Type                | Description                                         |
| ------------------- | --------------------------------------------------- |
| `widget`            | Rendered in a layout zone (`main` or `sidebar`)     |
| `calendarExtension` | Provides additional events to the built-in calendar |
| `clockTheme`        | Replaces the default clock face                     |

## Available Widgets

| Widget                            | Type              | Allowed Locations |
| --------------------------------- | ----------------- | ----------------- |
| [actualbudget](actualbudget.md)   | widget            | sidebar           |
| [alexa](alexa.md)                 | widget            | sidebar           |
| [google](google.md)               | calendarExtension | —                 |
| [homeassistant](homeassistant.md) | widget            | sidebar           |
| [photos](photos.md)               | clockTheme        | —                 |
| [trakt](trakt.md)                 | calendarExtension | —                 |
| [updater](updater.md)             | widget            | sidebar           |
| [weather](weather.md)             | widget            | main, sidebar     |

## Layout Zones

### `main`

The large central area below the clock. Widgets here typically show more detail. Currently only `weather` supports this zone.

### `sidebar`

The right-hand panel. Multiple widgets can be stacked vertically. Widgets are displayed in the order they are listed.

## Calendar Extensions

Calendar extensions (`calendarExtension` type) are not placed in `layout`. Instead, they are activated by listing their names in `calendar.extensions`:

```json
{
  "calendar": {
    "extensions": ["google", "trakt"]
  }
}
```

## Clock Themes

A clock theme (`clockTheme` type) replaces the default clock face. Set `clockTheme` in the top-level config to the widget name:

```json
{
  "clockTheme": "photos"
}
```

Only one clock theme can be active at a time. Use `"default"` to use the default clock face.

## Built-in Widgets

`calendar` and `clock` are reserved names handled internally by the app and cannot be used by third-party widgets.
