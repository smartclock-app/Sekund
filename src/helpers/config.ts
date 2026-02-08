import { Widget, WidgetLocation, WidgetModule, WidgetOfType, WidgetType } from "@/helpers/types";
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

export default async () => {
  const modules = import.meta.glob("../widgets/*/index.ts", { eager: true }) as Record<string, WidgetModule>;
  const componentModules = import.meta.glob("../widgets/*/Component.{ts,tsx}", { eager: true }) as Record<
    string,
    { default: any }
  >;

  const widgetSchemas: Record<string, ZodType> = {};
  const widgetModules: Record<string, Widget> = {};
  const widgetAllowedLocations: Record<WidgetLocation, string[]> = { main: [], sidebar: [] };
  const widgetThemes: string[] = [];

  for (let [path, mod] of Object.entries(modules)) {
    const widgetName = path.match(/\.\.\/widgets\/(.+)\/index\.ts$/)![1];
    const componentPath = `../widgets/${widgetName}/Component.tsx`;
    const Component = componentModules[componentPath].default;

    if (!mod.Type || !Object.values(WidgetType).includes(mod.Type)) {
      throw new Error(`Widget "${widgetName}" at ${path} missing Type export or Type is not a valid WidgetType`);
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
      throw new Error(`Widget "${widgetName}" at ${path} already used by another widget, widget names must be unique`);
    }

    if (mod.Type == WidgetType.Widget) {
      if (!mod.AllowedLocations || !Array.isArray(mod.AllowedLocations)) {
        throw new Error(
          `Widget "${widgetName}" at ${path} missing AllowedLocations export or AllowedLocations is not an array`,
        );
      }

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

    if (mod.Type == WidgetType.ClockTheme) widgetThemes.push(widgetName);
    widgetSchemas[widgetName] = mod.Schema.prefault({});
  }

  const currentVersion = await getVersion();
  const schemaFileName = `${SCHEMA_FILENAME_PREFIX}${currentVersion}.json`;

  // Backup old config if schema version has changed to prevent breaking changes from losing user config
  if (!(await exists(schemaFileName, { baseDir: BASE_DIRECTORY }))) {
    await backupConfig();
  }

  const configSchema = z.object({
    $schema: z.literal(schemaFileName).catch(schemaFileName),
    ...baseConfig,
    widgets: z.object(widgetSchemas).prefault({}),
    clockTheme: z.enum(["default", ...widgetThemes]).catch("default"),
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

  const layout = Object.entries(configData.layout).reduce(
    (acc, [location, widgetNames]) => {
      acc[location as WidgetLocation] = widgetNames.map(
        (name: string) => widgetModules[name] as WidgetOfType<WidgetType.Widget>,
      );
      return acc;
    },
    {} as Record<WidgetLocation, WidgetOfType<WidgetType.Widget>[]>,
  );

  const theme = (widgetModules[configData.clockTheme] as WidgetOfType<WidgetType.ClockTheme> | undefined) ?? "default";

  return [configData, layout, theme] as const;
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
