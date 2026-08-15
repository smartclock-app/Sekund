import { CalendarEvent } from "@/helpers/types";
import dayjs from "dayjs";
import utc from "dayjs/plugin/utc";
import getOrdinal from "../clock/getOrdinal";

import FittedBox from "../FittedBox";
import styles from "./eventitem.module.scss";

dayjs.extend(utc);

interface EventItemProps {
  event: CalendarEvent;
}

const EventItem: React.FC<EventItemProps> = ({ event }) => {
  /// Check if the event is an all-day event
  ///
  /// If [oneDay] is true (default), it will only return true if the event is exactly one day long
  ///
  /// If [oneDay] is false, it will return true if the event starts and ends at midnight
  const isAllDay = (oneDay = true) => {
    // Check if both dates align with the start of their respective local calendar day
    const startsMidnight = event.start.isSame(event.start.clone().startOf("day"));
    const endsMidnight = event.end.isSame(event.end.clone().startOf("day"));

    // Measure calendar difference rather than exact 24h duration
    const startDay = event.start.clone().startOf("day");
    const endDay = event.end.clone().startOf("day");
    const calendarDays = endDay.diff(startDay, "day");

    const isOneDay = calendarDays === 1 || calendarDays === 0;

    return (isOneDay || !oneDay) && startsMidnight && endsMidnight;
  };

  const formatDate = (date: dayjs.Dayjs, format: string) => {
    if (date.isSame(dayjs(), "day")) return "Today";
    if (date.isSame(dayjs().add(1, "day"), "day")) return "Tomorrow";

    return date.format(format);
  };

  let eventStart: string;
  let eventEnd = "";
  const startDay = formatDate(event.start, `dddd D[${getOrdinal(event.start.date())}]`);
  const isSameMonth = event.start.month() === event.end.month();

  if (isAllDay()) {
    eventStart = startDay;
  } else if (isAllDay(false)) {
    const endPrevDay = event.end.clone().subtract(1, "day");
    const format = `dddd D[${getOrdinal(endPrevDay.date())}]${!isSameMonth ? " MMM" : ""}`;
    eventStart = `${startDay} — ${formatDate(event.end.subtract(1, "day"), format)}`;
  } else if (!event.start.isSame(event.end, "day") && event.end.diff(event.start, "hours") >= 24) {
    const format = `dddd D[${getOrdinal(event.end.date())}]${!isSameMonth ? " MMM" : ""}`;
    eventStart = `${startDay} (${event.start.format("HH:mm")}) -`;
    eventEnd = `${formatDate(event.end, format)} (${event.end.format("HH:mm")})`;
  } else if (event.start.isSame(event.end)) {
    eventStart = `${startDay} (${event.start.format("HH:mm")})`;
  } else {
    eventStart = `${startDay} (${event.start.format("HH:mm")} - ${event.end.format("HH:mm")})`;
  }

  return (
    <div className={styles.event} style={{ "--event-color": event.color } as React.CSSProperties}>
      <p className={styles.title}>
        {typeof event.title === "string" ? <span>{event.title}</span> : event.title.map(t => <span key={t}>{t}</span>)}
      </p>
      <FittedBox className={styles.date}>{eventStart}</FittedBox>
      {!!eventEnd && <FittedBox className={styles.date}>{eventEnd}</FittedBox>}
    </div>
  );
};

export default EventItem;
