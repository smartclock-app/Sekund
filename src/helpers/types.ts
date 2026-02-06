export enum Location {
  Main = "main",
  Sidebar = "sidebar",
  Floating = "floating",
}

export enum WidgetType {
  Widget = "widget",
  CalendarExtension = "calendarExtension",
}

export interface CalendarEvent {
  id: string;
  title: string;
  start: Date;
  end: Date;
  color: string;
}

export type WidgetComponent<Config> = React.FC<{ config: Config; location: Location }>;
export type CalendarExtensionComponent<Config> = (config: Config) => CalendarEvent[] | Promise<CalendarEvent[]>;
