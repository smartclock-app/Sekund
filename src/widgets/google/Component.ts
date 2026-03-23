import { CalendarEvent, CalendarExtensionComponent } from "@/helpers/types";
import useConfigStore from "@/hooks/useConfigStore";
import { info } from "@tauri-apps/plugin-log";
import dayjs from "dayjs";
import { Config } from ".";
import GoogleClient from "./GoogleClient";
import { getCache, setCache } from "./eventsCache";

const Component: CalendarExtensionComponent<Config> = async (config, calendarConfig) => {
  const cached = getCache();
  if (cached !== null) return cached;

  const calendarApi = new GoogleClient(config);

  const lists = await calendarApi.getCalendarLists();

  const eventsPromises = lists?.map(async list => {
    const listEvents: CalendarEvent[] = [];
    const recurringEvents = new Set<string>();
    const colour = list.backgroundColor ?? "#1a1a1a";
    const eventsForList = await calendarApi.getEventsForList(list.id!, calendarConfig.maxEvents);

    for (const event of eventsForList) {
      let startDate: dayjs.Dayjs;
      let endDate: dayjs.Dayjs;

      event.summary ??= "No title";

      if (
        calendarConfig.eventFilter.some(filter => (event.summary != null ? RegExp(filter).test(event.summary!) : false))
      ) {
        continue;
      }

      // Skip event if event in this calendar already has same name
      // Doesn't skip if event in another calendar has same name
      if (recurringEvents.has(event.summary)) {
        continue;
      } else {
        recurringEvents.add(event.summary!);
      }

      if (event.start!.date != null) {
        startDate = dayjs(event.start!.date!);
        endDate = dayjs(event.end!.date!);
      } else {
        startDate = dayjs(event.start!.dateTime!);
        endDate = dayjs(event.end!.dateTime!);
      }

      listEvents.push({
        id: event.id,
        title: event.summary!,
        start: startDate,
        end: endDate,
        color: colour,
      });
    }

    return listEvents;
  });

  if (!eventsPromises) return [];

  let updatedCredentials = false;
  if (calendarApi.credentials.accessToken != config.accessToken) {
    info("[Calendar] Updating Google access token");
    config.accessToken = calendarApi.credentials.accessToken;
    updatedCredentials = true;
  }

  if (calendarApi.credentials.refreshToken != config.refreshToken) {
    info("[Calendar] Updating Google refresh token");
    config.refreshToken = calendarApi.credentials.refreshToken;
    updatedCredentials = true;
  }

  if (calendarApi.credentials.tokenExpiry != config.tokenExpiry) {
    info("[Calendar] Updating Google token expiry");
    config.tokenExpiry = calendarApi.credentials.tokenExpiry;
    updatedCredentials = true;
  }

  if (updatedCredentials) {
    useConfigStore.getState().editConfigByPath("widgets.google", config);
  }

  const events = (await Promise.all(eventsPromises)).flat();
  setCache(events);
  return events;
};

export default Component;
