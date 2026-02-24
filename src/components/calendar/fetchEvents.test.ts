import { CalendarEvent, WidgetOfType, WidgetType } from "@/helpers/types";
import dayjs from "dayjs";
import isoWeek from "dayjs/plugin/isoWeek";
import utc from "dayjs/plugin/utc";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import fetchEvents from "./fetchEvents";

dayjs.extend(isoWeek);
dayjs.extend(utc);

const makeEvent = (id: string, title: string, start: dayjs.Dayjs, end?: dayjs.Dayjs): CalendarEvent => ({
  id,
  title,
  start,
  end: end ?? start.add(1, "hour"),
  color: "#000",
});

const makeConfig = (
  extensions: string[] = [],
  widgets: Record<string, any> = {},
  maxEvents = 50,
  titles = { odd: "", even: "" },
) => ({
  calendar: { extensions, maxEvents, titles, eventFilter: [] },
  widgets,
});

const makeExtensions = (
  events: CalendarEvent[],
): Record<string, WidgetOfType<WidgetType.CalendarExtension>> => ({
  testExt: {
    Name: "testExt",
    Type: WidgetType.CalendarExtension,
    Schema: {} as any,
    Component: () => events,
  },
});

describe("fetchEvents", () => {
  let now: dayjs.Dayjs;

  beforeEach(() => {
    now = dayjs.utc();
    vi.useFakeTimers();
    vi.setSystemTime(now.toDate());
  });

  afterEach(() => {
    vi.useRealTimers();
  });

  it("returns empty object when there are no extensions", async () => {
    const result = await fetchEvents(makeConfig(), {});
    expect(result).toEqual({});
  });

  it("returns empty object when extensions produce no events", async () => {
    const config = makeConfig(["testExt"]);
    const result = await fetchEvents(config, makeExtensions([]));
    expect(result).toEqual({});
  });

  it("groups current-week events under 'This Week'", async () => {
    const start = now.startOf("isoWeek").add(1, "day");
    const event = makeEvent("1", "Test", start);
    const config = makeConfig(["testExt"]);
    const result = await fetchEvents(config, makeExtensions([event]));
    expect(Object.keys(result)).toContain("This Week");
    expect(result["This Week"]).toHaveLength(1);
  });

  it("groups past events under 'This Week'", async () => {
    const start = now.subtract(2, "day");
    const event = makeEvent("1", "Past Event", start);
    const config = makeConfig(["testExt"]);
    const result = await fetchEvents(config, makeExtensions([event]));
    expect(Object.keys(result)).toContain("This Week");
  });

  it("groups next-week events under 'Next Week'", async () => {
    const start = now.startOf("isoWeek").add(1, "week").add(1, "day");
    const event = makeEvent("1", "Next", start);
    const config = makeConfig(["testExt"]);
    const result = await fetchEvents(config, makeExtensions([event]));
    expect(Object.keys(result)).toContain("Next Week");
    expect(result["Next Week"]).toHaveLength(1);
  });

  it("groups far-future events under 'Month Year'", async () => {
    const start = now.add(3, "week");
    const event = makeEvent("1", "Future", start);
    const config = makeConfig(["testExt"]);
    const result = await fetchEvents(config, makeExtensions([event]));
    const expectedKey = `${["January","February","March","April","May","June","July","August","September","October","November","December"][start.month()]} ${start.year()}`;
    expect(Object.keys(result)).toContain(expectedKey);
  });

  it("sorts events ascending by start time", async () => {
    const later = now.add(2, "hour");
    const earlier = now.add(1, "hour");
    const events = [
      makeEvent("2", "Later", later),
      makeEvent("1", "Earlier", earlier),
    ];
    const config = makeConfig(["testExt"]);
    const result = await fetchEvents(config, makeExtensions(events));
    const allEvents = Object.values(result).flat();
    expect(allEvents[0].id).toBe("1");
    expect(allEvents[1].id).toBe("2");
  });

  it("sorts events alphabetically by title when start times are equal", async () => {
    const start = now.add(1, "hour");
    const events = [
      makeEvent("2", "Zebra", start),
      makeEvent("1", "Apple", start),
    ];
    const config = makeConfig(["testExt"]);
    const result = await fetchEvents(config, makeExtensions(events));
    const allEvents = Object.values(result).flat();
    expect(allEvents[0].id).toBe("1");
    expect(allEvents[1].id).toBe("2");
  });

  it("sorts array-titled events by joined string", async () => {
    const start = now.add(1, "hour");
    const events = [
      makeEvent("2", "Zebra", start),
      { ...makeEvent("1", "", start), title: ["Apple", "Pie"] },
    ];
    const config = makeConfig(["testExt"]);
    const result = await fetchEvents(config, makeExtensions(events));
    const allEvents = Object.values(result).flat();
    expect(allEvents[0].id).toBe("1");
  });

  it("respects maxEvents limit", async () => {
    const events = Array.from({ length: 10 }, (_, i) =>
      makeEvent(String(i), `Event ${i}`, now.add(i + 1, "hour")),
    );
    const config = makeConfig(["testExt"], {}, 3);
    const result = await fetchEvents(config, makeExtensions(events));
    const total = Object.values(result).flat().length;
    expect(total).toBe(3);
  });

  it("collects events from multiple extensions", async () => {
    const eventA = makeEvent("a", "A", now.add(1, "hour"));
    const eventB = makeEvent("b", "B", now.add(2, "hour"));
    const extensions: Record<string, WidgetOfType<WidgetType.CalendarExtension>> = {
      ext1: { Name: "ext1", Type: WidgetType.CalendarExtension, Schema: {} as any, Component: () => [eventA] },
      ext2: { Name: "ext2", Type: WidgetType.CalendarExtension, Schema: {} as any, Component: () => [eventB] },
    };
    const config = makeConfig(["ext1", "ext2"]);
    const result = await fetchEvents(config, extensions);
    const total = Object.values(result).flat().length;
    expect(total).toBe(2);
  });
});
