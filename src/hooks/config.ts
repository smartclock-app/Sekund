import { create } from "zustand";

interface BearState {
  config: Record<string, any>;
  setConfig: (newConfig: Record<string, any>) => void;
}

const useConfigStore = create<BearState>()(set => ({
  config: {},
  setConfig: newConfig => set({ config: newConfig }),
}));

export default useConfigStore;
