import { ClockConfig } from "@/helpers/baseConfig";
import useConfigStore from "@/hooks/useConfigStore";
import { dispatchEvent, EventType } from "@/hooks/useEventListener";
import dayjs from "dayjs";
import { useEffect, useState } from "react";
import styles from "./clock.module.scss";
import getOrdinal from "./getOrdinal";

const DefaultTheme = ({ config, now }: { config: ClockConfig; now: dayjs.Dayjs }) => {
  return (
    <div className={styles.container}>
      <div className={styles.time}>
        <p className={styles.main}>{now.format(`${config.format == "12h" ? "hh" : "HH"}:mm`)}</p>
        <div className={styles.sub}>
          <p>{now.format("A")}</p>
          {config.showSeconds && <p>{now.format("ss")}</p>}
        </div>
      </div>
      <div className={styles.date}>
        {now.format(`dddd D`)}
        <sup>{getOrdinal(now.date())}</sup>
        {now.format(" MMMM YYYY")}
      </div>
    </div>
  );
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

  if (configStore.clockTheme === "default") return <DefaultTheme config={configStore.config.clock} now={now} />;
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
