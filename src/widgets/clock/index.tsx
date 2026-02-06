import z from "zod";

export const Name = "clock";

export const Schema = z.object({
  enabled: z.boolean().catch(true),
  format: z.enum(["12h", "24h"]).catch("12h"),
  showSeconds: z.boolean().catch(true),
});

export type Config = z.infer<typeof Schema>;

export const Component = ({ config }: { config: Config }) => {
  return (
    <>
      <h1>Clock Widget</h1>
      <pre>{JSON.stringify(config, null, 2)}</pre>
    </>
  );
};
