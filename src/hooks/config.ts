import { WidgetLocation, WidgetModuleOfType, WidgetType } from "@/helpers/types";
import { create } from "zustand";

interface ConfigStoreState {
  config: Record<string, any>;
  layout: Record<WidgetLocation, WidgetModuleOfType<WidgetType.Widget>[]>;
  clockTheme: WidgetModuleOfType<WidgetType.ClockTheme> | "default";
  setConfig: (
    config: [
      Record<string, any>,
      Record<WidgetLocation, WidgetModuleOfType<WidgetType.Widget>[]>,
      WidgetModuleOfType<WidgetType.ClockTheme> | "default",
    ],
  ) => void;
}

const useConfigStore = create<ConfigStoreState>()(set => ({
  config: {},
  layout: { main: [], sidebar: [] },
  clockTheme: "default" as const,
  setConfig: ([config, layout, theme]) => set({ config, layout, clockTheme: theme }),
}));

export default useConfigStore;
