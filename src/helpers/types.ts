export enum WidgetLocation {
  Main = "main",
  Sidebar = "sidebar",
}

export enum WidgetType {
  Widget = "widget",
  CalendarExtension = "calendarExtension",
  ClockTheme = "clockTheme",
}

export interface CalendarEvent {
  id: string;
  title: string;
  start: Date;
  end: Date;
  color: string;
}

export type WidgetComponent<Config = {}> = React.FC<{ config: Config; location: WidgetLocation }>;
export type CalendarExtensionComponent<Config> = (config: Config) => CalendarEvent[] | Promise<CalendarEvent[]>;
export type ClockThemeComponent<Config> = React.FC<{ config: Config }>;
