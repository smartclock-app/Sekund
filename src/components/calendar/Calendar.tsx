import type { CalendarConfig } from "@/helpers/config/base";
import { CalendarEvent, WidgetComponent } from "@/helpers/types";
import useConfigStore from "@/hooks/useConfigStore";
import useEventListener, { CalendarExtensionEventsUpdatedDetail, EventType } from "@/hooks/useEventListener";
import { error } from "@tauri-apps/plugin-log";
import { memo, useEffect, useMemo, useState } from "react";
import Card from "../Card";
import EventItem from "./EventItem";
import { fetchEventsByExtension, groupEvents } from "./fetchEvents";

const Calendar: WidgetComponent<CalendarConfig> = memo(() => {
  const configStore = useConfigStore();
  const [eventsByExtension, setEventsByExtension] = useState<Record<string, CalendarEvent[]>>({});

  const events = useMemo(
    () => groupEvents(configStore.config, eventsByExtension),
    [configStore.config, eventsByExtension],
  );

  useEffect(() => {
    let isMounted = true;
    fetchEventsByExtension(configStore.config, configStore.calendarExtensions)
      .then(events => isMounted && setEventsByExtension(events))
      .catch(e => error(`[Calendar] ${e}`));
    return () => {
      isMounted = false;
    };
  }, [configStore.config, configStore.calendarExtensions]);

  useEventListener(EventType.Refresh, async () => {
    const nonPushExtensions = configStore.config.calendar.extensions.filter(
      (extension: string) => !configStore.calendarExtensions[extension]?.ProvidesPushUpdates,
    );

    if (nonPushExtensions.length === 0) return;

    const refreshed = await fetchEventsByExtension(
      configStore.config,
      configStore.calendarExtensions,
      nonPushExtensions,
    );
    setEventsByExtension(previous => ({ ...previous, ...refreshed }));
  });

  useEventListener(EventType.CalendarExtensionEventsUpdated, event => {
    const detail = event.detail as CalendarExtensionEventsUpdatedDetail | undefined;
    if (!detail) return;
    if (!configStore.config.calendar.extensions.includes(detail.extension)) return;

    setEventsByExtension(previous => ({
      ...previous,
      [detail.extension]: detail.events,
    }));
  });

  return (
    <>
      {Object.entries(configStore.calendarExtensions).map(([name, ext]) =>
        ext.Manager ? <ext.Manager key={name} /> : null,
      )}
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
              <EventItem key={`${event.title}-${event.start.toISOString()}`} event={event} />
            ))}
          </div>
        </Card>
      ))}
    </>
  );
});

export default Calendar;
