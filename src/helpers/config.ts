import { Location, WidgetType } from "@/helpers/types";
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
  const modules = import.meta.glob("../widgets/*/index.{ts,tsx}", { eager: true }) as Record<string, any>;

  const widgetSchemas: Record<string, ZodType> = {};
  const widgetAllowedLocations: Record<Location, string[]> = { main: [], sidebar: [] };

  for (const [path, mod] of Object.entries(modules)) {
    const Name: string = mod.Name;
    const Type: WidgetType = mod.Type;
    const Schema: ZodType = mod.Schema;
    const AllowedLocations: Location[] = mod.AllowedLocations;

    if (typeof Name !== "string") {
      throw new Error(`Widget at ${path} missing Name export or Name is not a string`);
    }

    if (!Type || !Object.values(WidgetType).includes(Type)) {
      throw new Error(`Widget "${Name}" at ${path} missing Type export or Type is not a valid WidgetType`);
    }

    if (!Schema || typeof (Schema as any).parse !== "function") {
      throw new Error(`Widget "${Name}" at ${path} missing Schema export or Schema is not a Zod schema`);
    }

    if (widgetSchemas[Name]) {
      throw new Error(`Widget "${Name}" at ${path} already used by another widget, widget names must be unique`);
    }

    if (Type == WidgetType.Widget) {
      if (!AllowedLocations || !Array.isArray(AllowedLocations)) {
        // prettier-ignore
        throw new Error(`Widget "${Name}" at ${path} missing AllowedLocations export or AllowedLocations is not an array`);
      } else {
        for (const location of AllowedLocations) {
          widgetAllowedLocations[location].push(Name);
        }
      }
    }

    widgetSchemas[Name] = Schema.prefault({});
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

  return configData;
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
