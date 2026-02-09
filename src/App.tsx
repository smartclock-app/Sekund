import "@/App.scss";
import LoadConfig from "@/helpers/config";
import useConfigStore from "./hooks/config";
import useMDNSStore from "./hooks/mdns";

import { info } from "@tauri-apps/plugin-log";
import { useEffect, useRef } from "react";
import Calendar from "./components/Calendar";
import Clock from "./components/Clock";
import ConfigEditor from "./components/ConfigEditor";
import { WidgetLocation } from "./helpers/types";
import useLongPress from "./hooks/longPress";
import useRemoteStore, { useHttpRequestListener } from "./hooks/remote";
import useRouter, { RouterScreen } from "./hooks/router";

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

  return routerStore.currentScreen === RouterScreen.Editor ? (
    <ConfigEditor />
  ) : (
    <div className="container">
      <div className="main" {...longPressProps}>
        {configStore.layout.main.map(Widget => (
          <Widget.Component
            key={Widget.Name}
            config={configStore.config.widgets[Widget.Name]}
            location={WidgetLocation.Main}
          />
        ))}
        <Clock />
      </div>
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
    </div>
  );
}

export default App;
