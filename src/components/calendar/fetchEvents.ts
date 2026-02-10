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

const fetchEvents = async (
  config: Record<string, any>,
  extensions: Record<string, WidgetOfType<WidgetType.CalendarExtension>>,
): Promise<Record<string, CalendarEvent[]>> => {
  const events = [];

  for (const name of config.calendar.extensions) {
    const eventsFromExtension = await extensions[name].Component(config.widgets[name], config.calendar);
    events.push(...eventsFromExtension);
  }

  // Sort events ascending by start time, then alphabetically by title
  events.sort((a, b) => {
    if (a.start.isBefore(b.start)) return -1;
    if (a.start.isAfter(b.start)) return 1;
    return a.title.localeCompare(b.title);
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

export default fetchEvents;
