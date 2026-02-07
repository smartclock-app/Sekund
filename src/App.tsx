import "@/App.scss";
import LoadConfig from "@/helpers/config";
import useConfigStore from "./hooks/config";
import useMDNSStore from "./hooks/mdns";

import { useEffect } from "react";
import { WidgetLocation } from "./helpers/types";

function App() {
  const configStore = useConfigStore();
  const mdnsStore = useMDNSStore();

  useEffect(() => {
    LoadConfig()
      .then(([config, layout]) => configStore.setConfig(config, layout))
      .catch(console.log);
  }, []);

  useEffect(() => {
    if (!mdnsStore.broadcasting && configStore?.config?.remoteConfig?.enabled) {
      mdnsStore.startBroadcast(mdnsStore.broadcasting, configStore.config.remoteConfig);
    }
  }, [configStore.config]);

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
