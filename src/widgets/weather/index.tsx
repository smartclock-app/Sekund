import { Location, WidgetComponent, WidgetType } from "@/helpers/types";
import z from "zod";

export const Name = "weather";
export const Type = WidgetType.Widget;

export const AllowedLocations = [Location.Main, Location.Sidebar] as const;

export const Schema = z.object({
  apiKey: z.string().catch(""),
  postcode: z
    .string()
    .regex(/^\d{5}$/, "Postcode must be a 5 digit number")
    .catch(""),
  country: z
    .string()
    .regex(/^[A-Z]{2}$/, "Country code must be 2 characters")
    .catch("GB"),
  units: z.enum(["metric", "imperial"]).catch("metric"),
});

export type Config = z.infer<typeof Schema>;

export const Component: WidgetComponent<Config> = ({ config, location }) => {
  return (
    <>
      <h1>Weather Widget</h1>
      <p>Location: {location}</p>
      <pre>{JSON.stringify(config, null, 2)}</pre>
    </>
  );
};
