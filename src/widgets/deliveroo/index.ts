import { WidgetLocation, WidgetType } from "@/helpers/types";
import z from "zod";

export const Type = WidgetType.Widget;

export const AllowedLocations = [WidgetLocation.Sidebar] as const;

export const Schema = z.object({
  token: z.string().catch(""),
  refreshInterval: z.number().describe("Refresh interval in minutes").min(0).max(60).default(10),
});

export type Config = z.infer<typeof Schema>;
