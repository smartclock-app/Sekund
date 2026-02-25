import { afterEach, beforeEach, describe, expect, it } from "vitest";
import useRouter, { RouterScreen } from "./useRouter";

describe("useRouter", () => {
  beforeEach(() => {
    // Reset store to initial state before each test
    useRouter.setState({
      currentScreen: RouterScreen.Main,
      previousScreen: undefined,
    });
  });

  afterEach(() => {
    useRouter.setState({
      currentScreen: RouterScreen.Main,
      previousScreen: undefined,
    });
  });

  it("starts on the Main screen", () => {
    expect(useRouter.getState().currentScreen).toBe(RouterScreen.Main);
  });

  it("navigate() changes currentScreen and saves previousScreen", () => {
    useRouter.getState().navigate(RouterScreen.Editor);
    const state = useRouter.getState();
    expect(state.currentScreen).toBe(RouterScreen.Editor);
    expect(state.previousScreen).toBe(RouterScreen.Main);
  });

  it("goBack() restores the previous screen", () => {
    useRouter.getState().navigate(RouterScreen.Editor);
    useRouter.getState().goBack();
    const state = useRouter.getState();
    expect(state.currentScreen).toBe(RouterScreen.Main);
    expect(state.previousScreen).toBeUndefined();
  });

  it("goBack() falls back to Main when there is no previous screen", () => {
    useRouter.getState().goBack();
    expect(useRouter.getState().currentScreen).toBe(RouterScreen.Main);
  });

  it("navigate() chains correctly through multiple screens", () => {
    useRouter.getState().navigate(RouterScreen.Editor);
    useRouter.getState().navigate(RouterScreen.Logs);
    const state = useRouter.getState();
    expect(state.currentScreen).toBe(RouterScreen.Logs);
    expect(state.previousScreen).toBe(RouterScreen.Editor);
  });
});
