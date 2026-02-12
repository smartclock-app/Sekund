import { saveConfig } from "@/helpers/config";
import { WidgetLocation, WidgetOfType, WidgetType } from "@/helpers/types";
import { ZodType } from "zod";
import { create } from "zustand";

interface ConfigStoreState {
  initialized: boolean;
  configSchema: ZodType | undefined;
  config: Record<string, any>;
  layout: Record<WidgetLocation, WidgetOfType<WidgetType.Widget>[]>;
  clockTheme: WidgetOfType<WidgetType.ClockTheme> | undefined;
  calendarExtensions: Record<string, WidgetOfType<WidgetType.CalendarExtension>>;
  version: string;
  setConfig: (config: {
    config: Record<string, any>;
    layout: Record<WidgetLocation, WidgetOfType<WidgetType.Widget>[]>;
    clockTheme: WidgetOfType<WidgetType.ClockTheme> | undefined;
    calendarExtensions: Record<string, WidgetOfType<WidgetType.CalendarExtension>>;
  }) => void;
  editConfig: (config: Record<string, any>) => Promise<void>;
  editConfigByPath: (path: string, value: any) => Promise<void>;
}

const useConfigStore = create<ConfigStoreState>()(set => ({
  initialized: false,
  configSchema: undefined,
  config: {},
  layout: { main: [], sidebar: [] },
  clockTheme: undefined,
  calendarExtensions: {},
  version: "",
  setConfig: config => set({ ...config, initialized: true }),
  editConfig: async config => {
    await saveConfig(config);
    set({ config });
  },
  editConfigByPath: async (path: string, value: any) => {
    const config = useConfigStore.getState().config;
    const keys = path.split(".");
    let current = config;
    for (let i = 0; i < keys.length - 1; i++) {
      if (!current[keys[i]]) current[keys[i]] = {};
      current = current[keys[i]];
    }
    current[keys[keys.length - 1]] = value;
    await saveConfig(config);
    set({ config });
  },
}));

export default useConfigStore;
