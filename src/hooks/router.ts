import { create } from "zustand";

export enum RouterScreen {
  Main = "main",
  Editor = "editor",
  Logs = "logs",
}

interface RouterStoreState {
  currentScreen: RouterScreen;
  navigate: (screen: RouterScreen) => void;
}

const useRouter = create<RouterStoreState>()(set => ({
  currentScreen: RouterScreen.Main,
  navigate: (screen: RouterScreen) => set({ currentScreen: screen }),
}));

export default useRouter;
