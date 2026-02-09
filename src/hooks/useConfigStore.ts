import { saveConfig } from "@/helpers/config";
import { WidgetLocation, WidgetOfType, WidgetType } from "@/helpers/types";
import { create } from "zustand";

interface ConfigStoreState {
  initialized: boolean;
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
  initialized: false,
  config: {},
  layout: { main: [], sidebar: [] },
  clockTheme: "default" as const,
  calendarExtensions: {},
  setConfig: config => set({ ...config, initialized: true }),
  editConfig: async config => {
    await saveConfig(config);
    set({ config });
  },
}));

export default useConfigStore;
