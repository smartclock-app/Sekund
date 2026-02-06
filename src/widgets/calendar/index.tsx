import { Location, WidgetComponent } from "@/helpers/types";
import z from "zod";

export const Name = "calendar";

export const AllowedLocations = [Location.Sidebar] as const;

export const Schema = z.object({
  maxEvents: z.number().min(1).catch(50),
  titles: z.object({
    odd: z.string().default(""),
    even: z.string().default(""),
  }),
  eventFilter: z.array(z.string()).default([]),
});

export type Config = z.infer<typeof Schema>;

export const Component: WidgetComponent<Config> = ({ config, location }) => {
  return (
    <>
      <h1>Calendar Widget</h1>
      <p>Location: {location}</p>
      <pre>{JSON.stringify(config, null, 2)}</pre>
    </>
  );
};
