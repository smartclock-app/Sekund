import { render, screen } from "@testing-library/react";
import { describe, expect, it } from "vitest";
import ErrorBoundary from "./ErrorBoundary";

const ThrowingComponent = () => {
  throw new Error("Test error");
};

describe("ErrorBoundary", () => {
  it("renders children when there is no error", () => {
    render(
      <ErrorBoundary fallback={<div>Error fallback</div>}>
        <span>Normal content</span>
      </ErrorBoundary>,
    );
    expect(screen.getByText("Normal content")).toBeInTheDocument();
    expect(screen.queryByText("Error fallback")).not.toBeInTheDocument();
  });

  it("renders fallback when a child throws", () => {
    // Suppress the expected console.error from React's error boundary
    const consoleError = console.error;
    console.error = () => {};
    render(
      <ErrorBoundary fallback={<div>Error fallback</div>}>
        <ThrowingComponent />
      </ErrorBoundary>,
    );
    expect(screen.getByText("Error fallback")).toBeInTheDocument();
    console.error = consoleError;
  });
});
