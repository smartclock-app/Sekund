import { VARIABLES_FILENAME } from "@/helpers/config/cssVariables";
import { BASE_DIRECTORY } from "@/helpers/types";
import { writeTextFile } from "@tauri-apps/plugin-fs";
import { create } from "zustand";

interface VariablesStoreState {
  variables: string;
  setVariables: (variables: string, writeToDisk?: boolean) => Promise<void>;
}

const useVariablesStore = create<VariablesStoreState>()(set => ({
  variables: "",
  setVariables: async (variables, writeToDisk = true) => {
    if (writeToDisk) {
      await writeTextFile(`${VARIABLES_FILENAME}.css`, variables, { baseDir: BASE_DIRECTORY });
    }
    set({ variables });
  },
}));

export default useVariablesStore;
