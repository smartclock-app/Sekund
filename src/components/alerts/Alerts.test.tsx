import useAlertsStore from "@/hooks/useAlertsStore";
import { cleanup, render, screen } from "@testing-library/react";
import { afterEach, beforeEach, describe, expect, it } from "vitest";
import Alerts from "./Alerts";

describe("Alerts", () => {
  beforeEach(() => {
    useAlertsStore.setState({ alerts: {} });
  });

  // Explicitly unmount rendered components before resetting the store to avoid
  // React 18 "update not wrapped in act()" warnings from Zustand subscriptions.
  afterEach(() => {
    cleanup();
    useAlertsStore.setState({ alerts: {} });
  });

  it("renders nothing when there are no alerts", () => {
    const { container } = render(<Alerts />);
    expect(container).toBeEmptyDOMElement();
  });

  it("renders a card with the widget name and message for a single alert", () => {
    useAlertsStore.setState({ alerts: { weather: "No data available" } });
    render(<Alerts />);
    expect(screen.getByText("weather")).toBeInTheDocument();
    expect(screen.getByText("No data available")).toBeInTheDocument();
  });

  it("renders a card for each alert when multiple alerts are present", () => {
    useAlertsStore.setState({ alerts: { weather: "Error A", trakt: "Error B" } });
    render(<Alerts />);
    expect(screen.getByText("weather")).toBeInTheDocument();
    expect(screen.getByText("Error A")).toBeInTheDocument();
    expect(screen.getByText("trakt")).toBeInTheDocument();
    expect(screen.getByText("Error B")).toBeInTheDocument();
  });
});
