import useConfigStore from "@/hooks/useConfigStore";
import { dispatchEvent, EventType } from "@/hooks/useEventListener";
import dayjs from "dayjs";
import { useEffect, useState } from "react";

const DefaultTheme = ({ now }: { now: dayjs.Dayjs }) => {
  return <div style={{ background: "#fff8f8f8" }}>{now.format("HH:mm:ss")}</div>;
};

const Clock = () => {
  const [now, setNow] = useState(dayjs());
  const configStore = useConfigStore();

  useEffect(() => {
    const timer = setInterval(() => {
      const time = dayjs();

      if (now.second() !== time.second()) {
        dispatchEvent(EventType.Tick, time);
        if (time.second() % 30 === 0) {
          dispatchEvent(EventType.Refresh, time);
        }
      }

      setNow(time);
    }, 1000);

    return () => clearInterval(timer);
  }, []);

  if (configStore.clockTheme === "default") return <DefaultTheme now={now} />;
  const Component = configStore.clockTheme.Component;
  return (
    <Component
      config={configStore.config.widgets[configStore.clockTheme.Name]}
      clockConfig={configStore.config.clock}
      now={now}
    />
  );
};

export default Clock;
