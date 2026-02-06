import { Location } from "@/helpers/types";
import { BaseDirectory, exists, readTextFile, writeTextFile } from "@tauri-apps/plugin-fs";
import z, { type ZodType } from "zod";

const baseConfig = {
  version: z.string().catch("1.0.0"),
  checkNetwork: z.boolean().catch(true),
  orientation: z.enum(["portrait", "landscape"]).catch("landscape"),
};

export default async () => {
  const modules = import.meta.glob("../widgets/*/index.tsx", { eager: true }) as Record<string, any>;

  const widgetSchemas: Record<string, ZodType> = {};
  const widgetAllowedLocations: Record<Location, string[]> = { main: [], sidebar: [], calendar: [], floating: [] };

  for (const [path, mod] of Object.entries(modules)) {
    const Name: string = mod.Name;
    const Schema: ZodType = mod.Schema;
    const AllowedLocations: Location[] = mod.AllowedLocations;

    if (typeof Name !== "string") {
      throw new Error(`Widget at ${path} missing Name export or Name is not a string`);
    }
    if (!Schema || typeof (Schema as any).parse !== "function") {
      throw new Error(`Widget "${Name}" at ${path} missing Schema export or Schema is not a Zod schema`);
    }
    if (!Array.isArray(AllowedLocations)) {
      // prettier-ignore
      throw new Error(`Widget "${Name}" at ${path} missing AllowedLocations export or AllowedLocations is not an array`);
    }
    if (widgetSchemas[Name]) {
      throw new Error(`Widget "${Name}" at ${path} already used by another widget, widget names must be unique`);
    }

    widgetSchemas[Name] = Schema.prefault({});

    for (const location of AllowedLocations) {
      widgetAllowedLocations[location].push(Name);
    }
  }

  const configSchema = z.object({
    $schema: z.literal("./schema.json").catch("./schema.json"),
    ...baseConfig,
    widgets: z.object(widgetSchemas).prefault({}),
    layout: z
      .object({
        main: z.array(z.enum(widgetAllowedLocations.main)).prefault([]),
        sidebar: z.array(z.enum(widgetAllowedLocations.sidebar)).prefault([]),
        calendar: z.array(z.enum(widgetAllowedLocations.calendar)).prefault([]),
        floating: z.array(z.enum(widgetAllowedLocations.floating)).prefault([]),
      })
      .prefault({}),
  });

  let configData;
  if (!(await exists("config.json", { baseDir: BaseDirectory.AppConfig }))) {
    console.warn("Config file not found, using defaults");
    configData = configSchema.parse({});
  } else {
    const configFileContents = await readTextFile("config.json", { baseDir: BaseDirectory.AppConfig });
    const parsedConfig = JSON.parse(configFileContents);
    configData = configSchema.parse(parsedConfig);
  }

  await writeTextFile("config.json", JSON.stringify(configData, null, 2), { baseDir: BaseDirectory.AppConfig });
  await writeTextFile("schema.json", JSON.stringify(configSchema.toJSONSchema(), null, 2), {
    baseDir: BaseDirectory.AppConfig,
  });

  return configData;
};
