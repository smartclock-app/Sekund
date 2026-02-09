import { create } from "zustand";
import QueryClient from "./QueryClient";

interface QueryClientStoreState {
  isInitialized: boolean;
  client: QueryClient | null;
  init: (token: string) => Promise<void>;
}

const useQueryClient = create<QueryClientStoreState>()(set => ({
  isInitialized: false,
  client: null,
  init: async token => {
    const client = await QueryClient.createClient("cookies.json", token);
    set({ isInitialized: true, client });
  },
}));

export default useQueryClient;
