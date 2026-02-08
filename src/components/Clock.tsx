import useConfigStore from "@/hooks/config";
import { dispatchEvent, EventType } from "@/hooks/event";
import { info } from "@tauri-apps/plugin-log";
import moment from "moment";
import { Suspense, useEffect, useState } from "react";

const DefaultTheme = ({ now }: { now: moment.Moment }) => {
  return <>{now.format("HH:mm:ss")}</>;
};

const Clock = () => {
  const [now, setNow] = useState(moment());
  const configStore = useConfigStore();

  useEffect(() => {
    const timer = setInterval(() => {
      const time = moment();

      if (now.seconds() !== time.seconds()) {
        dispatchEvent(EventType.Tick);
        if (time.seconds() % 30 === 0) {
          info("Dispatching refresh event");
          dispatchEvent(EventType.Refresh);
        }
      }

      setNow(time);
    }, 1000);

    return () => clearInterval(timer);
  }, []);

  if (configStore.clockTheme === "default") return <DefaultTheme now={now} />;
  const Component = configStore.clockTheme.Component;
  return (
    <Suspense fallback={<h1>Loading</h1>}>
      <Component
        config={configStore.config.widgets[configStore.clockTheme.Name]}
        clockConfig={configStore.config.clock}
        now={now}
      />
    </Suspense>
  );
};

export default Clock;
