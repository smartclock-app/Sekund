import { Widget, WidgetLocation, WidgetModule, WidgetOfType, WidgetType } from "@/helpers/types";
import useVariablesStore from "@/hooks/useVariablesStore";
import { getVersion } from "@tauri-apps/api/app";
import { BaseDirectory, exists, mkdir, readDir, readTextFile, remove, writeTextFile } from "@tauri-apps/plugin-fs";
import { info, warn } from "@tauri-apps/plugin-log";
import z, { type ZodType } from "zod";
import baseConfig from "./baseConfig";

const BASE_DIRECTORY = BaseDirectory.AppData;
const CONFIG_BACKUP_LIMIT = 5;
const CONFIG_BACKUP_DIR = "config-backups";
const CONFIG_FILENAME = "config.json";
const SCHEMA_FILENAME_PREFIX = "schema-";
const VARIABLES_FILENAME = "variables";
const RESERVED_WIDGET_NAMES = ["calendar", "clock", "default", "root"];

export default async () => {
  const modules = import.meta.glob("../widgets/*/index.ts", {
    eager: true,
  }) as Record<string, WidgetModule>;
  const componentModules = import.meta.glob("../widgets/*/Component.{ts,tsx}", {
    eager: true,
    import: "default",
  }) as Record<string, any>;
  const variableModules = import.meta.glob("../widgets/*/Variables.css", {
    eager: true,
    query: "raw",
    import: "default",
  }) as Record<string, string>;
  const rootStyles = import.meta.glob("../assets/variables.css", {
    eager: true,
    query: "raw",
    import: "default",
  }) as Record<string, string>;

  const widgetSchemas: Record<string, ZodType> = {};
  const widgetModules: Record<string, Widget> = { calendar: { Name: "calendar" } as any };
  const widgetAllowedLocations: Record<WidgetLocation, string[]> = { main: [], sidebar: ["calendar"] };
  const clockThemes: string[] = ["default"];
  const calendarExtensions: Record<string, WidgetOfType<WidgetType.CalendarExtension>> = {};
  const widgetVariables: Record<string, string> = { root: rootStyles["../assets/variables.css"] };

  for (let [path, mod] of Object.entries(modules)) {
    const widgetName = path.match(/\.\.\/widgets\/(.+)\/index\.ts$/)![1];
    const componentPath = `../widgets/${widgetName}/Component.tsx`;
    const altComponentPath = `../widgets/${widgetName}/Component.ts`;
    const Component = componentModules[componentPath] || componentModules[altComponentPath];
    const Variables = variableModules[`../widgets/${widgetName}/Variables.css`];

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

      if (!mod.Schema || typeof (mod.Schema as any).parse !== "function") {
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

    if (Variables && typeof Variables === "string") {
      widgetVariables[widgetName] = Variables;
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
      };
    }

    if (mod.Type == WidgetType.ClockTheme) {
      clockThemes.push(widgetName);
    } else if (mod.Type == WidgetType.CalendarExtension) {
      calendarExtensions[widgetName] = widgetModules[widgetName] as WidgetOfType<WidgetType.CalendarExtension>;
    }
    widgetSchemas[widgetName] = mod.Schema.prefault({});
  }

  const currentVersion = await getVersion();
  const schemaFileName = `${SCHEMA_FILENAME_PREFIX}${currentVersion}.json`;

  if (!(await exists(schemaFileName, { baseDir: BASE_DIRECTORY }))) {
    await backupConfig();
  }

  baseConfig.calendar = baseConfig.calendar
    .unwrap()
    .extend({
      extensions: z.array(z.enum(Object.keys(calendarExtensions))).catch([]),
    })
    .prefault({} as any);

  const configSchema = z.object({
    $schema: z.literal(schemaFileName).catch(schemaFileName),
    ...baseConfig,
    widgets: z.object(widgetSchemas).prefault({}),
    clockTheme: z.enum(clockThemes).catch("default"),
    layout: z
      .object({
        main: z.array(z.enum(widgetAllowedLocations.main)).prefault([]),
        sidebar: z.array(z.enum(widgetAllowedLocations.sidebar)).prefault(["updater"]),
      })
      .prefault({}),
  });

  let configData;
  if (!(await exists(CONFIG_FILENAME, { baseDir: BASE_DIRECTORY }))) {
    warn("Config file not found, using defaults");
    configData = configSchema.parse({});
  } else {
    const configFileContents = await readTextFile(CONFIG_FILENAME, { baseDir: BASE_DIRECTORY });
    const parsedConfig = JSON.parse(configFileContents);
    configData = configSchema.parse(parsedConfig);
  }

  await saveConfig(configData);
  await writeTextFile(schemaFileName, JSON.stringify(configSchema.toJSONSchema(), null, 2), {
    baseDir: BASE_DIRECTORY,
  });

  try {
    await generateVariablesTemplate(widgetVariables);
    await loadVariables();
  } catch (error) {
    warn(`Failed to load variables: ${(error as Error).message}`);
  }

  const layout = Object.entries(configData.layout).reduce<Record<WidgetLocation, WidgetOfType<WidgetType.Widget>[]>>(
    (acc, [location, widgetNames]) => {
      acc[location as WidgetLocation] = widgetNames
        .map((name: string) => widgetModules[name] as WidgetOfType<WidgetType.Widget>)
        .filter(widget => widget !== undefined);
      return acc;
    },
    { main: [], sidebar: [] },
  );

  const clockTheme = widgetModules[configData.clockTheme] as WidgetOfType<WidgetType.ClockTheme>;

  return { config: configData, layout, clockTheme, calendarExtensions } as const;
};

const generateVariablesTemplate = async (widgetVariables: Record<string, string>) => {
  const mergedVariables: string[] = [];

  Object.entries(widgetVariables).forEach(([name, css]) => {
    const match = css.match(/:root\s*\{([\s\S]*?)\}/);
    if (match) {
      const content = "  " + match[1].trim();
      mergedVariables.push(`  /* ${name} widget variables */`);
      mergedVariables.push(content);
    }
  });

  const template = `
:root {
${mergedVariables.join("\n")}
}`;

  if (!(await exists(`${VARIABLES_FILENAME}.css`, { baseDir: BASE_DIRECTORY }))) {
    await writeTextFile(
      `${VARIABLES_FILENAME}.css`,
      `/* Custom CSS variables - uncomment and modify from variables.template.css */
${template}`,
      { baseDir: BASE_DIRECTORY },
    );
  }

  await writeTextFile(
    `${VARIABLES_FILENAME}.template.css`,
    `/* 
 * DO NOT EDIT THIS FILE - it is auto-generated on app startup
 * To customize variables, edit variables.css instead
 */
${template}`,
    { baseDir: BASE_DIRECTORY },
  );
};

const loadVariables = async () => {
  const variablesPath = `${VARIABLES_FILENAME}.css`;

  const template = await readTextFile(`${VARIABLES_FILENAME}.template.css`, { baseDir: BASE_DIRECTORY });

  let css = template;
  if (await exists(variablesPath, { baseDir: BASE_DIRECTORY })) {
    const variablesContent = await readTextFile(variablesPath, { baseDir: BASE_DIRECTORY });
    useVariablesStore.getState().setVariables(variablesContent, false);
    css += "\n" + variablesContent;
  }

  const styleElement = document.createElement("style");
  styleElement.setAttribute("id", "widget-variables");
  styleElement.textContent = css;
  document.head.appendChild(styleElement);
};

export const saveConfig = async (config: Record<string, any>) => {
  await writeTextFile(CONFIG_FILENAME, JSON.stringify(config, null, 2), { baseDir: BASE_DIRECTORY });
};

const backupConfig = async () => {
  if (await exists(CONFIG_FILENAME, { baseDir: BASE_DIRECTORY })) {
    await mkdir(CONFIG_BACKUP_DIR, { baseDir: BASE_DIRECTORY, recursive: true });

    const entries = await readDir(CONFIG_BACKUP_DIR, { baseDir: BASE_DIRECTORY });
    const backups = entries
      .filter(entry => entry.name.startsWith("config-") && entry.name.endsWith(".json"))
      .sort((a, b) => b.name.localeCompare(a.name));

    const oldConfigContents = await readTextFile(CONFIG_FILENAME, { baseDir: BASE_DIRECTORY });
    const timestamp = new Date().toISOString().replace(/[:T]/g, "-").replace(/\..+/, "");
    await writeTextFile(`${CONFIG_BACKUP_DIR}/config-${timestamp}.json`, oldConfigContents, {
      baseDir: BASE_DIRECTORY,
    });

    for (const backup of backups.slice(CONFIG_BACKUP_LIMIT)) {
      remove(`${CONFIG_BACKUP_DIR}/${backup.name}`, { baseDir: BASE_DIRECTORY });
      info(`Removed old backup: ${backup.name}`);
    }
  }
};
