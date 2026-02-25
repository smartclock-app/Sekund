import z from "zod";

const remoteConfigSchema = z.object({
  enabled: z.boolean().catch(true),
  port: z.number().catch(8080),
  password: z.string().catch(""),
  useBonjour: z.boolean().catch(true),
  bonjourName: z.string().catch("Sekund"),
  toggleDisplayPath: z.string().catch(""),
});

export type RemoteConfigConfig = z.infer<typeof remoteConfigSchema>;

const clockSchema = z.object({
  format: z.enum(["12h", "24h"]).catch("12h"),
  showSeconds: z.boolean().catch(true),
});

export type ClockConfig = z.infer<typeof clockSchema>;

export const calendarSchema = z.object({
  maxEvents: z.number().min(1).catch(50),
  titles: z
    .object({
      odd: z.string().catch(""),
      even: z.string().catch(""),
    })
    .prefault({} as any),
  eventFilter: z.array(z.string()).catch([]),
});

export type CalendarConfig = z.infer<typeof calendarSchema>;

const baseConfig = {
  orientation: z.enum(["portrait", "landscape"]).catch("landscape"),
  checkNetwork: z.boolean().catch(true),
  remoteConfig: remoteConfigSchema.prefault({} as any),
  clock: clockSchema.prefault({} as any),
  calendar: calendarSchema.prefault({} as any),
};

export default baseConfig;
