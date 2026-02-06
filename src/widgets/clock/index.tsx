import { Location, WidgetComponent } from "@/helpers/types";
import z from "zod";

export const Name = "clock";

export const AllowedLocations = [Location.Main] as const;

export const Schema = z.object({
  format: z.enum(["12h", "24h"]).catch("12h"),
  showSeconds: z.boolean().catch(true),
});

export type Config = z.infer<typeof Schema>;

export const Component: WidgetComponent<Config> = ({ config, location }) => {
  return (
    <>
      <h1>Clock Widget</h1>
      <p>Location: {location}</p>
      <pre>{JSON.stringify(config, null, 2)}</pre>
    </>
  );
};
