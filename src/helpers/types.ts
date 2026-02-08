import { ZodAny } from "zod";

export enum WidgetLocation {
  Main = "main",
  Sidebar = "sidebar",
}

export enum WidgetType {
  Widget = "widget",
  CalendarExtension = "calendarExtension",
  ClockTheme = "clockTheme",
}

export type WidgetModule =
  | {
      Name: string;
      Type: WidgetType.Widget;
      AllowedLocations: WidgetLocation[];
      Schema: ZodAny;
      Component: WidgetComponent;
    }
  | {
      Name: string;
      Type: WidgetType.CalendarExtension;
      Schema: ZodAny;
      Component: CalendarExtensionComponent;
    }
  | {
      Name: string;
      Type: WidgetType.ClockTheme;
      Schema: ZodAny;
      Component: ClockThemeComponent;
    };

export type WidgetModuleOfType<T extends WidgetType> = Extract<WidgetModule, { Type: T }>;

export interface CalendarEvent {
  id: string;
  title: string;
  start: Date;
  end: Date;
  color: string;
}

export type WidgetComponent<Config = {}> = React.FC<{ config: Config; location: WidgetLocation }>;
export type CalendarExtensionComponent<Config = {}> = (config: Config) => CalendarEvent[] | Promise<CalendarEvent[]>;
export type ClockThemeComponent<Config = {}> = React.FC<{ config: Config; now: moment.Moment }>;
