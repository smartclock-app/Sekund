import { WidgetLocation, WidgetType } from "@/helpers/types";
import z from "zod";

export const Type = WidgetType.Widget;
export const AllowedLocations = [WidgetLocation.Sidebar] as const;

export const Schema = z.object({
  auth: z
    .object({
      clientId: z.string().catch(""),
      clientSecret: z.string().catch(""),
      accessToken: z.string().catch(""),
      refreshToken: z.string().catch(""),
      tokenExpiry: z.iso.datetime().catch(new Date().toISOString()),
    })
    .prefault({} as any),
  maxEvents: z.number().min(1).catch(50),
  titles: z
    .object({
      odd: z.string().catch(""),
      even: z.string().catch(""),
    })
    .prefault({} as any),
  eventFilter: z.array(z.string()).catch([]),
});

export type Config = z.infer<typeof Schema>;
