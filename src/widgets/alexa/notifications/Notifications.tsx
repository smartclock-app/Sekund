import useEventListener, { EventType } from "@/hooks/useEventListener";
import { error, info } from "@tauri-apps/plugin-log";
import dayjs from "dayjs";
import { useCallback, useEffect, useRef, useState } from "react";
import { Config } from "..";
import { Notification } from "../util/types";
import useQueryClient from "../util/useQueryClient";
import AlarmCard from "./AlarmCard";
import TimerCard from "./TimerCard";

interface NotificationsProps {
  config: Config;
}

const Notifications: React.FC<NotificationsProps> = ({ config }) => {
  const runOnce = useRef(false);
  const queryClientStore = useQueryClient();
  const [notifications, setNotifications] = useState<{ alarms: Notification[]; timers: Notification[] }>({
    alarms: [],
    timers: [],
  });

  const getNotifications = useCallback(async () => {
    if (!queryClientStore.isInitialized) return;

    info("[Alexa] Refetching notifications");
    let ns: Notification[] = [];
    try {
      ns = await queryClientStore.client!.getNotifications(config.userId);
    } catch (e) {
      return error("[Alexa] Failed to fetch notifications: $e");
    }

    const allDevices = await queryClientStore.client!.getDevices(config.userId);
    const devices = allDevices.filter(d => config.devices.includes(d.accountName));

    const timers: Notification[] = [];
    const alarms: Notification[] = [];

    for (const n of ns) {
      if (n.status != "ON") continue;
      if (devices.length > 0 && !devices.some(d => d.serialNumber == n.deviceSerialNumber)) continue;

      switch (n.type) {
        case "Timer":
          if (config.features.timers) timers.push(n);
          break;
        case "Alarm":
        case "MusicAlarm":
        case "Reminder":
          // If alarm is more than 12 hours away, skip
          if (dayjs(`${n.originalDate!}T${n.snoozedToTime ?? n.originalTime!}`).diff(dayjs()) > 12 * 60 * 60 * 1000) {
            continue;
          }
          if (config.features.alarms) alarms.push(n);
          break;
      }
    }

    setNotifications({ timers, alarms });
  }, [config, queryClientStore]);

  useEffect(() => {
    const initialize = async () => {
      if (!queryClientStore.client?.isInitialized) {
        await queryClientStore.init(config.token);
      }

      if (runOnce.current) return;
      runOnce.current = true;

      await getNotifications();
    };

    initialize();
  }, [config, queryClientStore.client]);

  useEventListener(EventType.Refresh, () => {
    getNotifications();
  });

  return (
    <>
      {notifications.timers.map(timer => (
        <TimerCard key={timer.id} timer={timer} />
      ))}
      {notifications.alarms.map(alarm => (
        <AlarmCard key={alarm.id} alarm={alarm} />
      ))}
    </>
  );
};

export default Notifications;
