import { WidgetLocation, WidgetType } from "@/helpers/types";
import z from "zod";

export const Type = WidgetType.Widget;
export const AllowedLocations = [WidgetLocation.Sidebar] as const;

export const Schema = z.object({
  budgets: z
    .array(
      z.object({
        syncId: z.string(),
        accounts: z
          .array(
            z.object({
              name: z.string().catch("Account"),
              icon: z.url().or(z.literal("")).catch(""),
            }),
          )
          .prefault([]),
      }),
    )
    .prefault([]),
  syncInterval: z.number().catch(60),
});

export type Config = z.infer<typeof Schema>;
