import { CalendarEvent, CalendarExtensionComponent } from "@/helpers/types";
import useConfigStore from "@/hooks/useConfigStore";
import useDatabaseStore from "@/hooks/useDatabaseStore";
import { error, info } from "@tauri-apps/plugin-log";
import dayjs from "dayjs";
import { Config } from ".";
import TraktManager from "./TraktClient";

const updateWl = false; // Set to true to force watchlist update on every fetch (for testing)

const Component: CalendarExtensionComponent<Config> = async config => {
  const database = useDatabaseStore.getState();
  if (!database.db) await database.init();

  const events: CalendarEvent[] = [];

  // Get watchlist
  let newTraktTokens: [accessToken: string, refreshToken: string] | null = null;
  let watchlistError: any = null;
  try {
    if (
      !config.auth.accessToken ||
      !config.auth.refreshToken ||
      !config.auth.clientId ||
      !config.auth.clientSecret ||
      !config.auth.redirectUri
    ) {
      throw new Error("[Watchlist] Trakt & TMDb API credentials must be set in the config file.");
    }

    const trakt = new TraktManager(
      config.auth.clientId,
      config.auth.clientSecret,
      config.auth.redirectUri,
      config.auth.accessToken,
      config.auth.refreshToken,
    );

    const currentWatchlist = await database.read<any>("SELECT * FROM watchlist");
    const [watchlistChanged, itemIds, tokens] = await trakt.getClockList(config, currentWatchlist);
    if (watchlistChanged || updateWl) await trakt.updateWatchlist(itemIds);
    if (tokens != null) newTraktTokens = [tokens[0], tokens[1]];

    let count = 0;
    const watchlist = await database.read<any>(
      "SELECT * FROM watchlist WHERE nextAirDate IS NOT NULL ORDER BY nextAirDate",
    );
    const watchlistEvents: Map<dayjs.Dayjs, string[]> = new Map();
    for (const item of watchlist) {
      if ((item["nextAirDate"] as string) == null) continue;

      const nextAirDate = dayjs(item["nextAirDate"] as string);
      if (nextAirDate.startOf("day").isBefore(dayjs().startOf("day"))) continue;

      let start;
      if (nextAirDate.isBefore(dayjs().add(1, "day"))) {
        start = nextAirDate;
      } else {
        start = nextAirDate.startOf("day");
      }

      if (!watchlistEvents.has(start)) {
        if (++count > config.maxItems) break;
        watchlistEvents.set(start, []);
      }
      watchlistEvents.get(start)!.push(item["name"] as string);
    }

    for (const [start, names] of watchlistEvents.entries()) {
      let end = start.isBefore(dayjs().add(1, "day")) ? start : start.add(1, "day");

      if (start.isBefore(dayjs().add(1, "day"))) {
        end = start;
      } else {
        end = start.add(1, "day");
      }

      names.sort();

      const event: CalendarEvent = {
        id: crypto.randomUUID(),
        title: config.prefix.trim() !== "" ? `${config.prefix}\n${names.join("\n")}` : names.join("\n"),
        start: start,
        end: end,
        color: config.color,
      };
      events.push(event);
    }
  } catch (e) {
    watchlistError = e;
    console.log(watchlistError); // TODO: Remove this
    error(`[Watchlist] Error fetching watchlist: ${e}`);
  }

  let updatedCredentials = false;
  if (newTraktTokens && newTraktTokens[0] != config.auth.accessToken) {
    info("[Calendar] Updating Trakt access token");
    config.auth.accessToken = newTraktTokens![0];
    updatedCredentials = true;
  }

  if (newTraktTokens && newTraktTokens[1] != config.auth.refreshToken) {
    info("[Calendar] Updating Trakt refresh token");
    config.auth.refreshToken = newTraktTokens![1];
    updatedCredentials = true;
  }

  if (updatedCredentials) {
    useConfigStore.getState().editConfigByPath("widgets.trakt", config);
  }

  return events;
};

export default Component;
