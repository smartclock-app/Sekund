import { invoke } from "@tauri-apps/api/core";
import { listen } from "@tauri-apps/api/event";
import { useEffect } from "react";
import { create } from "zustand";

interface HttpStoreState {
  running: boolean;
  startServer: (port: number) => void;
  stopServer: () => void;
}

const useHttpStore = create<HttpStoreState>()(set => ({
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

export default useHttpStore;

interface HttpRequest {
  id: number;
  method: string;
  path: string;
  headers: [string, string][];
  body: string;
}

export const useHttpRequestListener = (
  callback: (
    event: HttpRequest,
  ) => Promise<{ status: "ok"; result: Record<string, any> | string } | { status: "error"; error: string }>,
) => {
  useEffect(() => {
    const unlisten = listen<HttpRequest>("http-request", async e => {
      const response = await callback(e.payload);
      console.log(response);
      await invoke("http_respond", {
        id: e.payload.id,
        responseBody: JSON.stringify(response),
      });
    });

    return () => {
      unlisten.then(fn => fn());
    };
  }, []);
};
