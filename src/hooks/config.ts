import { create } from "zustand";

interface ConfigStoreState {
  config: Record<string, any>;
  setConfig: (newConfig: Record<string, any>) => void;
}

const useConfigStore = create<ConfigStoreState>()(set => ({
  config: {},
  setConfig: newConfig => set({ config: newConfig }),
}));

export default useConfigStore;
