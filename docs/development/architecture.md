# Architecture Overview

## Technology Stack

| Layer | Technology |
|-------|-----------|
| Frontend | React 19 + TypeScript, Vite, SCSS modules |
| State management | [Zustand](https://github.com/pmndrs/zustand) stores |
| Schema validation | [Zod v4](https://zod.dev/) |
| Desktop shell | Tauri v2 (Rust) |
| HTTP / FS / logs | Tauri plugins (`plugin-fs`, `plugin-http`, `plugin-log`, …) |

## Startup Flow

```
main.tsx
 └─ App.tsx
     └─ loadConfig()   (src/helpers/config/index.ts)
         ├─ loadModules()       discovers all widgets
         ├─ Zod schema build    merges base + widget schemas
         ├─ config.json read / write (AppData)
         ├─ schema-<version>.json write
         ├─ backupConfig()      on version change
         └─ CSS variables       generateCssVariablesTemplate() + loadCssVariables()
```

After `loadConfig()` resolves, the React tree renders with the config, layout, and resolved widget components passed down as props/context.

## Widget Loader (`src/helpers/config/modules.ts`)

Widgets are discovered at **build time** using Vite's `import.meta.glob`:

```ts
import.meta.glob("/src/widgets/*/index.ts", { eager: true })
import.meta.glob("/src/widgets/*/Component.{ts,tsx}", { eager: true, import: "default" })
```

Every directory under `src/widgets/` that exports the required interface is registered automatically. No manual registration is needed.

### Widget module validation

The loader validates each widget module before registering it. Invalid widgets are skipped with a warning (logged via `@tauri-apps/plugin-log`) and do **not** crash the app.

Required exports from `index.ts`:

| Export | Type | Required for |
|--------|------|-------------|
| `Type` | `WidgetType` enum value | all widgets |
| `Schema` | Zod schema | all widgets |
| `AllowedLocations` | `WidgetLocation[]` | `widget` type only |
| `OnInit` | `() => void` | optional (all types) |

Required file in the widget directory:

| File | Required for |
|------|-------------|
| `Component.tsx` or `Component.ts` | all widgets (must be a default export of a React component or function) |

### Reserved widget names

The following names are reserved and cannot be used by third-party widgets:

- `calendar`
- `clock`
- `default`
- `root`

## CSS Variables System

Each widget can ship a `Variables.css` file containing a `:root {}` block of CSS custom properties. On startup:

1. `generateCssVariablesTemplate()` reads all `Variables.css` files and the root `src/assets/variables.css` via `import.meta.glob`.
2. It merges all `:root` blocks into a single template and writes `variables.template.css` to AppData.
3. If `variables.css` does not already exist in AppData, it is created from the template (all variables commented out).
4. `loadCssVariables()` injects both files into `document.head` as a `<style id="widget-variables">` element. `variables.css` is appended after the template, so user overrides take precedence.

## Remote Config Server

The Rust backend (`src-tauri/src/`) runs an embedded HTTP server (via `tiny_http`) when `remoteConfig.enabled` is true. Incoming requests at `/api/command` are forwarded to the frontend via a Tauri event/command. The React `RemoteConfig` component listens for requests and dispatches them to the matching handler registered in `useRemoteConfigStore`.

## State Stores (Zustand)

| Store | File | Description |
|-------|------|-------------|
| `useConfigStore` | `src/hooks/useConfigStore.ts` | Holds parsed config and `configSchema`; exposes `editConfig()` |
| `useVariablesStore` | `src/hooks/useVariablesStore.ts` | Holds `variables.css` content; exposes `setVariables()` |
| `useRemoteConfigStore` | `src/hooks/useRemoteConfig.ts` | Registry of remote-config command handlers |
| `useAlertsStore` | `src/hooks/useAlertsStore.ts` | In-app alert/notification queue |
| `useDatabaseStore` | `src/hooks/useDatabaseStore.ts` | SQLite database handle (via `plugin-sql`) |
