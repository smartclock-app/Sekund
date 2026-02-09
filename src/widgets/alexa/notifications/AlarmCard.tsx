import Card from "@/components/Card";
import dayjs from "dayjs";
import { Notification } from "../util/types";

interface AlarmCardProps {
  alarm: Notification;
}

const AlarmCard: React.FC<AlarmCardProps> = ({ alarm }) => {
  const triggerTime = `${alarm.originalDate!}T${alarm.snoozedToTime ?? alarm.originalTime!}`;
  const trigger = dayjs(triggerTime);

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
        <p>{alarm.reminderLabel ?? "Alarm"}</p>
        <p>{trigger.format("hh:mm A")}</p>
      </div>
    </Card>
  );
};

export default AlarmCard;
