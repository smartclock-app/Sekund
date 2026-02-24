import { CalendarEvent } from "@/helpers/types";
import { render, screen } from "@testing-library/react";
import dayjs from "dayjs";
import utc from "dayjs/plugin/utc";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import EventItem from "./EventItem";

dayjs.extend(utc);

// Fix "today" to a known non-Monday non-Sunday date for test stability
const TODAY = dayjs("2025-06-18T10:00:00"); // Wednesday

const makeEvent = (overrides: Partial<CalendarEvent> = {}): CalendarEvent => ({
  id: "1",
  title: "Test Event",
  start: TODAY,
  end: TODAY.add(1, "hour"),
  color: "#ff0000",
  ...overrides,
});

describe("EventItem", () => {
  beforeEach(() => {
    vi.useFakeTimers();
    vi.setSystemTime(TODAY.toDate());
  });

  afterEach(() => {
    vi.useRealTimers();
  });

  it("renders a string title", () => {
    render(<EventItem event={makeEvent({ title: "My Event" })} />);
    expect(screen.getByText("My Event")).toBeInTheDocument();
  });

  it("renders an array title as multiple spans", () => {
    render(<EventItem event={makeEvent({ title: ["Part 1", "Part 2"] })} />);
    expect(screen.getByText("Part 1")).toBeInTheDocument();
    expect(screen.getByText("Part 2")).toBeInTheDocument();
  });

  it("shows 'Today' for events starting today", () => {
    render(<EventItem event={makeEvent()} />);
    expect(screen.getByText(/Today/)).toBeInTheDocument();
  });

  it("shows 'Tomorrow' for events starting tomorrow", () => {
    const tomorrow = TODAY.add(1, "day");
    render(<EventItem event={makeEvent({ start: tomorrow, end: tomorrow.add(1, "hour") })} />);
    expect(screen.getByText(/Tomorrow/)).toBeInTheDocument();
  });

  it("shows formatted date for events not today or tomorrow", () => {
    const nextWeek = TODAY.add(7, "day");
    render(<EventItem event={makeEvent({ start: nextWeek, end: nextWeek.add(1, "hour") })} />);
    // Should contain the weekday name
    expect(screen.getByText(/Wednesday/)).toBeInTheDocument();
  });

  it("shows time range for same-day timed events", () => {
    const start = TODAY.hour(14).minute(0);
    const end = TODAY.hour(15).minute(30);
    render(<EventItem event={makeEvent({ start, end })} />);
    expect(screen.getByText(/14:00/)).toBeInTheDocument();
    expect(screen.getByText(/15:30/)).toBeInTheDocument();
  });

  it("shows all-day label for midnight-to-midnight one-day events", () => {
    const start = dayjs("2025-06-20T00:00:00");
    const end = dayjs("2025-06-21T00:00:00");
    render(<EventItem event={makeEvent({ start, end })} />);
    // All-day shows only the start day (no time range)
    expect(screen.queryByText(/:/)).not.toBeInTheDocument();
  });

  it("applies event color as CSS variable", () => {
    const { container } = render(<EventItem event={makeEvent({ color: "#123456" })} />);
    const eventDiv = container.querySelector("[style]") as HTMLElement;
    expect(eventDiv.style.getPropertyValue("--event-color")).toBe("#123456");
  });
});
