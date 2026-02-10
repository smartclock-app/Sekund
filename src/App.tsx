import "@/App.scss";
import useConfigStore from "./hooks/useConfigStore";

import { info } from "@tauri-apps/plugin-log";
import { memo, useEffect, useRef, useState } from "react";
import Calendar from "./components/calendar/Calendar";
import Clock from "./components/clock/Clock";
import ConfigEditor from "./components/ConfigEditor";
import { WidgetLocation, WidgetOfType, WidgetType } from "./helpers/types";
import useLongPress from "./hooks/useLongPress";
import { useHttpRequestListener } from "./hooks/useRemoteStore";
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
  const routerStore = useRouter();
  const layout = useConfigStore(state => state.layout);
  const widgetConfigs = useConfigStore(state => state.config.widgets);
  const calendarConfig = useConfigStore(state => state.config.calendar);
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

  return routerStore.currentScreen === RouterScreen.Editor ? (
    <ConfigEditor />
  ) : (
    <div className="container">
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
        {layout.sidebar.map(Widget => {
          if (Widget.Name === "calendar") {
            return <Calendar key="calendar" config={calendarConfig} location={WidgetLocation.Sidebar} />;
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
