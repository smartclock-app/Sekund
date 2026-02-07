import { Location, WidgetComponent, WidgetType } from "@/helpers/types";
import z from "zod";

export const Name = "updater";
export const Type = WidgetType.Widget;

export const AllowedLocations = [
  // Location.Main,
  Location.Sidebar,
] as const;

export const Schema = z.object({
  updateInterval: z.number().catch(5),
});

export type Config = z.infer<typeof Schema>;

export const Component: WidgetComponent<Config> = ({ config, location }) => {
  return (
    <>
      <h1>Updater Widget</h1>
      <p>Location: {location}</p>
      <pre>{JSON.stringify(config, null, 2)}</pre>
    </>
  );
};
