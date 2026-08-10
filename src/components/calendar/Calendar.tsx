import type { CalendarConfig } from "@/helpers/config/base";
import { CalendarEvent, WidgetComponent } from "@/helpers/types";
import useConfigStore from "@/hooks/useConfigStore";
import useEventListener, { EventType } from "@/hooks/useEventListener";
import { error } from "@tauri-apps/plugin-log";
import { memo, useEffect, useRef, useState } from "react";
import Card from "../Card";
import EventItem from "./EventItem";
import fetchEvents from "./fetchEvents";

const Calendar: WidgetComponent<CalendarConfig> = memo(() => {
  const configStore = useConfigStore();
  const [events, setEvents] = useState<Record<string, CalendarEvent[]>>({});
  // Prevents overlapping fetch cycles (e.g. a 30s Refresh tick firing while a prior
  // fetch is still in flight), which would let widgets like Trakt read and refresh
  // the same not-yet-rotated OAuth token twice.
  const isFetchingRef = useRef(false);

  useEffect(() => {
    let isMounted = true;

    if (!isFetchingRef.current) {
      isFetchingRef.current = true;
      fetchEvents(configStore.config, configStore.calendarExtensions)
        .then(events => isMounted && setEvents(events))
        .catch(e => error(`[Calendar] ${e}`))
        .finally(() => {
          isFetchingRef.current = false;
        });
    }

    return () => {
      isMounted = false;
    };
  }, [configStore.config, configStore.calendarExtensions]);

  useEventListener(EventType.Refresh, async () => {
    if (isFetchingRef.current) return;
    isFetchingRef.current = true;
    try {
      const events = await fetchEvents(configStore.config, configStore.calendarExtensions);
      setEvents(events);
    } catch (e) {
      error(`[Calendar] ${e}`);
    } finally {
      isFetchingRef.current = false;
    }
  });

  return (
    <>
      {Object.entries(events).map(([month, events]) => (
        <Card key={month}>
          <h1
            style={{
              fontSize: "2.3rem",
              fontWeight: "bold",
              borderBottom: "0.125rem solid rgb(193, 193, 193)",
              borderRadius: "0.06rem",
              marginBottom: "var(--card-padding, 1rem)",
            }}
          >
            {month}
          </h1>
          <div className="events" style={{ display: "flex", flexDirection: "column", gap: "0.5rem" }}>
            {events.map(event => (
              <EventItem key={`${event.id}-${event.color}`} event={event} />
            ))}
          </div>
        </Card>
      ))}
    </>
  );
});

export default Calendar;
