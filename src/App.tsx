import "@/App.scss";
import LoadConfig from "@/helpers/config";
import useConfigStore from "./hooks/useConfigStore";
import useMDNSStore from "./hooks/useMDNSStore";

import { info } from "@tauri-apps/plugin-log";
import { useEffect, useRef, useState } from "react";
import Calendar from "./components/calendar/Calendar";
import Clock from "./components/clock/Clock";
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

  const sidebarRef = useRef<HTMLDivElement>(null);
  const [sidebarHasChildren, setSidebarHasChildren] = useState(false);

  useEffect(() => {
    if (!sidebarRef.current) return;

    const observer = new MutationObserver(() => {
      setSidebarHasChildren(sidebarRef.current!.children.length > 0);
    });

    observer.observe(sidebarRef.current, { childList: true });
    setSidebarHasChildren(sidebarRef.current.children.length > 0);
    return () => observer.disconnect();
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

  if (!configLoaded.current) return null;

  return routerStore.currentScreen === RouterScreen.Editor ? (
    <ConfigEditor />
  ) : (
    <div className="container">
      <div className="main" style={{ width: sidebarHasChildren ? undefined : "100%" }} {...longPressProps}>
        {configStore.layout.main.map(Widget => (
          <Widget.Component
            key={Widget.Name}
            config={configStore.config.widgets[Widget.Name]}
            location={WidgetLocation.Main}
          />
        ))}
        <Clock />
      </div>
      <div className="sidebar" ref={sidebarRef} style={{ display: sidebarHasChildren ? undefined : "none" }}>
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
