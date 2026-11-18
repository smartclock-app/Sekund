# plugins/declarative-builtins

The declarative widget definitions that ship with Sekund. They're ordinary JSON files in
`src/commonMain/composeResources/files/definitions/`, in exactly the format documented in
[docs/plugins/declarative-widgets.md](../../docs/plugins/declarative-widgets.md). They load through the same
code as a user's own definitions (the entry points pass `BuiltInDefinitions.readAll()` to `AppGraph`).

| File | What it is |
|---|---|
| `weather.widget.json` | OpenWeather current weather (`stat`), with a 24-hour forecast as the tap-to-expand view. Units are picked separately for temperature and wind. |
| `actualbudget.widget.json` | Balances of chosen Actual Budget accounts through [actual-http-api](https://github.com/jhonderson/actual-http-api), one request per account (`forEach`). |

To add one: put the file in the folder and its name in `BuiltInDefinitions.FILE_NAMES`. The tests load
every listed file and fail on any problem.

This module has no `SekundPlugin`: the definitions become plugins through `plugins/declarative`.
