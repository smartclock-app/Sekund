import { WidgetComponent, WidgetLocation, WidgetType } from "@/helpers/types";
import z from "zod";

export const Name = "alexa";
export const Type = WidgetType.Widget;

export const AllowedLocations = [
  // Location.Main,
  WidgetLocation.Sidebar,
] as const;

export const Schema = z.object({
  features: z
    .object({
      nowplaying: z.boolean().catch(true),
      alarms: z.boolean().catch(true),
      timers: z.boolean().catch(true),
      notes: z.boolean().catch(false),
    })
    .prefault({} as any),
  userId: z.string().catch(""),
  token: z.string().catch(""),
  devices: z.array(z.string()).catch([]),
  radioProviders: z.array(z.string()).catch([]),
  noteColumns: z.number().catch(3),
});

export type Config = z.infer<typeof Schema>;

export const Component: WidgetComponent<Config> = ({ config, location }) => {
  return (
    <>
      <h1>Alexa Widget</h1>
      <p>Location: {location}</p>
      <pre>{JSON.stringify(config, null, 2)}</pre>
    </>
  );
};
