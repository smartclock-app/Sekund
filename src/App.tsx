import "@/App.scss";
import LoadConfig from "@/helpers/config";
import useConfigStore from "./hooks/config";
import useMDNSStore from "./hooks/mdns";

import { useEffect } from "react";
import { WidgetLocation } from "./helpers/types";
import useRemoteStore, { useHttpRequestListener } from "./hooks/remote";

function App() {
  const configStore = useConfigStore();
  const mdnsStore = useMDNSStore();
  const remoteStore = useRemoteStore();

  useHttpRequestListener(event => {
    console.log(event);
  });

  useEffect(() => {
    LoadConfig()
      .then(([config, layout]) => {
        configStore.setConfig(config, layout);
        if (config.remoteConfig?.enabled) {
          if (!mdnsStore.broadcasting) mdnsStore.startBroadcast(mdnsStore.broadcasting, config.remoteConfig);
          if (!remoteStore.running) remoteStore.startServer(config.remoteConfig.port);
        }
      })
      .catch(console.error);
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
