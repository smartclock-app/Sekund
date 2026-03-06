import LoadConfig from "@/helpers/config";
import * as Sentry from "@sentry/react";
import { path } from "@tauri-apps/api";
import { invoke } from "@tauri-apps/api/core";
import { onOpenUrl } from "@tauri-apps/plugin-deep-link";
import { attachConsole, error, info, warn } from "@tauri-apps/plugin-log";
import React from "react";
import ReactDOM from "react-dom/client";
import App from "./App";
import handleDeepLink from "./helpers/handleDeepLink";
import useConfigStore from "./hooks/useConfigStore";
import useHttpStore from "./hooks/useHttpStore";
import useMDNSStore from "./hooks/useMDNSStore";

Sentry.init({
  dsn: "https://1d60c7dff4f94a41932ee397143fff67@sentry.danpeak.co.uk/2",
});

await attachConsole();
await onOpenUrl(handleDeepLink);
await invoke("start_network_monitor");

const configStore = useConfigStore.getState();
const mdnsStore = useMDNSStore.getState();
const httpStore = useHttpStore.getState();

try {
  const config = await LoadConfig();
  configStore.setConfig(config);
  if (config.config.remoteConfig?.enabled) {
    if (!mdnsStore.broadcasting) mdnsStore.startBroadcast(mdnsStore.broadcasting, config.config.remoteConfig);
    if (!httpStore.running) httpStore.startServer(config.config.remoteConfig.port);
  }
} catch (e) {
  error(`[Config] Failed to load on startup: ${(e as Error).message}`);
}

const appDirectory = await path.appDataDir();
info(`[Config] App Data Directory: ${appDirectory}`);

const viewport = document.querySelector("html");
info(
  `[Config] Viewport size: ${viewport?.clientWidth}x${viewport?.clientHeight} (${(viewport?.clientWidth ?? 1) / (viewport?.clientHeight ?? 1)})`,
);

ReactDOM.createRoot(document.getElementById("root") as HTMLElement, {
  onUncaughtError: Sentry.reactErrorHandler((error, errorInfo) => {
    warn(`Uncaught error:\n${error}\n${errorInfo.componentStack}`);
  }),
  onCaughtError: Sentry.reactErrorHandler(),
  onRecoverableError: Sentry.reactErrorHandler(),
}).render(
  <React.StrictMode>
    <App />
  </React.StrictMode>,
);
