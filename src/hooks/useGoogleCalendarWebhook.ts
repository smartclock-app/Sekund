import { fetch } from "@tauri-apps/plugin-http";
import { error, info } from "@tauri-apps/plugin-log";
import TauriWebSocket from "@tauri-apps/plugin-websocket";
import { useEffect, useRef } from "react";
import { Config } from "@/widgets/google";
import useConfigStore from "./useConfigStore";
import { dispatchEvent, EventType } from "./useEventListener";

interface OAuthTokens {
  access_token: string;
  expiry_date: number;
}

type InboundMessage =
  | { type: "registered"; clockId: string }
  | { type: "authenticated"; clockId: string; calendars: string[] }
  | { type: "subscribed"; calendars: string[] }
  | { type: "calendar_updated"; calendarId: string }
  | { type: "token_request"; requestId: string }
  | { type: "error"; message: string };

async function fetchCalendarIds(accessToken: string): Promise<string[]> {
  const res = await fetch("https://www.googleapis.com/calendar/v3/users/me/calendarList", {
    headers: { Authorization: `Bearer ${accessToken}` },
  });
  if (!res.ok) throw new Error(`Failed to fetch calendar list: ${res.status}`);
  const data = (await res.json()) as { items?: { id: string }[] };
  return (data.items ?? []).map(item => item.id);
}

async function refreshAccessToken(config: Config): Promise<OAuthTokens> {
  const res = await fetch("https://oauth2.googleapis.com/token", {
    method: "POST",
    headers: { "Content-Type": "application/x-www-form-urlencoded" },
    body: new URLSearchParams({
      client_id: config.clientId,
      client_secret: config.clientSecret,
      refresh_token: config.refreshToken,
      grant_type: "refresh_token",
    }).toString(),
  });

  if (!res.ok) throw new Error(`Token refresh failed: ${res.status}`);

  const data = (await res.json()) as { access_token: string; expires_in: number };
  return {
    access_token: data.access_token,
    expiry_date: Date.now() + data.expires_in * 1000,
  };
}

const useGoogleCalendarWebhook = () => {
  const wsRef = useRef<TauriWebSocket | null>(null);
  const reconnectTimerRef = useRef<ReturnType<typeof setTimeout> | null>(null);
  const backoffRef = useRef(1000);
  const cancelledRef = useRef(false);

  const scheduleReconnect = () => {
    const delay = backoffRef.current;
    backoffRef.current = Math.min(backoffRef.current * 2, 60_000);
    info(`[GoogleWebhook] Reconnecting in ${delay}ms`);
    reconnectTimerRef.current = setTimeout(() => {
      if (!cancelledRef.current) connect();
    }, delay);
  };

  const connect = async () => {
    const state = useConfigStore.getState();
    const config = state.config.widgets?.google as Config | undefined;

    if (!config?.webhookServerUrl || !config.refreshToken || !config.clientId || !config.clientSecret) {
      info("[GoogleWebhook] Not configured, skipping");
      return;
    }

    // Disconnect any existing socket before reconnecting
    if (wsRef.current) {
      await wsRef.current.disconnect().catch(() => {});
      wsRef.current = null;
    }

    let tokens: OAuthTokens;
    try {
      tokens = await refreshAccessToken(config);
    } catch (e) {
      error(`[GoogleWebhook] Initial token refresh failed: ${e}`);
      scheduleReconnect();
      return;
    }

    // Persist fresh tokens
    await useConfigStore.getState().editConfigByPath("widgets.google", {
      ...(useConfigStore.getState().config.widgets?.google as Config),
      accessToken: tokens.access_token,
      tokenExpiry: new Date(tokens.expiry_date).toISOString(),
    });

    let ws: TauriWebSocket;
    try {
      info(`[GoogleWebhook] Connecting to ${config.webhookServerUrl}`);
      ws = await TauriWebSocket.connect(config.webhookServerUrl);
    } catch (e) {
      error(`[GoogleWebhook] Connection failed: ${e}`);
      scheduleReconnect();
      return;
    }

    if (cancelledRef.current) {
      ws.disconnect().catch(() => {});
      return;
    }

    wsRef.current = ws;
    backoffRef.current = 1000;

    const latestConfig = () => useConfigStore.getState().config.widgets?.google as Config;
    const clockId = () => latestConfig().clockId || null;

    const send = (msg: object) => ws.send(JSON.stringify(msg));

    // Register or resume session
    const savedClockId = clockId();
    if (savedClockId) {
      send({ type: "authenticate", clockId: savedClockId, tokens });
    } else {
      send({ type: "register" });
    }

    const subscribe = async (clockId: string) => {
      const cfg = latestConfig();
      const excluded = new Set(cfg.excludedCalendars);
      let allIds: string[];
      try {
        allIds = await fetchCalendarIds(tokens.access_token);
      } catch (e) {
        error(`[GoogleWebhook] Could not fetch calendar list: ${e}`);
        allIds = ["primary"];
      }
      const calendars = allIds.filter(id => !excluded.has(id));
      info(`[GoogleWebhook] Subscribing to ${calendars.length} calendars`);
      send({ type: "subscribe", clockId, calendars, tokens });
    };

    ws.addListener(async message => {
      if (message.type === "Close") {
        info("[GoogleWebhook] Connection closed");
        wsRef.current = null;
        if (!cancelledRef.current) scheduleReconnect();
        return;
      }

      if (message.type !== "Text") return;

      let msg: InboundMessage;
      try {
        msg = JSON.parse(message.data as string);
      } catch {
        return;
      }

      switch (msg.type) {
        case "registered": {
          info(`[GoogleWebhook] Registered: ${msg.clockId}`);
          await useConfigStore.getState().editConfigByPath("widgets.google", {
            ...latestConfig(),
            clockId: msg.clockId,
          });
          await subscribe(msg.clockId);
          break;
        }

        case "authenticated": {
          info(`[GoogleWebhook] Authenticated, re-subscribing`);
          await subscribe(msg.clockId);
          break;
        }

        case "subscribed": {
          info(`[GoogleWebhook] Watching: ${msg.calendars.join(", ")}`);
          break;
        }

        case "calendar_updated": {
          if (msg.calendarId === "__calendarList__") {
            // Calendar list changed — re-discover and subscribe to any new calendars
            info("[GoogleWebhook] Calendar list changed, re-subscribing");
            const clockId = latestConfig().clockId;
            if (clockId) await subscribe(clockId);
            dispatchEvent(EventType.Refresh);
          } else {
            info(`[GoogleWebhook] Calendar updated: ${msg.calendarId}`);
            dispatchEvent(EventType.Refresh);
          }
          break;
        }

        case "token_request": {
          info("[GoogleWebhook] Server requested fresh tokens");
          try {
            const fresh = await refreshAccessToken(latestConfig());
            tokens = fresh;
            await useConfigStore.getState().editConfigByPath("widgets.google", {
              ...latestConfig(),
              accessToken: fresh.access_token,
              tokenExpiry: new Date(fresh.expiry_date).toISOString(),
            });
            send({ type: "token_response", requestId: msg.requestId, tokens: fresh });
          } catch (e) {
            error(`[GoogleWebhook] Token refresh for server failed: ${e}`);
          }
          break;
        }

        case "error": {
          error(`[GoogleWebhook] Server error: ${msg.message}`);
          if (msg.message?.includes("clock not found")) {
            // Server restarted and lost state — re-register
            await useConfigStore.getState().editConfigByPath("widgets.google", {
              ...latestConfig(),
              clockId: "",
            });
            send({ type: "register" });
          }
          break;
        }
      }
    });
  };

  useEffect(() => {
    cancelledRef.current = false;
    connect();

    return () => {
      cancelledRef.current = true;
      if (reconnectTimerRef.current) clearTimeout(reconnectTimerRef.current);
      wsRef.current?.disconnect().catch(() => {});
      wsRef.current = null;
    };
  }, []);
};

export default useGoogleCalendarWebhook;
