import { afterEach, beforeEach, describe, expect, it } from "vitest";
import useAlertsStore from "./useAlertsStore";

describe("useAlertsStore", () => {
  beforeEach(() => {
    useAlertsStore.setState({ alerts: {} });
  });

  afterEach(() => {
    useAlertsStore.setState({ alerts: {} });
  });

  it("starts with no alerts", () => {
    expect(useAlertsStore.getState().alerts).toEqual({});
  });

  it("pushAlert() adds an alert", () => {
    useAlertsStore.getState().pushAlert("weather", { title: "No data" });
    const alerts = useAlertsStore.getState().alerts;
    expect(Object.keys(alerts)).toContain("weather");
  });

  it("pushAlert() overwrites an existing alert for the same widget", () => {
    useAlertsStore.getState().pushAlert("weather", { title: "First" });
    useAlertsStore.getState().pushAlert("weather", { title: "Second" });
    expect(useAlertsStore.getState().alerts["weather"]).toEqual({ title: "Second" });
  });

  it("pushAlert() keeps alerts for different widgets separate", () => {
    useAlertsStore.getState().pushAlert("weather", { title: "W" });
    useAlertsStore.getState().pushAlert("trakt", { title: "T" });
    const alerts = useAlertsStore.getState().alerts;
    expect(Object.keys(alerts)).toHaveLength(2);
    expect(alerts["weather"]).toEqual({ title: "W" });
    expect(alerts["trakt"]).toEqual({ title: "T" });
  });

  it("clearAlert() removes the specified alert", () => {
    useAlertsStore.getState().pushAlert("weather", { title: "W" });
    useAlertsStore.getState().clearAlert("weather");
    expect(useAlertsStore.getState().alerts["weather"]).toBeUndefined();
  });

  it("clearAlert() does not affect other alerts", () => {
    useAlertsStore.getState().pushAlert("weather", { title: "W" });
    useAlertsStore.getState().pushAlert("trakt", { title: "T" });
    useAlertsStore.getState().clearAlert("weather");
    expect(useAlertsStore.getState().alerts["trakt"]).toEqual({ title: "T" });
  });

  it("clearAlert() is a no-op for a non-existent key", () => {
    useAlertsStore.getState().clearAlert("nonexistent");
    expect(useAlertsStore.getState().alerts).toEqual({});
  });
});
