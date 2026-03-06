import { fetch } from "@tauri-apps/plugin-http";
import dayjs, { Dayjs } from "dayjs";
import { create } from "zustand";

interface NetworkStoreState {
  connected: boolean;
  lastProbe: Dayjs;
  lastHash: string;
  setHash: (hash: string) => void;
  probing: boolean;
  probe: () => Promise<void>;
  _debounceTimer: ReturnType<typeof setTimeout> | null;
  _pendingResolvers: Array<() => void>;
}

const DEBOUNCE_MS = 500;

const useNetworkStore = create<NetworkStoreState>((set, get) => ({
  connected: true,
  lastProbe: dayjs(),
  lastHash: "",
  setHash: (hash: string) => set({ lastHash: hash }),
  probing: false,
  _debounceTimer: null,
  _pendingResolvers: [],
  probe: async () => {
    return new Promise<void>(resolve => {
      const { _debounceTimer } = get();
      if (_debounceTimer) clearTimeout(_debounceTimer);

      set(state => ({
        _pendingResolvers: [...state._pendingResolvers, resolve],
      }));

      const timer = setTimeout(async () => {
        const { probing } = get();

        if (probing) {
          const resolvers = get()._pendingResolvers.slice();
          set({ _pendingResolvers: [], _debounceTimer: null });
          resolvers.forEach(r => r());
          return;
        }

        set({ probing: true, _debounceTimer: null });

        try {
          const response = await fetch("http://www.google.com/generate_204", {
            cache: "no-cache",
            connectTimeout: 5000,
          });
          set({ connected: response.ok, lastProbe: dayjs() });
        } catch (error) {
          set({ connected: false, lastProbe: dayjs() });
        } finally {
          const resolvers = get()._pendingResolvers.slice();
          set({ probing: false, _pendingResolvers: [] });
          resolvers.forEach(r => r());
        }
      }, DEBOUNCE_MS);

      set({ _debounceTimer: timer });
    });
  },
}));

export default useNetworkStore;
