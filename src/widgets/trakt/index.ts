import { WidgetType } from "@/helpers/types";
import z from "zod";

export const Type = WidgetType.CalendarExtension;

export const Schema = z.object({
  auth: z
    .object({
      clientId: z.string().catch(""),
      clientSecret: z.string().catch(""),
      accessToken: z.string().catch(""),
      refreshToken: z.string().catch(""),
      redirectUri: z.url().or(z.literal("")).catch(""),
    })
    .prefault({} as any),
  listId: z.string().catch(""),
  includeWatchlist: z.boolean().catch(false),
  includeEpisodesAsShow: z.boolean().catch(false),
  prefix: z.string().catch(""),
  color: z.string().catch("#FFF5511D"),
  maxItems: z.number().catch(50),
});

export type Config = z.infer<typeof Schema>;
