import { BaseDirectory, writeTextFile } from "@tauri-apps/plugin-fs";
import { create } from "zustand";

interface VariablesStoreState {
  variables: string;
  setVariables: (variables: string, writeToDisk?: boolean) => Promise<void>;
}

const useVariablesStore = create<VariablesStoreState>()(set => ({
  variables: "",
  setVariables: async (variables, writeToDisk = true) => {
    if (writeToDisk) {
      await writeTextFile("variables.css", variables, { baseDir: BaseDirectory.AppData });
    }
    set({ variables });
  },
}));

export default useVariablesStore;
