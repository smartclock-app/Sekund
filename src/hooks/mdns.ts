import { invoke } from "@tauri-apps/api/core";
import { create } from "zustand";

interface MDNSStoreState {
  broadcasting: boolean;
  startBroadcast: (broadcasting: boolean, remoteConfig: { port: number; bonjourName: string }) => void;
}

const useMDNSStore = create<MDNSStoreState>()(set => ({
  broadcasting: false,
  startBroadcast: async (broadcasting, remoteConfig) => {
    if (broadcasting) return;

    try {
      await invoke("start_mdns", {
        port: remoteConfig.port,
        name: remoteConfig.bonjourName,
      });
      console.log("mDNS broadcast started");
      set({ broadcasting: true });
    } catch (error) {
      console.error("Failed to start mDNS:", error);
      set({ broadcasting: false });
    }
  },
}));

export default useMDNSStore;
