import { BaseDirectory } from "@tauri-apps/plugin-fs";
import dayjs from "dayjs";
import { ZodAny } from "zod";
import { CalendarConfig, ClockConfig } from "./config/base";

export const BASE_DIRECTORY = BaseDirectory.AppData;

export enum WidgetLocation {
  Main = "main",
  Sidebar = "sidebar",
}

export enum WidgetType {
  Widget = "widget",
  CalendarExtension = "calendarExtension",
  ClockTheme = "clockTheme",
}

export type RemoteConfigResult =
  | { status: "ok"; result: Record<string, any> | string }
  | { status: "error"; error: string };
export type RemoteConfigHandler = (data: any) => Promise<RemoteConfigResult> | RemoteConfigResult;

export type WidgetModule =
  | {
      Type: WidgetType.Widget;
      AllowedLocations: WidgetLocation[];
      Schema: ZodAny;
      OnInit?: () => void;
    }
  | {
      Type: WidgetType.CalendarExtension;
      Schema: ZodAny;
      OnInit?: () => void;
      Manager?: React.ComponentType;
    }
  | {
      Type: WidgetType.ClockTheme;
      Schema: ZodAny;
      OnInit?: () => void;
    };
export type WidgetComponentModule = WidgetComponent | CalendarExtensionComponent | ClockThemeComponent;
export type Widget =
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
      Manager?: React.ComponentType;
    }
  | {
      Name: string;
      Type: WidgetType.ClockTheme;
      Schema: ZodAny;
      Component: ClockThemeComponent;
    };

export type WidgetOfType<T extends WidgetType> = Extract<Widget, { Type: T }>;

export interface CalendarEvent {
  id: string;
  title: string | string[];
  start: dayjs.Dayjs;
  end: dayjs.Dayjs;
  color: string;
}

export type WidgetComponent<Config = {}> = React.FC<{ config: Config; location: WidgetLocation }>;
export type CalendarExtensionComponent<Config = {}> = (
  config: Config,
  calendarConfig: CalendarConfig,
) => CalendarEvent[] | Promise<CalendarEvent[]>;
export type ClockThemeComponent<Config = {}> = React.FC<{
  config: Config;
  clockConfig: ClockConfig;
  now: dayjs.Dayjs;
}>;
