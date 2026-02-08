import { invoke } from "@tauri-apps/api/core";
import { listen } from "@tauri-apps/api/event";
import { useEffect } from "react";
import { create } from "zustand";

interface RemoteStoreState {
  running: boolean;
  startServer: (port: number) => void;
  stopServer: () => void;
}

const useRemoteStore = create<RemoteStoreState>()(set => ({
  running: false,
  startServer: async (port: number) => {
    try {
      await invoke<string>("start_http_server", { port });
      set({ running: true });
    } catch {}
  },
  stopServer: async () => {
    try {
      await invoke<string>("stop_http_server");
      set({ running: false });
    } catch {}
  },
}));

export default useRemoteStore;

interface HttpRequest {
  method: string;
  path: string;
  headers: [string, string][];
  body: string;
}

export const useHttpRequestListener = (callback: (event: HttpRequest) => void) => {
  useEffect(() => {
    const unlisten = listen<HttpRequest>("http-request", e => callback(e.payload));

    return () => {
      unlisten.then(fn => fn());
    };
  }, []);
};
