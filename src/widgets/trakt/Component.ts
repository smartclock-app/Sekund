import { CalendarEvent, CalendarExtensionComponent } from "@/helpers/types";
import useAlertsStore from "@/hooks/useAlertsStore";
import useConfigStore from "@/hooks/useConfigStore";
import useDatabaseStore from "@/hooks/useDatabaseStore";
import { error, info } from "@tauri-apps/plugin-log";
import dayjs from "dayjs";
import QRCode from "qrcode";
import { Config } from ".";
import TraktManager from "./TraktClient";
import { TraktManagerAPIError } from "./TraktClientTypes";

const updateWl = false; // Set to true to force watchlist update on every fetch (for testing)

const ALERT_KEY = "Trakt";
const DEVICE_FLOW_COOLDOWN_MS = 5 * 60 * 1000;

let trakt: TraktManager | null = null;
let deviceFlowInFlight: Promise<void> | null = null;
let deviceFlowCooldownUntil = 0;

const getTraktManager = (config: Config): TraktManager => {
  if (
    !trakt ||
    trakt.clientId !== config.auth.clientId ||
    trakt.clientSecret !== config.auth.clientSecret ||
    trakt.redirectURI !== config.auth.redirectUri
  ) {
    trakt = new TraktManager(
      config.auth.clientId,
      config.auth.clientSecret,
      config.auth.redirectUri,
      config.auth.accessToken,
      config.auth.refreshToken,
    );
    deviceFlowCooldownUntil = 0;
  }

  return trakt;
};

const sleep = (ms: number) => new Promise(resolve => setTimeout(resolve, ms));

// Re-reads the latest persisted config rather than trusting a closure-captured copy, so a
// device-authorization flow that resolves minutes after it started doesn't clobber unrelated
// config edits made in the meantime.
const persistTraktTokens = (manager: TraktManager) => {
  const config = useConfigStore.getState().config.widgets.trakt as Config;
  config.auth.accessToken = manager.accessToken;
  config.auth.refreshToken = manager.refreshToken;
  useConfigStore.getState().editConfigByPath("widgets.trakt", config);
};

const failDeviceFlow = (reason: string) => {
  error(`[Trakt] Device authorization failed: ${reason}`);
  useAlertsStore.getState().pushAlert(ALERT_KEY, {
    title: "Trakt connection failed",
    subtitle: "Will retry automatically in a few minutes.",
  });
  deviceFlowCooldownUntil = Date.now() + DEVICE_FLOW_COOLDOWN_MS;
};

const runDeviceFlow = (manager: TraktManager): Promise<void> => {
  if (deviceFlowInFlight) return deviceFlowInFlight;
  if (Date.now() < deviceFlowCooldownUntil) return Promise.resolve();

  deviceFlowInFlight = (async () => {
    try {
      const { deviceCode, userCode, verificationUrl, expiresIn, interval } = await manager.generateDeviceCode();
      const activateUrl = `${verificationUrl}/${userCode}`;

      info(`[Trakt] Starting device authorization (code ${userCode})`);
      const qrCode = await QRCode.toDataURL(activateUrl).catch(e => {
        error(`[Trakt] Failed to generate QR code: ${e}`);
        return undefined;
      });
      useAlertsStore.getState().pushAlert(ALERT_KEY, {
        title: "Connect Trakt",
        subtitle: `Scan with your phone, or visit ${verificationUrl} and enter code ${userCode}`,
        image: qrCode,
      });

      const deadline = Date.now() + expiresIn * 1000;
      let pollInterval = interval;

      while (Date.now() < deadline) {
        await sleep(pollInterval * 1000);
        const result = await manager.pollDeviceToken(deviceCode);

        if (result.status === "success") {
          info("[Trakt] Device authorization successful");
          persistTraktTokens(manager);
          useAlertsStore.getState().pushAlert(ALERT_KEY, {
            title: "Trakt connected",
            subtitle: "Trakt data will load on the next refresh.",
          });
          return;
        }
        if (result.status === "pending") continue;
        if (result.status === "slow_down") {
          pollInterval += 1;
          continue;
        }

        // denied / expired / not_found / already_used are all terminal
        failDeviceFlow(result.status);
        return;
      }

      failDeviceFlow("expired while polling");
    } catch (e) {
      failDeviceFlow(`${e}`);
    } finally {
      deviceFlowInFlight = null;
    }
  })();

  return deviceFlowInFlight;
};

const Component: CalendarExtensionComponent<Config> = async config => {
  const database = useDatabaseStore.getState();
  if (!database.db) await database.init();

  const events: CalendarEvent[] = [];

  if (!config.auth.clientId || !config.auth.clientSecret) {
    useAlertsStore.getState().pushAlert(ALERT_KEY, {
      title: "Trakt not configured",
      subtitle: "Set clientId and clientSecret in the config file.",
    });
    return events;
  }

  const manager = getTraktManager(config);

  try {
    if (!manager.accessToken || !manager.refreshToken) {
      runDeviceFlow(manager); // backgrounded, does not block other calendar extensions
    } else {
      const currentWatchlist = await database.read<any>("SELECT * FROM watchlist");
      try {
        const [watchlistChanged, itemIds] = await manager.getClockList(config, currentWatchlist);
        useAlertsStore.getState().clearAlert(ALERT_KEY);
        if (watchlistChanged || updateWl) await manager.updateWatchlist(itemIds);
      } catch (e) {
        if (!(e instanceof TraktManagerAPIError) || ![400, 401, 403].includes(e.statusCode)) throw e;

        info("[Trakt] Stored credentials were rejected, restarting device authorization");
        runDeviceFlow(manager); // backgrounded, see above
      }
    }

    // Build events from whatever is currently cached, even while a device authorization runs in
    // the background above -- the calendar keeps showing the last-known watchlist rather than
    // blocking on reauthorization.
    let count = 0;
    const watchlist = await database.read<any>(
      "SELECT * FROM watchlist WHERE nextAirDate IS NOT NULL ORDER BY nextAirDate",
    );
    const watchlistEvents: Map<string, string[]> = new Map();
    for (const item of watchlist) {
      if ((item["nextAirDate"] as string) == null) continue;

      const nextAirDate = dayjs(item["nextAirDate"] as string);
      if (nextAirDate.startOf("day").isBefore(dayjs().startOf("day"))) continue;

      let start;
      if (nextAirDate.isBefore(dayjs().add(1, "day"))) {
        start = nextAirDate.toISOString();
      } else {
        start = nextAirDate.startOf("day").toISOString();
      }

      if (!watchlistEvents.has(start)) {
        if (++count > config.maxItems) break;
        watchlistEvents.set(start, []);
      }
      watchlistEvents.get(start)!.push(item["name"] as string);
    }

    for (const [start, names] of watchlistEvents.entries()) {
      let end = dayjs(start).isBefore(dayjs().add(1, "day")) ? start : dayjs(start).add(1, "day");

      if (dayjs(start).isBefore(dayjs().add(1, "day"))) {
        end = dayjs(start);
      } else {
        end = dayjs(start).add(1, "day");
      }

      names.sort();

      const event: CalendarEvent = {
        id: crypto.randomUUID(),
        title: [config.prefix.trim(), ...names].filter(s => s !== ""),
        start: dayjs(start),
        end: end,
        color: config.color,
      };
      events.push(event);
    }
  } catch (e) {
    error(`[Watchlist] Error fetching watchlist: ${e}`);
  }

  if (manager.accessToken !== config.auth.accessToken || manager.refreshToken !== config.auth.refreshToken) {
    info("[Trakt] Persisting updated credentials");
    persistTraktTokens(manager);
  }

  return events;
};

export default Component;
