import { render, screen } from "@testing-library/react";
import { describe, expect, it } from "vitest";
import FittedBox from "./FittedBox";

describe("FittedBox", () => {
  it("renders children", () => {
    render(<FittedBox>Some text</FittedBox>);
    expect(screen.getByText("Some text")).toBeInTheDocument();
  });

  it("applies className to the inner span", () => {
    render(<FittedBox className="my-class">text</FittedBox>);
    const span = screen.getByText("text");
    expect(span).toHaveClass("my-class");
  });

  it("wraps children in a full-width container", () => {
    const { container } = render(<FittedBox>text</FittedBox>);
    const outer = container.firstChild as HTMLElement;
    expect(outer.style.width).toBe("100%");
  });
});
