import LoadConfig from "@/helpers/config";
import { attachConsole, error } from "@tauri-apps/plugin-log";
import React from "react";
import ReactDOM from "react-dom/client";
import App from "./App";
import useConfigStore from "./hooks/useConfigStore";
import useMDNSStore from "./hooks/useMDNSStore";
import useRemoteStore from "./hooks/useRemoteStore";

await attachConsole();

const configStore = useConfigStore.getState();
const mdnsStore = useMDNSStore.getState();
const remoteStore = useRemoteStore.getState();

try {
  const config = await LoadConfig();
  configStore.setConfig(config);
  if (config.config.remoteConfig?.enabled) {
    if (!mdnsStore.broadcasting) mdnsStore.startBroadcast(mdnsStore.broadcasting, config.config.remoteConfig);
    if (!remoteStore.running) remoteStore.startServer(config.config.remoteConfig.port);
  }
} catch (e) {
  error(`Failed to load config on startup: ${(e as Error).message}`);
}

ReactDOM.createRoot(document.getElementById("root") as HTMLElement).render(
  <React.StrictMode>
    <App />
  </React.StrictMode>,
);
