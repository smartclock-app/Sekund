import { Location, WidgetComponent, WidgetType } from "@/helpers/types";
import z from "zod";

export const Name = "photos";
export const Type = WidgetType.Widget;

export const AllowedLocations = [
  Location.Main,
  // Location.Sidebar,
  // Location.Floating,
] as const;

export const Schema = z.object({
  interval: z.number().min(1).catch(1),
  immichUrl: z.url().catch(""),
  immichAccessToken: z.string().catch(""),
  immichAlbumId: z.string().catch(""),
  immichShareKey: z.string().catch(""),
  useStaticLinks: z.boolean().catch(false),
  images: z.array(z.url()).catch([]),
});

export type Config = z.infer<typeof Schema>;

export const Component: WidgetComponent<Config> = ({ config, location }) => {
  return (
    <>
      <h1>Photos Widget</h1>
      <p>Location: {location}</p>
      <pre>{JSON.stringify(config, null, 2)}</pre>
    </>
  );
};
