import { CalendarExtensionComponent, WidgetType } from "@/helpers/types";
import z from "zod";

export const Name = "watchlist";
export const Type = WidgetType.CalendarExtension;

export const Schema = z.object({
  trakt: z
    .object({
      clientId: z.string().catch(""),
      clientSecret: z.string().catch(""),
      accessToken: z.string().catch(""),
      refreshToken: z.string().catch(""),
      tokenExpiry: z.iso.datetime().catch(new Date().toISOString()),
      redirectUri: z.url().or(z.literal("")).catch(""),
      listId: z.string().catch(""),
      includeWatchlist: z.boolean().catch(false),
      includeEpisodesAsShow: z.boolean().catch(false),
    })
    .prefault({} as any),
  prefix: z.string().catch(""),
  color: z.string().catch("#FFF5511D"),
  maxItems: z.number().catch(50),
});

export type Config = z.infer<typeof Schema>;

export const Component: CalendarExtensionComponent<Config> = config => {
  console.log(config);

  return [];
};
