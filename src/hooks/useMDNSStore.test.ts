import { afterEach, beforeEach, describe, expect, it } from "vitest";
import useMDNSStore from "./useMDNSStore";

describe("useMDNSStore", () => {
  beforeEach(() => {
    useMDNSStore.setState({ broadcasting: false });
  });

  afterEach(() => {
    useMDNSStore.setState({ broadcasting: false });
  });

  it("starts not broadcasting", () => {
    expect(useMDNSStore.getState().broadcasting).toBe(false);
  });

  it("startBroadcast is a no-op when the broadcasting guard is true", async () => {
    // Guard: if the passed-in 'broadcasting' flag is true, return early
    await useMDNSStore.getState().startBroadcast(true, { port: 8080, bonjourName: "Test" });
    expect(useMDNSStore.getState().broadcasting).toBe(false);
  });

  it("sets broadcasting to true on a successful invoke", async () => {
    // IPC mock returns null for 'start_mdns' (success)
    await useMDNSStore.getState().startBroadcast(false, { port: 8080, bonjourName: "Test" });
    expect(useMDNSStore.getState().broadcasting).toBe(true);
  });
});
