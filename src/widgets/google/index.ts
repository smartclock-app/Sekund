import { WidgetType } from "@/helpers/types";
import z from "zod";

export const Type = WidgetType.CalendarExtension;

export const Schema = z.object({
  clientId: z.string().catch(""),
  clientSecret: z.string().catch(""),
  accessToken: z.string().catch(""),
  refreshToken: z.string().catch(""),
  tokenExpiry: z.iso.datetime().catch(new Date().toISOString()),
});

export type Config = z.infer<typeof Schema>;
