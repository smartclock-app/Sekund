import z from "zod";

export const Name = "weather";

export const Schema = z.object({
  enabled: z.boolean().default(true),
});

export type Config = z.infer<typeof Schema>;

export const Component = ({ config }: { config: Config }) => {
  return (
    <>
      <h1>Weather Widget</h1>
      <pre>{JSON.stringify(config, null, 2)}</pre>
    </>
  );
};
