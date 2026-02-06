import { Location, WidgetComponent, WidgetType } from "@/helpers/types";
import z from "zod";

export const Name = "homeassistant";
export const Type = WidgetType.Widget;

export const AllowedLocations = [
  // Location.Main,
  // Location.Sidebar,
  Location.Floating,
] as const;

export const Schema = z.object({
  url: z.url().catch(""),
  token: z.string().catch(""),
  cameras: z
    .array(
      z
        .object({
          id: z.string().catch(""),
          trigger: z.string().catch(""),
          streamUri: z.url().catch(""),
          aspectRatio: z.number().catch(16 / 9),
        })
        .prefault({} as any),
    )
    .default([]),
  cameraWaitTime: z.number().catch(5),
});

export type Config = z.infer<typeof Schema>;

export const Component: WidgetComponent<Config> = ({ config, location }) => {
  return (
    <>
      <h1>HomeAssistant Widget</h1>
      <p>Location: {location}</p>
      <pre>{JSON.stringify(config, null, 2)}</pre>
    </>
  );
};
