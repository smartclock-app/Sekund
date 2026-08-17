# Getting Started

Sekund is a full-screen clock application built with Tauri v2, React, and TypeScript. It is intended to run on always-on displays such as Raspberry Pis or android tablets.

## Prerequisites

- A supported OS (macOS, Windows, Linux, or Android)
- [Bun](https://bun.sh/) (JavaScript runtime / package manager)
- [Rust + Cargo](https://www.rust-lang.org/tools/install) (required by Tauri)
- [Tauri CLI v2](https://v2.tauri.app/reference/cli/) (installed automatically via `bun tauri`)

## Installation

### Running from source

1. Clone the repository:

   ```bash
   git clone https://github.com/dnpkuk/ClockBeta.git
   cd ClockBeta
   ```

2. Install JavaScript dependencies:

   ```bash
   bun install
   ```

3. Start the development server (Tauri + Vite hot-reload):
   ```bash
   bun tauri dev
   ```

### Building for production

```bash
bun tauri build
```

For Android (APK targeting ARM64 and ARMv7):

```bash
bun run build:android
```

## First Launch

On first launch the app creates its configuration directory in your system's **AppData** folder under the identifier `uk.dnpk.sekund`. The exact path varies by OS:

| OS      | Path                                            |
| ------- | ----------------------------------------------- |
| macOS   | `~/Library/Application Support/uk.dnpk.sekund/` |
| Linux   | `~/.local/share/uk.dnpk.sekund/`                |
| Windows | `%APPDATA%\uk.dnpk.sekund\`                     |

The following files are created automatically on startup:

| File                     | Description                                        |
| ------------------------ | -------------------------------------------------- |
| `config.json`            | Main configuration file                            |
| `schema-<version>.json`  | JSON Schema for the current app version            |
| `variables.template.css` | Auto-generated CSS variable template (do not edit) |
| `variables.css`          | Your custom CSS variable overrides (safe to edit)  |

## Quick Configuration

The easiest way to configure Sekund is through the **in-app editor**. Long-press anywhere on the clock face to open the editor overlay. The editor provides:

- A Monaco-based JSON editor for `config.json` with live Zod schema validation.
- A CSS editor for `variables.css`.
- A read-only log viewer.

Alternatively, edit `config.json` directly in the AppData directory. Changes take effect after restarting the app.

For a full reference of every configuration option see [Configuration](configuration.md).

## Adding Widgets

Widgets are placed in the `layout` section of `config.json`. There are two layout zones:

- **`main`** – the large central area.
- **`sidebar`** – the right-hand panel.

Each zone accepts an ordered array of widget names, e.g.:

```json
{
  "layout": {
    "main": ["weather"],
    "sidebar": ["calendar", "alexa", "updater"]
  }
}
```

See [Widgets](widgets/index.md) for available widgets and their allowed locations.
