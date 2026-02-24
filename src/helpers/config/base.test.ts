import { describe, expect, it } from "vitest";
import { z } from "zod";
import baseConfig, { calendarSchema } from "./base";

describe("remoteConfig schema", () => {
  const schema = z.object(baseConfig).shape.remoteConfig;

  it("uses defaults for missing fields", () => {
    const result = schema.parse({});
    expect(result.enabled).toBe(true);
    expect(result.port).toBe(8080);
    expect(result.password).toBe("");
    expect(result.useBonjour).toBe(true);
    expect(result.bonjourName).toBe("Smart Clock");
    expect(result.toggleDisplayPath).toBe("");
  });

  it("accepts valid values", () => {
    const result = schema.parse({ enabled: false, port: 9090, password: "secret", useBonjour: false });
    expect(result.enabled).toBe(false);
    expect(result.port).toBe(9090);
    expect(result.password).toBe("secret");
  });

  it("falls back to defaults for invalid types", () => {
    const result = schema.parse({ enabled: "yes", port: "abc" });
    expect(result.enabled).toBe(true);
    expect(result.port).toBe(8080);
  });
});

describe("clock schema", () => {
  const schema = z.object(baseConfig).shape.clock;

  it("uses defaults for missing fields", () => {
    const result = schema.parse({});
    expect(result.format).toBe("12h");
    expect(result.showSeconds).toBe(true);
  });

  it("accepts '24h' format", () => {
    const result = schema.parse({ format: "24h" });
    expect(result.format).toBe("24h");
  });

  it("falls back to default for invalid format", () => {
    const result = schema.parse({ format: "invalid" });
    expect(result.format).toBe("12h");
  });
});

describe("calendar schema", () => {
  it("uses defaults for missing fields", () => {
    const result = calendarSchema.parse({});
    expect(result.maxEvents).toBe(50);
    expect(result.titles.odd).toBe("");
    expect(result.titles.even).toBe("");
    expect(result.eventFilter).toEqual([]);
  });

  it("accepts valid maxEvents", () => {
    const result = calendarSchema.parse({ maxEvents: 10 });
    expect(result.maxEvents).toBe(10);
  });

  it("falls back to default for maxEvents below minimum", () => {
    const result = calendarSchema.parse({ maxEvents: 0 });
    expect(result.maxEvents).toBe(50);
  });

  it("accepts custom titles", () => {
    const result = calendarSchema.parse({ titles: { odd: "Odd Week", even: "Even Week" } });
    expect(result.titles.odd).toBe("Odd Week");
    expect(result.titles.even).toBe("Even Week");
  });

  it("accepts eventFilter array", () => {
    const result = calendarSchema.parse({ eventFilter: ["holiday", "birthday"] });
    expect(result.eventFilter).toEqual(["holiday", "birthday"]);
  });
});

describe("orientation schema", () => {
  const schema = z.object(baseConfig).shape.orientation;

  it("defaults to 'landscape'", () => {
    const result = schema.parse(undefined);
    expect(result).toBe("landscape");
  });

  it("accepts 'portrait'", () => {
    const result = schema.parse("portrait");
    expect(result).toBe("portrait");
  });

  it("falls back to 'landscape' for invalid value", () => {
    const result = schema.parse("diagonal");
    expect(result).toBe("landscape");
  });
});
