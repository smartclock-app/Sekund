import { WidgetOfType, WidgetType } from "@/helpers/types";

const fetchEvents = async (
  config: Record<string, any>,
  extensions: Record<string, WidgetOfType<WidgetType.CalendarExtension>>,
) => {
  const events = [];

  for (const name of config.calendar.extensions) {
    const eventsFromExtension = await extensions[name].Component(config.widgets[name]);
    events.push(...eventsFromExtension);
  }

  // Sort events ascending by start time, then alphabetically by title
  events.sort((a, b) => {
    if (a.start.isBefore(b.start)) return -1;
    if (a.start.isAfter(b.start)) return 1;
    return a.title.localeCompare(b.title);
  });

  return events;
};

export default fetchEvents;
