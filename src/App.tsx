import "@/App.scss";
import LoadConfig from "@/helpers/config";
import useConfigStore from "./hooks/config";
import useMDNSStore from "./hooks/mdns";

import { info } from "@tauri-apps/plugin-log";
import { useEffect, useRef } from "react";
import Clock from "./components/Clock";
import { WidgetLocation } from "./helpers/types";
import useRemoteStore, { useHttpRequestListener } from "./hooks/remote";

function App() {
  const configLoaded = useRef(false);
  const configStore = useConfigStore();
  const mdnsStore = useMDNSStore();
  const remoteStore = useRemoteStore();

  useHttpRequestListener(event => {
    info(JSON.stringify(event));
  });

  useEffect(() => {
    if (configLoaded.current) return;
    configLoaded.current = true;
    LoadConfig()
      .then(([config, layout, theme]) => {
        configStore.setConfig([config, layout, theme]);
        if (config.remoteConfig?.enabled) {
          if (!mdnsStore.broadcasting) mdnsStore.startBroadcast(mdnsStore.broadcasting, config.remoteConfig);
          if (!remoteStore.running) remoteStore.startServer(config.remoteConfig.port);
        }
      })
      .catch(err => info(`Error loading config: ${err.toString()}`));
  }, []);

  return (
    <div className="container">
      <div className="main">
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
        {configStore.layout.sidebar.map(Widget => (
          <Widget.Component
            key={Widget.Name}
            config={configStore.config.widgets[Widget.Name]}
            location={WidgetLocation.Sidebar}
          />
        ))}
      </div>
    </div>
  );
}

export default App;
