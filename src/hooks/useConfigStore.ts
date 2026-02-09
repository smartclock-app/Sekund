import { saveConfig } from "@/helpers/config";
import { WidgetLocation, WidgetOfType, WidgetType } from "@/helpers/types";
import { create } from "zustand";

interface ConfigStoreState {
  config: Record<string, any>;
  layout: Record<WidgetLocation, WidgetOfType<WidgetType.Widget>[]>;
  clockTheme: WidgetOfType<WidgetType.ClockTheme> | "default";
  calendarExtensions: Record<string, WidgetOfType<WidgetType.CalendarExtension>>;
  setConfig: (config: {
    config: Record<string, any>;
    layout: Record<WidgetLocation, WidgetOfType<WidgetType.Widget>[]>;
    clockTheme: WidgetOfType<WidgetType.ClockTheme> | "default";
    calendarExtensions: Record<string, WidgetOfType<WidgetType.CalendarExtension>>;
  }) => void;
  editConfig: (config: Record<string, any>) => Promise<void>;
}

const useConfigStore = create<ConfigStoreState>()(set => ({
  config: {},
  layout: { main: [], sidebar: [] },
  clockTheme: "default" as const,
  calendarExtensions: {},
  setConfig: set,
  editConfig: async config => {
    await saveConfig(config);
    set({ config });
  },
}));

export default useConfigStore;
