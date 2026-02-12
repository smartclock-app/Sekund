import { create } from "zustand";
import { Config } from "..";
import QueryClient from "./QueryClient";

interface QueryClientStoreState {
  isInitialized: boolean;
  client: QueryClient | null;
  init: (config: Config) => void;
}

const useQueryClient = create<QueryClientStoreState>()(set => ({
  isInitialized: false,
  client: null,
  init: config => {
    const client = new QueryClient(config.cookies, config.token);
    set({ isInitialized: true, client });
  },
}));

export default useQueryClient;
