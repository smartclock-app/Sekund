import "@/App.scss";
import LoadConfig from "@/helpers/config";
import useConfigStore from "./hooks/useConfigStore";
import useMDNSStore from "./hooks/useMDNSStore";

import { info } from "@tauri-apps/plugin-log";
import { useEffect, useRef } from "react";
import Calendar from "./components/Calendar";
import Clock from "./components/Clock";
import ConfigEditor from "./components/ConfigEditor";
import { WidgetLocation } from "./helpers/types";
import useLongPress from "./hooks/useLongPress";
import useRemoteStore, { useHttpRequestListener } from "./hooks/useRemoteStore";
import useRouter, { RouterScreen } from "./hooks/useRouter";

function App() {
  const routerStore = useRouter();
  const configLoaded = useRef(false);
  const configStore = useConfigStore();
  const mdnsStore = useMDNSStore();
  const remoteStore = useRemoteStore();
  const longPressProps = useLongPress(() => {
    info("Long press detected");
    routerStore.navigate(RouterScreen.Editor);
  });

  useHttpRequestListener(event => {
    info(JSON.stringify(event));
    return { status: "ok", result: { message: "Hello from SmartClock!" } };
  });

  useEffect(() => {
    if (configLoaded.current) return;
    configLoaded.current = true;
    LoadConfig()
      .then(loadedConfig => {
        configStore.setConfig(loadedConfig);
        if (loadedConfig.config.remoteConfig?.enabled) {
          if (!mdnsStore.broadcasting)
            mdnsStore.startBroadcast(mdnsStore.broadcasting, loadedConfig.config.remoteConfig);
          if (!remoteStore.running) remoteStore.startServer(loadedConfig.config.remoteConfig.port);
        }
      })
      .catch(err => info(`Error loading config: ${err.toString()}`));
  }, []);

  if (!configStore.initialized) return null;

  return routerStore.currentScreen === RouterScreen.Editor ? (
    <ConfigEditor />
  ) : (
    <div className="container">
      <div
        className="main"
        style={{ width: configStore.layout.sidebar.length > 0 ? undefined : "100%" }}
        {...longPressProps}
      >
        {configStore.layout.main.map(Widget => (
          <Widget.Component
            key={Widget.Name}
            config={configStore.config.widgets[Widget.Name]}
            location={WidgetLocation.Main}
          />
        ))}
        <Clock />
      </div>
      {configStore.layout.sidebar.length > 0 && (
        <div className="sidebar">
          {configStore.layout.sidebar.map(Widget => {
            if (Widget.Name === "calendar") {
              return <Calendar key="calendar" config={configStore.config.calendar} location={WidgetLocation.Sidebar} />;
            }

            return (
              <Widget.Component
                key={Widget.Name}
                config={configStore.config.widgets[Widget.Name]}
                location={WidgetLocation.Sidebar}
              />
            );
          })}
        </div>
      )}
    </div>
  );
}

export default App;
