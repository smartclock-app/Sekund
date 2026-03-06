import { afterEach, beforeEach, describe, expect, it } from "vitest";
import useConfigStore from "./useConfigStore";

describe("useConfigStore", () => {
  beforeEach(() => {
    useConfigStore.setState({ config: {}, initialized: false });
  });

  afterEach(() => {
    useConfigStore.setState({ config: {}, initialized: false });
  });

  describe("setConfig", () => {
    it("marks the store as initialized", () => {
      useConfigStore.getState().setConfig({
        config: {},
        layout: { main: [], sidebar: [] },
        clockTheme: undefined,
        calendarExtensions: {},
      });
      expect(useConfigStore.getState().initialized).toBe(true);
    });

    it("stores the provided config", () => {
      const config = { clock: { format: "12h" } };
      useConfigStore.getState().setConfig({
        config,
        layout: { main: [], sidebar: [] },
        clockTheme: undefined,
        calendarExtensions: {},
      });
      expect(useConfigStore.getState().config).toEqual(config);
    });
  });

  describe("editConfigByPath", () => {
    it("sets a top-level value at a dot-separated path", async () => {
      useConfigStore.setState({ config: { clock: { format: "12h" } } });
      await useConfigStore.getState().editConfigByPath("clock.format", "24h");
      expect(useConfigStore.getState().config.clock.format).toBe("24h");
    });

    it("creates intermediate objects that do not yet exist", async () => {
      useConfigStore.setState({ config: {} });
      await useConfigStore.getState().editConfigByPath("a.b.c", 42);
      expect(useConfigStore.getState().config.a.b.c).toBe(42);
    });

    it("sets a deeply nested key in an existing structure", async () => {
      useConfigStore.setState({ config: { remoteConfig: { port: 8080 } } });
      await useConfigStore.getState().editConfigByPath("remoteConfig.port", 9090);
      expect(useConfigStore.getState().config.remoteConfig.port).toBe(9090);
    });

    it("handles a single-segment path (top-level key)", async () => {
      useConfigStore.setState({ config: { version: 1 } });
      await useConfigStore.getState().editConfigByPath("version", 2);
      expect(useConfigStore.getState().config.version).toBe(2);
    });
  });
});
