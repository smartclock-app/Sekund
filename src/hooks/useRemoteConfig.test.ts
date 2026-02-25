import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import useRemoteConfigStore from "./useRemoteConfig";

describe("useRemoteConfig", () => {
  const originalHandlers = useRemoteConfigStore.getState().handlers;

  beforeEach(() => {
    useRemoteConfigStore.setState({ handlers: { ...originalHandlers } });
  });

  afterEach(() => {
    useRemoteConfigStore.setState({ handlers: { ...originalHandlers } });
  });

  describe("getHandler", () => {
    it("returns a registered handler by name", () => {
      const handler = useRemoteConfigStore.getState().getHandler("get_config");
      expect(typeof handler).toBe("function");
    });

    it("returns undefined for an unknown handler name", () => {
      const handler = useRemoteConfigStore.getState().getHandler("nonexistent");
      expect(handler).toBeUndefined();
    });
  });

  describe("addHandler", () => {
    it("registers a new handler", () => {
      const fn = vi.fn().mockReturnValue({ status: "ok", result: "pong" });
      useRemoteConfigStore.getState().addHandler("ping", fn);
      const handler = useRemoteConfigStore.getState().getHandler("ping");
      expect(handler).toBe(fn);
    });

    it("throws when adding a duplicate handler name", () => {
      const fn = vi.fn();
      useRemoteConfigStore.getState().addHandler("unique_cmd", fn);
      expect(() => useRemoteConfigStore.getState().addHandler("unique_cmd", fn)).toThrow(
        "Handler with name unique_cmd already exists",
      );
    });
  });

  describe("built-in handlers", () => {
    it("get_config returns ok status with config data", () => {
      const result = useRemoteConfigStore.getState().getHandler("get_config")!({});
      expect((result as any).status).toBe("ok");
    });

    it("get_variables returns ok status", () => {
      const result = useRemoteConfigStore.getState().getHandler("get_variables")!({});
      expect((result as any).status).toBe("ok");
    });

    it("set_config returns ok status", () => {
      const result = useRemoteConfigStore.getState().getHandler("set_config")!({});
      expect((result as any).status).toBe("ok");
    });

    it("set_variables returns ok status and result message", () => {
      const result = useRemoteConfigStore.getState().getHandler("set_variables")!(":root {}");
      expect((result as any).status).toBe("ok");
      expect((result as any).result).toBe("Variables updated");
    });

    it("refresh returns ok status and result message", () => {
      const result = useRemoteConfigStore.getState().getHandler("refresh")!({});
      expect((result as any).status).toBe("ok");
      expect((result as any).result).toBe("Refresh event dispatched");
    });

    it("get_logs returns ok status with log content", async () => {
      // IPC mock returns "" for plugin:fs|read_text_file
      const result = await (useRemoteConfigStore.getState().getHandler("get_logs")!({}) as Promise<any>);
      expect(result.status).toBe("ok");
      expect(typeof result.result).toBe("string");
    });
  });
});
