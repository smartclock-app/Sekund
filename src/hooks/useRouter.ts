import { create } from "zustand";

export enum RouterScreen {
  Main = "main",
  Editor = "editor",
  Logs = "logs",
}

interface RouterStoreState {
  currentScreen: RouterScreen;
  previousScreen?: RouterScreen;
  navigate: (screen: RouterScreen) => void;
  goBack: () => void;
}

const useRouter = create<RouterStoreState>()(set => ({
  currentScreen: RouterScreen.Main,
  previousScreen: undefined,
  navigate: (screen: RouterScreen) =>
    set(state => ({
      currentScreen: screen,
      previousScreen: state.currentScreen,
    })),
  goBack: () =>
    set(state => ({
      currentScreen: state.previousScreen ?? RouterScreen.Main,
      previousScreen: undefined,
    })),
}));

export default useRouter;
