import { fetch } from "@tauri-apps/plugin-http";
import dayjs, { Dayjs } from "dayjs";
import { create } from "zustand";

interface NetworkStoreState {
  connected: boolean;
  lastProbe: Dayjs;
  lastHash: string;
  setHash: (hash: string) => void;
  probe: () => Promise<void>;
}

const useNetworkStore = create<NetworkStoreState>(set => ({
  connected: false,
  lastProbe: dayjs(),
  lastHash: "",
  setHash: (hash: string) => set({ lastHash: hash }),
  probe: async () => {
    try {
      const response = await fetch("https://www.google.com/generate_204", { cache: "no-cache", connectTimeout: 5000 });
      set({ connected: response.ok });
    } catch (error) {
      set({ connected: false });
    }
  },
}));

export default useNetworkStore;
