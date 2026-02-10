import type { CalendarConfig } from "@/helpers/baseConfig";
import { CalendarEvent, WidgetComponent } from "@/helpers/types";
import useConfigStore from "@/hooks/useConfigStore";
import useEventListener, { EventType } from "@/hooks/useEventListener";
import { useEffect, useState } from "react";
import fetchEvents from "./fetchEvents";

const Calendar: WidgetComponent<CalendarConfig> = () => {
  const configStore = useConfigStore();
  const [events, setEvents] = useState<CalendarEvent[]>([]);

  useEffect(() => {
    let isMounted = true;
    fetchEvents(configStore.config, configStore.calendarExtensions).then(events => isMounted && setEvents(events));
    return () => {
      isMounted = false;
    };
  }, [configStore.config, configStore.calendarExtensions]);

  useEventListener(EventType.Refresh, async () => {
    const events = await fetchEvents(configStore.config, configStore.calendarExtensions);
    setEvents(events);
  });

  return (
    <>
      <h1>Calendar</h1>
      <pre>{JSON.stringify(events, null, 2)}</pre>
    </>
  );
};

export default Calendar;
