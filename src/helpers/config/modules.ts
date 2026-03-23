import { warn } from "@tauri-apps/plugin-log";
import { ZodType } from "zod";
import { Widget, WidgetLocation, WidgetModule, WidgetOfType, WidgetType } from "../types";

const RESERVED_WIDGET_NAMES = ["calendar", "clock", "default", "root"];

export default async () => {
  const modules = import.meta.glob("/src/widgets/*/index.ts", {
    eager: true,
  }) as Record<string, WidgetModule>;
  const componentModules = import.meta.glob("/src/widgets/*/Component.{ts,tsx}", {
    eager: true,
    import: "default",
  }) as Record<string, any>;

  const widgetSchemas: Record<string, ZodType> = {};
  const widgetModules: Record<string, Widget> = { calendar: { Name: "calendar" } as any };
  const widgetAllowedLocations: Record<WidgetLocation, string[]> = { main: [], sidebar: ["calendar"] };
  const clockThemes: string[] = ["default"];
  const calendarExtensions: Record<string, WidgetOfType<WidgetType.CalendarExtension>> = {};

  for (let [path, mod] of Object.entries(modules)) {
    const widgetName = path.match(/\/src\/widgets\/(.+)\/index\.ts$/)![1];
    const componentPath = `/src/widgets/${widgetName}/Component.tsx`;
    const altComponentPath = `/src/widgets/${widgetName}/Component.ts`;
    const Component = componentModules[componentPath] || componentModules[altComponentPath];

    try {
      if (RESERVED_WIDGET_NAMES.includes(widgetName)) {
        throw new Error(`Widget name "${widgetName}" at ${path} is reserved and cannot be used`);
      }

      if (!mod.Type || !Object.values(WidgetType).includes(mod.Type)) {
        throw new Error(`Widget "${widgetName}" at ${path} missing Type export or Type is not a valid WidgetType`);
      }

      if (mod.Type === WidgetType.Widget && (!mod.AllowedLocations || !Array.isArray(mod.AllowedLocations))) {
        throw new Error(
          `Widget "${widgetName}" at ${path} missing AllowedLocations export or AllowedLocations is not an array`,
        );
      }

      if (!mod.Schema || typeof mod.Schema.parse !== "function") {
        throw new Error(`Widget "${widgetName}" at ${path} missing Schema export or Schema is not a Zod schema`);
      }

      if (!Component || typeof Component !== "function") {
        throw new Error(
          `Widget "${widgetName}" at ${path} missing Component export or Component is not a valid React component`,
        );
      }

      if (widgetSchemas[widgetName]) {
        throw new Error(
          `Widget "${widgetName}" at ${path} already used by another widget, widget names must be unique`,
        );
      }
    } catch (error) {
      warn(`Error loading widget at ${path}: ${(error as Error).message}`);
      continue;
    }

    if (mod.OnInit) {
      mod.OnInit();
    }

    if (mod.Type == WidgetType.Widget) {
      for (const location of mod.AllowedLocations) {
        widgetAllowedLocations[location].push(widgetName);
      }

      widgetModules[widgetName] = {
        Name: widgetName,
        Type: mod.Type,
        AllowedLocations: mod.AllowedLocations,
        Schema: mod.Schema,
        Component,
      };
    } else {
      widgetModules[widgetName] = {
        Name: widgetName,
        Type: mod.Type,
        Schema: mod.Schema,
        Component,
        ...(mod.Type === WidgetType.CalendarExtension && mod.Manager ? { Manager: mod.Manager } : {}),
      };
    }

    if (mod.Type == WidgetType.ClockTheme) {
      clockThemes.push(widgetName);
    } else if (mod.Type == WidgetType.CalendarExtension) {
      calendarExtensions[widgetName] = widgetModules[widgetName] as WidgetOfType<WidgetType.CalendarExtension>;
    }
    widgetSchemas[widgetName] = mod.Schema.prefault({});
  }

  return {
    widgetModules,
    widgetSchemas,
    widgetAllowedLocations,
    clockThemes,
    calendarExtensions,
  };
};
