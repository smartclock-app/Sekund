import { WidgetLocation, WidgetModule, WidgetModuleOfType, WidgetType } from "@/helpers/types";
import { getVersion } from "@tauri-apps/api/app";
import { BaseDirectory, exists, mkdir, readDir, readTextFile, remove, writeTextFile } from "@tauri-apps/plugin-fs";
import z, { type ZodType } from "zod";

const BASE_DIRECTORY = BaseDirectory.AppData;
const CONFIG_BACKUP_LIMIT = 5;
const CONFIG_BACKUP_DIR = "config-backups";
const CONFIG_FILENAME = "config.json";
const SCHEMA_FILENAME_PREFIX = "schema-";

const baseConfig = {
  orientation: z.enum(["portrait", "landscape"]).catch("landscape"),
  checkNetwork: z.boolean().catch(true),
  remoteConfig: z
    .object({
      enabled: z.boolean().catch(true),
      port: z.number().catch(8080),
      password: z.string().catch(""),
      useBonjour: z.boolean().catch(true),
      bonjourName: z.string().catch("Smart Clock"),
      toggleDisplayPath: z.string().catch(""),
    })
    .prefault({} as any),
  clock: z
    .object({
      format: z.enum(["12h", "24h"]).catch("12h"),
      showSeconds: z.boolean().catch(true),
    })
    .prefault({} as any),
};

export default async () => {
  const modules = import.meta.glob("../widgets/*/index.{ts,tsx}", { eager: true }) as Record<string, WidgetModule>;

  const widgetSchemas: Record<string, ZodType> = {};
  const widgetModules: Record<string, WidgetModule> = {};
  const widgetAllowedLocations: Record<WidgetLocation, string[]> = { main: [], sidebar: [] };
  const widgetThemes: string[] = [];

  for (const [path, mod] of Object.entries(modules)) {
    if (typeof mod.Name !== "string") {
      throw new Error(`Widget at ${path} missing Name export or Name is not a string`);
    }

    if (!mod.Type || !Object.values(WidgetType).includes(mod.Type)) {
      throw new Error(`Widget "${mod.Name}" at ${path} missing Type export or Type is not a valid WidgetType`);
    }

    if (!mod.Schema || typeof (mod.Schema as any).parse !== "function") {
      throw new Error(`Widget "${mod.Name}" at ${path} missing Schema export or Schema is not a Zod schema`);
    }

    if (!mod.Component || typeof mod.Component !== "function") {
      throw new Error(
        `Widget "${mod.Name}" at ${path} missing Component export or Component is not a valid React component`,
      );
    }

    if (widgetSchemas[mod.Name]) {
      throw new Error(`Widget "${mod.Name}" at ${path} already used by another widget, widget names must be unique`);
    }

    if (mod.Type == WidgetType.Widget) {
      if (!mod.AllowedLocations || !Array.isArray(mod.AllowedLocations)) {
        // prettier-ignore
        throw new Error(`Widget "${mod.Name}" at ${path} missing AllowedLocations export or AllowedLocations is not an array`);
      }

      for (const location of mod.AllowedLocations) {
        widgetAllowedLocations[location].push(mod.Name);
      }
    }

    if (mod.Type == WidgetType.ClockTheme) {
      widgetThemes.push(mod.Name);
    }

    widgetSchemas[mod.Name] = mod.Schema.prefault({});
    widgetModules[mod.Name] = mod;
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
    console.warn("Config file not found, using defaults");
    configData = configSchema.parse({});
  } else {
    const configFileContents = await readTextFile(CONFIG_FILENAME, { baseDir: BASE_DIRECTORY });
    const parsedConfig = JSON.parse(configFileContents);
    configData = configSchema.parse(parsedConfig);
  }

  await writeTextFile(CONFIG_FILENAME, JSON.stringify(configData, null, 2), { baseDir: BASE_DIRECTORY });
  await writeTextFile(schemaFileName, JSON.stringify(configSchema.toJSONSchema(), null, 2), {
    baseDir: BASE_DIRECTORY,
  });

  const layout = Object.entries(configData.layout).reduce(
    (acc, [location, widgetNames]) => {
      acc[location as WidgetLocation] = widgetNames.map(
        (name: string) => widgetModules[name] as WidgetModuleOfType<WidgetType.Widget>,
      );
      return acc;
    },
    {} as Record<WidgetLocation, WidgetModuleOfType<WidgetType.Widget>[]>,
  );

  const theme =
    (widgetModules[configData.clockTheme] as WidgetModuleOfType<WidgetType.ClockTheme> | undefined) ?? "default";

  return [configData, layout, theme] as const;
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

    // Remove backups beyond the 5 most recent
    for (const backup of backups.slice(CONFIG_BACKUP_LIMIT)) {
      await remove(`${CONFIG_BACKUP_DIR}/${backup.name}`, { baseDir: BASE_DIRECTORY });
      console.log(`Removed old backup: ${backup.name}`);
    }
  }
};
