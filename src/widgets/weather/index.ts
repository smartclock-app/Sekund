import { WidgetLocation, WidgetType } from "@/helpers/types";
import z from "zod";

export const Type = WidgetType.Widget;
export const AllowedLocations = [WidgetLocation.Main, WidgetLocation.Sidebar] as const;
export const Schema = z
  .object({
    apiKey: z.string().catch(""),
    postcode: z.string().catch(""),
    country: z
      .string()
      .regex(/^[A-Z]{2}$/, "Country code must be 2 characters")
      .catch("GB"),
    units: z.enum(["metric", "imperial"]).catch("metric"),
  })
  .superRefine((data, ctx) => {
    if (data.country === "GB") {
      const ukPostcodeRegex =
        /(^([Gg][Ii][Rr] 0[Aa]{2})|((([A-Za-z][0-9]{1,2})|(([A-Za-z][A-Ha-hJ-Yj-y][0-9]{1,2})|(([A-Za-z][0-9][A-Za-z])|([A-Za-z][A-Ha-hJ-Yj-y][0-9]?[A-Za-z])))) [0-9][A-Za-z]{2})$)|^$/;

      if (!ukPostcodeRegex.test(data.postcode)) {
        ctx.addIssue({
          code: "invalid_format",
          format: "UK Postcode",
          message: "Postcode must be a valid UK postcode",
          path: ["postcode"],
        });
      }
    }
  });

export type Config = z.infer<typeof Schema>;
