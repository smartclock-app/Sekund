import { Location } from "@/helpers/types";
import { BaseDirectory, exists, readTextFile, writeTextFile } from "@tauri-apps/plugin-fs";
import z, { type ZodType } from "zod";

const baseConfig = {
  version: z.string().catch("1.0.0"),
  checkNetwork: z.boolean().catch(true),
  orientation: z.enum(["portrait", "landscape"]).catch("landscape"),
};

const emptyLocations = { main: [], sidebar: [], calendar: [], floating: [] };

export default async () => {
  const modules = import.meta.glob("../widgets/*/index.tsx", { eager: true }) as Record<string, any>;

  const widgetSchemas: Record<string, ZodType> = {};
  const defaultWidgetConfig: Record<string, any> = {};

  const widgetAllowedLocations: Record<Location, Set<string>> = {
    main: new Set(),
    sidebar: new Set(),
    calendar: new Set(),
    floating: new Set(),
  };

  for (const [path, mod] of Object.entries(modules)) {
    const Name: unknown = mod && mod.Name;
    const Schema: unknown = mod && mod.Schema;

    if (typeof Name !== "string") {
      console.warn(`Skipping widget at ${path}: missing or invalid Name export`);
      continue;
    }
    if (!Schema || typeof (Schema as any).parse !== "function") {
      console.warn(`Skipping widget "${Name}" at ${path}: missing or invalid Schema export`);
      continue;
    }
    if (widgetSchemas[Name]) {
      throw new Error(`Duplicate widget Name "${Name}" found at ${path}`);
    }

    widgetSchemas[Name] = Schema as ZodType;
    defaultWidgetConfig[Name] = (Schema as ZodType).parse({});

    for (const location of mod.AllowedLocations as Location[]) {
      widgetAllowedLocations[location].add(mod.Name);
    }
  }

  const configSchema = z.object({
    $schema: z.literal("./schema.json").catch("./schema.json"),
    ...baseConfig,
    widgets: z.object(widgetSchemas).default(defaultWidgetConfig),
    layout: z
      .object({
        main: z.array(z.enum(Array.from(widgetAllowedLocations.main))).default([]),
        sidebar: z.array(z.enum(Array.from(widgetAllowedLocations.sidebar))).default([]),
        calendar: z.array(z.enum(Array.from(widgetAllowedLocations.calendar))).default([]),
        floating: z.array(z.enum(Array.from(widgetAllowedLocations.floating))).default([]),
      })
      .catch({ ...emptyLocations }),
  });

  let configData;
  if (!(await exists("config.json", { baseDir: BaseDirectory.AppConfig }))) {
    console.warn("Config file not found, using defaults");
    configData = configSchema.parse({});
  } else {
    configData = configSchema.parse(
      JSON.parse(await readTextFile("config.json", { baseDir: BaseDirectory.AppConfig })),
    );
  }

  await writeTextFile("config.json", JSON.stringify(configData, null, 2), { baseDir: BaseDirectory.AppConfig });
  await writeTextFile("schema.json", JSON.stringify(configSchema.toJSONSchema(), null, 2), {
    baseDir: BaseDirectory.AppConfig,
  });

  return configData;
};
