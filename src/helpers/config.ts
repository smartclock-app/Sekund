import { BaseDirectory, exists, readTextFile, writeTextFile } from "@tauri-apps/plugin-fs";
import z, { type ZodType } from "zod";

const baseConfig = {
  version: z.string().catch("1.0.0"),
  checkNetwork: z.boolean().catch(true),
  orientation: z.enum(["portrait", "landscape"]).catch("landscape"),
};

export const loadConfig = async () => {
  const modules = import.meta.glob("../widgets/*/index.tsx", { eager: true }) as Record<string, any>;

  const widgetSchemas: Record<string, ZodType> = {};
  const defaultWidgetConfig: Record<string, any> = {};

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
  }

  const configSchema = z.object({
    $schema: z.literal("./schema.json").catch("./schema.json"),
    ...baseConfig,
    widgets: z.object(widgetSchemas).default(defaultWidgetConfig),
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
