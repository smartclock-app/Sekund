import useConfigStore from "@/hooks/config";
import { dispatchEvent, EventType } from "@/hooks/event";
import moment from "moment";
import { useEffect, useState } from "react";

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
        if (now.seconds() % 30 === 0) dispatchEvent(EventType.Refresh);
      }

      setNow(time);
    }, 100);

    return () => clearInterval(timer);
  }, []);

  if (configStore.clockTheme === "default") return <DefaultTheme now={now} />;
  const Component = configStore.clockTheme.Component;
  return <Component config={configStore.config.widgets[configStore.clockTheme.Name]} now={now} />;
};

export default Clock;
