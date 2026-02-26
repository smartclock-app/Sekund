import { ClockConfig } from "@/helpers/config/base";
import useConfigStore from "@/hooks/useConfigStore";
import { dispatchEvent, EventType } from "@/hooks/useEventListener";
import useNetworkStore from "@/hooks/useNetworkStore";
import dayjs from "dayjs";
import { useEffect, useState } from "react";
import styles from "./clock.module.scss";
import getOrdinal from "./getOrdinal";

const DefaultTheme = ({ config, now, connected }: { config: ClockConfig; now: dayjs.Dayjs; connected: boolean }) => {
  return (
    <div className={styles.container}>
      <div className={styles.time}>
        <p className={styles.main}>{now.format(`${config.format == "12h" ? "hh" : "HH"}:mm`)}</p>
        <div className={styles.sub}>
          {config.format == "12h" && <p>{now.format("A")}</p>}
          {config.showSeconds && <p>{now.format("ss")}</p>}
        </div>
      </div>
      <div className={styles.date}>
        {now.format(`dddd D`)}
        <sup>{getOrdinal(now.date())}</sup>
        {now.format(" MMMM YYYY")}
      </div>
      {!connected && <div id="offline">Offline - Waiting for network</div>}
    </div>
  );
};

const Clock = () => {
  const [now, setNow] = useState(dayjs());
  const configStore = useConfigStore();
  const connected = useNetworkStore(state => state.connected);

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

  if (!connected || !configStore.clockTheme)
    return <DefaultTheme config={configStore.config.clock} now={now} connected={connected} />;
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
