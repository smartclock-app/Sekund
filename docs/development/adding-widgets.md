# Adding / Contributing Widgets

Smart Clock uses a file-system-based widget loader. Adding a new widget requires creating a directory under `src/widgets/<name>/` with a small number of required files.

## Widget Types

Choose the type that fits your use-case:

| `WidgetType` | Use when |
|-------------|---------|
| `widget` | Renders a UI panel in `main` or `sidebar` |
| `calendarExtension` | Provides `CalendarEvent[]` to the built-in calendar |
| `clockTheme` | Replaces the default clock face with a custom React component |

## Required Files

### `src/widgets/<name>/index.ts`

Must export:

```ts
import { WidgetType, WidgetLocation } from "@/helpers/types";
import z from "zod";

// Required for all types
export const Type = WidgetType.Widget; // or CalendarExtension / ClockTheme

// Required for WidgetType.Widget only
export const AllowedLocations = [WidgetLocation.Main, WidgetLocation.Sidebar] as const;

// Required for all types – Zod schema for the widget's config object
export const Schema = z.object({
  myOption: z.string().catch("default value"),
});

export type Config = z.infer<typeof Schema>;

// Optional – called once when the widget is loaded
export const OnInit = () => {
  // e.g. register remote-config commands
};
```

> **Tip:** Always use `.catch(<default>)` (or `.prefault({} as any)` for objects, `.prefault([])` for arrays) so that missing values are silently filled rather than causing a validation error.

### `src/widgets/<name>/Component.tsx` (or `.ts`)

Must be the **default export** of:

- A `React.FC<{ config: Config; location: WidgetLocation }>` for `widget` type.
- A `(config: Config, calendarConfig: CalendarConfig) => CalendarEvent[] | Promise<CalendarEvent[]>` for `calendarExtension`.
- A `React.FC<{ config: Config; clockConfig: ClockConfig; now: dayjs.Dayjs }>` for `clockTheme`.

### `src/widgets/<name>/Variables.css` (optional)

If your widget uses CSS custom properties, define them here:

```css
:root {
  --my-widget-color: #ffffff;
  --my-widget-font-size: 1rem;
}
```

These are merged into `variables.template.css` on startup. Users can override them in `variables.css`.

## Naming Rules

- Use **lowercase letters and hyphens** only (no spaces, underscores, or uppercase).
- The directory name becomes the widget's unique key in the config.
- The following names are **reserved** and cannot be used: `calendar`, `clock`, `default`, `root`.

## Example: Minimal Widget

```
src/widgets/hello/
├── index.ts
└── Component.tsx
```

**`index.ts`**

```ts
import { WidgetLocation, WidgetType } from "@/helpers/types";
import z from "zod";

export const Type = WidgetType.Widget;
export const AllowedLocations = [WidgetLocation.Sidebar] as const;

export const Schema = z.object({
  message: z.string().catch("Hello, World!"),
});

export type Config = z.infer<typeof Schema>;
```

**`Component.tsx`**

```tsx
import { WidgetComponent } from "@/helpers/types";
import type { Config } from "./index";

const Hello: WidgetComponent<Config> = ({ config }) => {
  return <div>{config.message}</div>;
};

export default Hello;
```

Add it to your `config.json`:

```json
{
  "layout": {
    "sidebar": ["calendar", "hello", "updater"]
  },
  "widgets": {
    "hello": {
      "message": "Hello from my widget!"
    }
  }
}
```

## Registering Remote Config Commands

Widgets can register custom remote-config commands in their `OnInit` function:

```ts
import useRemoteConfigStore from "@/hooks/useRemoteConfig";

export const OnInit = () => {
  useRemoteConfigStore.getState().addHandler("my_command", (data) => {
    // handle the command
    return { status: "ok", result: "done" };
  });
};
```

The command can then be triggered via:

```bash
curl -X POST http://<device>:8080/api/command \
  -H "Content-Type: application/json" \
  -d '{"command": "my_command", "data": {}}'
```

## Contributing

1. Fork the repository and create a feature branch.
2. Add your widget under `src/widgets/<name>/`.
3. Add documentation at `docs/widgets/<name>.md` following the existing template.
4. Open a pull request.
