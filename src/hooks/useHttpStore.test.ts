import { afterEach, beforeEach, describe, expect, it } from "vitest";
import useHttpStore from "./useHttpStore";

describe("useHttpStore", () => {
  beforeEach(() => {
    useHttpStore.setState({ running: false });
  });

  afterEach(() => {
    useHttpStore.setState({ running: false });
  });

  it("starts not running", () => {
    expect(useHttpStore.getState().running).toBe(false);
  });

  it("startServer sets running to true on a successful invoke", async () => {
    await useHttpStore.getState().startServer(8080);
    expect(useHttpStore.getState().running).toBe(true);
  });

  it("stopServer sets running to false", async () => {
    useHttpStore.setState({ running: true });
    await useHttpStore.getState().stopServer();
    expect(useHttpStore.getState().running).toBe(false);
  });

  it("startServer does not throw when invoke fails", async () => {
    // Temporarily break the IPC so invoke rejects
    const original = window.__TAURI_INTERNALS__?.invoke;
    if (window.__TAURI_INTERNALS__) {
      window.__TAURI_INTERNALS__.invoke = async () => {
        throw new Error("IPC error");
      };
    }
    await expect(useHttpStore.getState().startServer(8080)).resolves.not.toThrow();
    if (window.__TAURI_INTERNALS__ && original) {
      window.__TAURI_INTERNALS__.invoke = original;
    }
  });
});
