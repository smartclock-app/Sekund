import { WidgetLocation, WidgetType } from "@/helpers/types";
import z from "zod";

export const Type = WidgetType.Widget;

export const AllowedLocations = [WidgetLocation.Sidebar] as const;

export const Schema = z.object({});

export type Config = z.infer<typeof Schema>;
