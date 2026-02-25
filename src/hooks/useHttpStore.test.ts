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
});
