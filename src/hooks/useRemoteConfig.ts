import { RemoteConfigHandler } from "@/helpers/types";
import { invoke } from "@tauri-apps/api/core";
import { BaseDirectory, readTextFile } from "@tauri-apps/plugin-fs";
import { create } from "zustand";
import useConfigStore from "./useConfigStore";
import useVariablesStore from "./useVariablesStore";

interface RemoteConfigStoreState {
  handlers: Record<string, RemoteConfigHandler>;
  addHandler: (name: string, handler: RemoteConfigHandler) => void;
  getHandler: (name: string) => RemoteConfigHandler | undefined;
}

const useRemoteConfigStore = create<RemoteConfigStoreState>()(set => ({
  handlers: {
    get_config: () => {
      const config = useConfigStore.getState().config;
      return { status: "ok", result: config };
    },
    set_config: data => {
      useConfigStore.getState().editConfig(data);
      setTimeout(() => window.location.reload(), 100);
      return { status: "ok", result: "Config updated" };
    },
    get_variables: () => {
      const variables = useVariablesStore.getState().variables;
      return { status: "ok", result: variables };
    },
    set_variables: data => {
      useVariablesStore.getState().setVariables(data);
      setTimeout(() => window.location.reload(), 100);
      return { status: "ok", result: "Variables updated" };
    },
    get_logs: async () => {
      const logs = await readTextFile("Sekund.log", { baseDir: BaseDirectory.AppLog });
      const last100Lines = logs
        .split("\n")
        .slice(-100)
        .join("\n")
        .replaceAll(/\[\d{4}-\d{2}-\d{2}\]|\[(?!INFO|WARN|ERROR|DEBUG|TRACE)(?!\d{2}:\d{2}:\d{2})[^\]]+\](?=\[)/gm, "");
      return { status: "ok", result: last100Lines };
    },
    refresh: () => {
      setTimeout(() => window.location.reload(), 100);
      return { status: "ok", result: "Refresh event dispatched" };
    },
    display_on: async () => {
      try {
        await invoke("turn_display_on");
        return { status: "ok", result: "Display turned on" };
      } catch (e) {
        return { status: "error", error: String(e) };
      }
    },
    display_off: async () => {
      try {
        await invoke("turn_display_off");
        return { status: "ok", result: "Display turned off" };
      } catch (e) {
        return { status: "error", error: String(e) };
      }
    },
    get_display_status: async () => {
      try {
        const adminActive = await invoke<boolean>("is_display_admin_active");
        return { status: "ok", result: { adminActive } };
      } catch (e) {
        return { status: "error", error: String(e) };
      }
    },
  },
  addHandler: (name, handler) => {
    if (useRemoteConfigStore.getState().handlers[name]) {
      throw new Error(`Handler with name ${name} already exists`);
    }

    set(prev => ({
      handlers: { ...prev.handlers, [name]: handler },
    }));
  },
  getHandler: name => {
    const handler = useRemoteConfigStore.getState().handlers[name] as RemoteConfigHandler | undefined;
    return handler;
  },
}));

export default useRemoteConfigStore;
