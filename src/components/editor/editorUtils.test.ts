import { describe, expect, it } from "vitest";
import { saveFile, validateJsonSchema } from "./editorUtils";

describe("validateJsonSchema", () => {
  it("returns invalid for a non-JSON string", () => {
    const result = validateJsonSchema("not json");
    expect(result.valid).toBe(false);
    expect(result.error).toBeDefined();
  });

  it("returns invalid with 'Schema not initialized' when no schema is loaded", () => {
    // useConfigStore.configSchema starts as undefined
    const result = validateJsonSchema('{"clock": {}}');
    expect(result.valid).toBe(false);
    expect(result.error).toBe("Schema not initialized");
  });
});

describe("saveFile", () => {
  it("returns an error for the read-only log file", async () => {
    const result = await saveFile("Sekund.log", "some content");
    expect(result.success).toBe(false);
    expect(result.error).toBe("Cannot save read-only log file");
  });

  it("returns an error for an unknown filename", async () => {
    const result = await saveFile("unknown.txt", "content");
    expect(result.success).toBe(false);
    expect(result.error).toBe("Unknown file: unknown.txt");
  });

  it("returns an error for config.json when the JSON is invalid", async () => {
    const result = await saveFile("config.json", "{ bad json }");
    expect(result.success).toBe(false);
    expect(result.error).toBeDefined();
  });

  it("saves variables.css successfully via the store", async () => {
    const result = await saveFile("variables.css", ":root { --color: red; }");
    expect(result.success).toBe(true);
  });
});
