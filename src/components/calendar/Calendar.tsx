import type { CalendarConfig } from "@/helpers/baseConfig";
import { CalendarEvent, WidgetComponent } from "@/helpers/types";
import useConfigStore from "@/hooks/useConfigStore";
import useEventListener, { EventType } from "@/hooks/useEventListener";
import { useEffect, useState } from "react";
import Card from "../Card";
import fetchEvents from "./fetchEvents";

const Calendar: WidgetComponent<CalendarConfig> = () => {
  const configStore = useConfigStore();
  const [events, setEvents] = useState<Record<string, CalendarEvent[]>>({});

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
      {Object.entries(events).map(([month, events]) => (
        <Card key={month}>
          <h2>{month}</h2>
          {events.map(event => (
            <div key={event.id}>
              <strong>{event.title}</strong>
              <div>
                {event.start.format("YYYY-MM-DD HH:mm")} - {event.end.format("YYYY-MM-DD HH:mm")}
              </div>
            </div>
          ))}
        </Card>
      ))}
    </>
  );
};

export default Calendar;
