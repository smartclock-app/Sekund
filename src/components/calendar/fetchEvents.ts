import { CalendarEvent, WidgetOfType, WidgetType } from "@/helpers/types";
import dayjs from "dayjs";
import isoWeek from "dayjs/plugin/isoWeek";
import utc from "dayjs/plugin/utc";

dayjs.extend(isoWeek);
dayjs.extend(utc);

const MONTHS = [
  "January",
  "February",
  "March",
  "April",
  "May",
  "June",
  "July",
  "August",
  "September",
  "October",
  "November",
  "December",
];

export const fetchEventsForExtension = async (
  extension: string,
  config: Record<string, any>,
  extensions: Record<string, WidgetOfType<WidgetType.CalendarExtension>>,
): Promise<CalendarEvent[]> => {
  const calendarExtension = extensions[extension];
  if (!calendarExtension) return [];
  return calendarExtension.Component(config.widgets[extension], config.calendar);
};

export const fetchEventsByExtension = async (
  config: Record<string, any>,
  extensions: Record<string, WidgetOfType<WidgetType.CalendarExtension>>,
  extensionNames = config.calendar.extensions,
): Promise<Record<string, CalendarEvent[]>> => {
  const extensionEvents: Record<string, CalendarEvent[]> = {};

  await Promise.all(
    extensionNames.map(async (name: string) => {
      extensionEvents[name] = await fetchEventsForExtension(name, config, extensions);
    }),
  );

  return extensionEvents;
};

export const groupEvents = (
  config: Record<string, any>,
  extensionEvents: Record<string, CalendarEvent[]>,
): Record<string, CalendarEvent[]> => {
  const events = Object.values(extensionEvents).flat();

  // Sort events ascending by start time, then alphabetically by title
  events.sort((a, b) => {
    if (a.start.isBefore(b.start)) return -1;
    if (a.start.isAfter(b.start)) return 1;

    let aTitle, bTitle;

    if (Array.isArray(a.title)) aTitle = a.title.join("\n");
    else aTitle = a.title;

    if (Array.isArray(b.title)) bTitle = b.title.join("\n");
    else bTitle = b.title;

    return aTitle.localeCompare(bTitle);
  });

  const currentTime = dayjs.utc();
  const currentWeek = currentTime.isoWeek();
  const weekReference = dayjs.utc("2024-01-01"); // Must be a Monday for isEven calculation below
  const sortedEvents: Record<string, CalendarEvent[]> = {};

  for (const event of events.slice(0, config.calendar.maxEvents)) {
    const eventMonth = MONTHS[event.start.month()];
    const eventYear = event.start.year();
    const eventWeek = event.start.isoWeek();

    let key: string;
    const isEven = Math.floor(weekReference.diff(event.start, "day") / 7) % 2 === 0;
    const title = isEven ? config.calendar.titles.even : config.calendar.titles.odd;
    if (eventWeek == currentWeek || event.start.isBefore(currentTime)) {
      key = title.isNotEmpty ? `This Week - ${title}` : "This Week";
    } else if (eventWeek == currentWeek + 1) {
      key = title.isNotEmpty ? `Next Week - ${title}` : "Next Week";
    } else {
      key = `${eventMonth} ${eventYear}`;
    }

    sortedEvents[key] ??= [];
    sortedEvents[key].push(event);
  }

  return sortedEvents;
};

const fetchEvents = async (
  config: Record<string, any>,
  extensions: Record<string, WidgetOfType<WidgetType.CalendarExtension>>,
): Promise<Record<string, CalendarEvent[]>> => {
  const extensionEvents = await fetchEventsByExtension(config, extensions);
  return groupEvents(config, extensionEvents);
};

export default fetchEvents;
