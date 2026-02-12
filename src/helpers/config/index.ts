import { BASE_DIRECTORY, WidgetLocation, WidgetOfType, WidgetType } from "@/helpers/types";
import { getVersion } from "@tauri-apps/api/app";
import { exists, mkdir, readDir, readTextFile, remove, writeTextFile } from "@tauri-apps/plugin-fs";
import { info, warn } from "@tauri-apps/plugin-log";
import z from "zod";
import baseConfig from "./base";
import { generateCssVariablesTemplate, loadCssVariables } from "./cssVariables";
import loadModules from "./modules";

const CONFIG_BACKUP_LIMIT = 5;
const CONFIG_BACKUP_DIR = "config-backups";
const CONFIG_FILENAME = "config.json";
const SCHEMA_FILENAME_PREFIX = "schema-";

export default async () => {
  const { widgetModules, widgetSchemas, widgetAllowedLocations, clockThemes, calendarExtensions } = await loadModules();

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
    await generateCssVariablesTemplate();
    await loadCssVariables();
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
