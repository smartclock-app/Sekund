import { Location, WidgetComponent } from "@/helpers/types";
import z from "zod";

export const Name = "watchlist";

export const AllowedLocations = [
  // Location.Main,
  // Location.Sidebar,
  Location.Calendar,
  // Location.Floating,
] as const;

export const Schema = z.object({
  trakt: z
    .object({
      clientId: z.string().catch(""),
      clientSecret: z.string().catch(""),
      accessToken: z.string().catch(""),
      refreshToken: z.string().catch(""),
      redirectUri: z.url().catch(""),
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

export const Component: WidgetComponent<Config> = ({ config, location }) => {
  return (
    <>
      <h1>Watchlist Widget</h1>
      <p>Location: {location}</p>
      <pre>{JSON.stringify(config, null, 2)}</pre>
    </>
  );
};
