import "@/App.scss";
import useConfigStore from "./hooks/useConfigStore";

import { listen } from "@tauri-apps/api/event";
import { info } from "@tauri-apps/plugin-log";
import { memo, useEffect, useRef, useState } from "react";
import Alerts from "./components/alerts/Alerts";
import Calendar from "./components/calendar/Calendar";
import Clock from "./components/clock/Clock";
import { EditorScreen } from "./components/editor";
import useOptionsMenu from "./components/menu/Menu";
import RemoteConfig from "./components/RemoteConfig";
import { WidgetLocation, WidgetOfType, WidgetType } from "./helpers/types";
import useAlertsStore from "./hooks/useAlertsStore";
import useRouter, { RouterScreen } from "./hooks/useRouter";

const MemoizedWidget = memo(
  ({
    Component,
    config,
    location,
  }: {
    Component: WidgetOfType<WidgetType.Widget>["Component"];
    config: any;
    location: WidgetLocation;
  }) => <Component config={config} location={location} />,
);

function App() {
  const [longPressProps, Menu] = useOptionsMenu();

  const currentScreen = useRouter(state => state.currentScreen);
  const layout = useConfigStore(state => state.layout);
  const widgetConfigs = useConfigStore(state => state.config.widgets);
  const calendarConfig = useConfigStore(state => state.config.calendar);

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

  useEffect(() => {
    let unlisten: (() => void) | undefined;

    const setupListener = async () => {
      info("[Network] Setting up listener...");
      unlisten = await listen("network-change", event => {
        const { payload } = event;
        info(`[Network] Change detected: ${JSON.stringify(payload)}`);
        useAlertsStore.getState().pushAlert("Network", JSON.stringify(payload));
      });
    };

    setupListener();

    return () => unlisten?.();
  }, []);

  return currentScreen === RouterScreen.Editor ? (
    <EditorScreen />
  ) : (
    <div className="container">
      <Menu />
      <RemoteConfig />
      <div className="main" style={{ width: sidebarHasChildren ? undefined : "100%" }} {...longPressProps}>
        {layout.main.map(Widget => (
          <MemoizedWidget
            key={Widget.Name}
            Component={Widget.Component}
            config={widgetConfigs[Widget.Name]}
            location={WidgetLocation.Main}
          />
        ))}
        <Clock />
      </div>
      <div className="sidebar" ref={sidebarRef} style={{ display: sidebarHasChildren ? undefined : "none" }}>
        <Alerts />
        {layout.sidebar.map(Widget => {
          if (Widget.Name === "calendar") {
            return <Calendar key={Widget.Name} config={calendarConfig} location={WidgetLocation.Sidebar} />;
          }

          return (
            <MemoizedWidget
              key={Widget.Name}
              Component={Widget.Component}
              config={widgetConfigs[Widget.Name]}
              location={WidgetLocation.Sidebar}
            />
          );
        })}
      </div>
    </div>
  );
}

export default App;
