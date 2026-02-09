import type { CalendarConfig } from "@/helpers/baseConfig";
import { WidgetComponent } from "@/helpers/types";

const Calendar: WidgetComponent<CalendarConfig> = ({ config }) => {
  return (
    <>
      <h1>Calendar</h1>
      <pre>{JSON.stringify(config, null, 2)}</pre>
    </>
  );
};

export default Calendar;
