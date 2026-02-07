import { ClockThemeComponent, WidgetType } from "@/helpers/types";
import z from "zod";

export const Name = "photos";
export const Type = WidgetType.ClockTheme;

export const Schema = z.object({
  refreshInterval: z
    .number()
    .min(1)
    .catch(1)
    .describe("Multiple of 30 second interval for how often to change the photo."),
  immichUrl: z.url().catch(""),
  immichAccessToken: z.string().catch(""),
  immichAlbumId: z.string().catch(""),
  immichShareKey: z.string().catch(""),
  useStaticLinks: z
    .boolean()
    .catch(false)
    .describe("Whether to use the static image links provided in the config instead of fetching from Immich."),
  images: z.array(z.url()).catch([]).describe("Array of image URLs to display when useStaticLinks is true."),
});

export type Config = z.infer<typeof Schema>;

export const Component: ClockThemeComponent<Config> = ({ config }) => {
  return (
    <>
      <h1>Photos Theme</h1>
      <pre>{JSON.stringify(config, null, 2)}</pre>
    </>
  );
};
