import LoadConfig from "@/helpers/config";
import { path } from "@tauri-apps/api";
import { attachConsole, error, info } from "@tauri-apps/plugin-log";
import React from "react";
import ReactDOM from "react-dom/client";
import App from "./App";
import useConfigStore from "./hooks/useConfigStore";
import useHttpStore from "./hooks/useHttpStore";
import useMDNSStore from "./hooks/useMDNSStore";

await attachConsole();

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

ReactDOM.createRoot(document.getElementById("root") as HTMLElement).render(
  <React.StrictMode>
    <App />
  </React.StrictMode>,
);
