import type { CalendarConfig } from "@/helpers/baseConfig";
import { CalendarEvent, WidgetComponent } from "@/helpers/types";
import useConfigStore from "@/hooks/useConfigStore";
import useEventListener, { EventType } from "@/hooks/useEventListener";
import { memo, useEffect, useState } from "react";
import Card from "../Card";
import EventItem from "./EventItem";
import fetchEvents from "./fetchEvents";

const Calendar: WidgetComponent<CalendarConfig> = memo(() => {
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
          <h1
            style={{
              fontSize: "2.3rem",
              fontWeight: "bold",
              borderBottom: "0.125rem solid #ddd",
              borderRadius: "0.06rem",
              marginBottom: "1rem",
            }}
          >
            {month}
          </h1>
          <div className="events" style={{ display: "flex", flexDirection: "column", gap: "0.5rem" }}>
            {events.map(event => (
              <EventItem key={`${event.title}-${event.start.toISOString()}`} event={event} />
            ))}
          </div>
        </Card>
      ))}
    </>
  );
});

export default Calendar;
