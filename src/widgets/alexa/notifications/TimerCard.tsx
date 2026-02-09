import Card from "@/components/Card";
import useEventListener, { EventType } from "@/hooks/useEventListener";
import dayjs from "dayjs";
import { useState } from "react";
import { Notification } from "../util/types";

interface TimerCardProps {
  timer: Notification;
}

const TimerCard: React.FC<TimerCardProps> = ({ timer }) => {
  const [, setTick] = useState(false);

  useEventListener(EventType.Tick, () => {
    setTick(tick => !tick);
  });

  return (
    <Card>
      <div
        style={{
          display: "flex",
          alignItems: "stretch",
          justifyContent: "space-between",
          fontSize: "2rem",
          fontWeight: "bold",
        }}
      >
        <p>Timer</p>
        <p>{dayjs(dayjs(timer.triggerTime).diff(dayjs())).subtract(1, "h").format("HH:mm:ss")}</p>
      </div>
    </Card>
  );
};

export default TimerCard;
