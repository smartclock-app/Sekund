import { WidgetComponent, WidgetLocation } from "@/helpers/types";
import { create } from "zustand";

interface ConfigStoreState {
  config: Record<string, any>;
  layout: Record<WidgetLocation, WidgetComponent[]>;
  setConfig: (newConfig: Record<string, any>, newLayout: Record<WidgetLocation, WidgetComponent[]>) => void;
}

const useConfigStore = create<ConfigStoreState>()(set => ({
  config: {},
  layout: { main: [], sidebar: [] },
  setConfig: (newConfig, newLayout) => set({ config: newConfig, layout: newLayout }),
}));

export default useConfigStore;
