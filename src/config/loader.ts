import z, { type ZodAny } from "zod";

export const loadConfig = async () => {
  const widgets = import.meta.glob("../widgets/*/index.tsx", { eager: true });

  const widgetConfig: Record<string, ZodAny> = {};

  for (const widget of Object.values(widgets)) {
    const { Name, Schema } = widget as any;

    widgetConfig[Name] = Schema;
  }

  const config = z.object({
    version: z.string().default("1.0.0"),
    widgets: z.object(widgetConfig),
  });

  return config;
};
