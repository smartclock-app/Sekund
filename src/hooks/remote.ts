import { invoke } from "@tauri-apps/api/core";
import { create } from "zustand";

interface RemoteStoreState {
  running: boolean;
  startServer: (port: number) => void;
  stopServer: () => void;
}

const useConfigStore = create<RemoteStoreState>()(set => ({
  running: false,
  startServer: async (port: number) => {
    try {
      const result = await invoke<string>("start_http_server", { port });
      console.log(result);
      set({ running: true });
    } catch (error) {
      console.error("Failed to start server:", error);
      set({ running: false });
    }
  },
  stopServer: async () => {
    try {
      const result = await invoke<string>("stop_http_server");
      console.log(result);
      set({ running: false });
    } catch (error) {
      console.error("Failed to stop server:", error);
    }
  },
}));

export default useConfigStore;
