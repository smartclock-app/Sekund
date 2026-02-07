import { WidgetLocation, WidgetModuleOfType, WidgetType } from "@/helpers/types";
import { create } from "zustand";

interface ConfigStoreState {
  config: Record<string, any>;
  layout: Record<WidgetLocation, WidgetModuleOfType<WidgetType.Widget>[]>;
  setConfig: (
    newConfig: Record<string, any>,
    newLayout: Record<WidgetLocation, WidgetModuleOfType<WidgetType.Widget>[]>,
  ) => void;
}

const useConfigStore = create<ConfigStoreState>()(set => ({
  config: {},
  layout: { main: [], sidebar: [] },
  setConfig: (newConfig, newLayout) => set({ config: newConfig, layout: newLayout }),
}));

export default useConfigStore;
