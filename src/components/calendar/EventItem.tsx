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

const dateOnlyUtc = (date: dayjs.Dayjs) => dayjs.utc(date).startOf("day");

const EventItem: React.FC<EventItemProps> = ({ event }) => {
  /// Check if the event is an all-day event
  ///
  /// If [oneDay] is true (default), it will only return true if the event is exactly one day long
  ///
  /// If [oneDay] is false, it will return true if the event starts and ends at midnight
  const isAllDay = (oneDay = true) => {
    const isOneDay =
      dateOnlyUtc(event.end).diff(dateOnlyUtc(event.start), "day") === 1 || event.start.isSame(event.end);
    const startsMidnight = event.start.hour() === 0 && event.start.minute() === 0;
    const endsMidnight = event.end.hour() === 0 && event.end.minute() === 0;

    return (isOneDay || !oneDay) && startsMidnight && endsMidnight;
  };

  const formatDate = (date: dayjs.Dayjs, format: string) => {
    if (date.isSame(dayjs(), "day")) return "Today";
    if (date.isSame(dayjs().add(1, "day"), "day")) return "Tomorrow";

    return date.format(format);
  };

  let dateString: string;
  const startDay = formatDate(event.start, `dddd D[${getOrdinal(event.start.date())}]`);
  const isSameMonth = event.start.month() === event.end.month();

  if (isAllDay()) {
    dateString = startDay;
  } else if (isAllDay(false)) {
    const format = `dddd D[${getOrdinal(event.end.date() - 1)}]${!isSameMonth ? " MMM" : ""}`;
    dateString = `${startDay} — ${formatDate(event.end.subtract(1, "day"), format)}`;
  } else if (!event.start.isSame(event.end, "day")) {
    const format = `dddd D[${getOrdinal(event.end.date())}]${!isSameMonth ? " MMM" : ""}`;
    dateString = `${startDay} (${event.start.format("HH:mm")}) - ${formatDate(event.end, format)} (${event.end.format("HH:mm")})`;
  } else if (event.start.isSame(event.end)) {
    dateString = `${startDay} (${event.start.format("HH:mm")})`;
  } else {
    dateString = `${startDay} (${event.start.format("HH:mm")} - ${event.end.format("HH:mm")})`;
  }

  return (
    <div className={styles.event} style={{ "--event-color": event.color } as React.CSSProperties}>
      <p className={styles.title}>
        {typeof event.title === "string" ? <span>{event.title}</span> : event.title.map(t => <span key={t}>{t}</span>)}
      </p>
      <FittedBox maxFontSize={32} minFontSize={16} className={styles.date}>
        {dateString}
      </FittedBox>
    </div>
  );
};

export default EventItem;
