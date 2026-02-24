import { afterEach, beforeEach, describe, expect, it } from "vitest";
import useVariablesStore from "./useVariablesStore";

describe("useVariablesStore", () => {
  beforeEach(() => {
    useVariablesStore.setState({ variables: "" });
  });

  afterEach(() => {
    useVariablesStore.setState({ variables: "" });
  });

  it("starts with an empty variables string", () => {
    expect(useVariablesStore.getState().variables).toBe("");
  });

  it("setVariables updates state without writing to disk when writeToDisk is false", async () => {
    await useVariablesStore.getState().setVariables(":root { --color: red; }", false);
    expect(useVariablesStore.getState().variables).toBe(":root { --color: red; }");
  });

  it("setVariables updates state and writes to disk when writeToDisk is true", async () => {
    await useVariablesStore.getState().setVariables(":root { --color: blue; }", true);
    expect(useVariablesStore.getState().variables).toBe(":root { --color: blue; }");
  });

  it("setVariables writes to disk by default (writeToDisk defaults to true)", async () => {
    await useVariablesStore.getState().setVariables("body {}");
    expect(useVariablesStore.getState().variables).toBe("body {}");
  });

  it("setVariables overwrites any previously stored value", async () => {
    await useVariablesStore.getState().setVariables("first", false);
    await useVariablesStore.getState().setVariables("second", false);
    expect(useVariablesStore.getState().variables).toBe("second");
  });
});
