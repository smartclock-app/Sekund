import { render, screen } from "@testing-library/react";
import { describe, expect, it } from "vitest";
import Card from "./Card";

describe("Card", () => {
  it("renders children", () => {
    render(<Card><span>Hello</span></Card>);
    expect(screen.getByText("Hello")).toBeInTheDocument();
  });

  it("applies padding by default", () => {
    const { container } = render(<Card>content</Card>);
    const div = container.firstChild as HTMLElement;
    expect(div.style.padding).toBeTruthy();
  });

  it("omits padding when padding={false}", () => {
    const { container } = render(<Card padding={false}>content</Card>);
    const div = container.firstChild as HTMLElement;
    expect(div.style.padding).toBe("");
  });
});
