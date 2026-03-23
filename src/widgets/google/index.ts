import { WidgetType } from "@/helpers/types";
import useGoogleCalendarWebhook from "@/widgets/google/useGoogleCalendarWebhook";
import z from "zod";

export const Type = WidgetType.CalendarExtension;

export const Schema = z.object({
  clientId: z.string().catch(""),
  clientSecret: z.string().catch(""),
  accessToken: z.string().catch(""),
  refreshToken: z.string().catch(""),
  tokenExpiry: z.iso.datetime().catch(new Date().toISOString()),
  webhookServerUrl: z.string().catch("ws://localhost:3000/ws"),
  clockId: z.string().catch(""),
  excludedCalendars: z.array(z.string()).catch([]),
});

export type Config = z.infer<typeof Schema>;

export const Manager = () => {
  useGoogleCalendarWebhook();
  return null;
};
