import z from "zod";

const remoteConfigSchema = z.object({
  enabled: z.boolean().catch(true),
  port: z.number().catch(8080),
  password: z.string().catch(""),
  useBonjour: z.boolean().catch(true),
  bonjourName: z.string().catch("Smart Clock"),
  toggleDisplayPath: z.string().catch(""),
});

export type RemoteConfigConfig = z.infer<typeof remoteConfigSchema>;

const clockSchema = z.object({
  format: z.enum(["12h", "24h"]).catch("12h"),
  showSeconds: z.boolean().catch(true),
});

export type ClockConfig = z.infer<typeof clockSchema>;

const baseConfig = {
  orientation: z.enum(["portrait", "landscape"]).catch("landscape"),
  checkNetwork: z.boolean().catch(true),
  remoteConfig: remoteConfigSchema.prefault({} as any),
  clock: clockSchema.prefault({} as any),
};

export default baseConfig;
