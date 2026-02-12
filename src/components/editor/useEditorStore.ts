import { BaseDirectory } from "@tauri-apps/plugin-fs";
import { create } from "zustand";

export interface EditorFile {
  name: string;
  language: "json" | "css" | "log";
  baseDir: BaseDirectory;
  content: string;
  readonly: boolean;
}

interface EditorStore {
  files: Record<string, EditorFile>;
  activeFile: string;
  unsavedChanges: Set<string>;
  loadFile: (file: EditorFile, content: string) => void;
  updateContent: (filename: string, content: string) => void;
  setActiveFile: (filename: string) => void;
  markSaved: (filename: string) => void;
  hasUnsaved: () => boolean;
  getUnsavedFiles: () => string[];
  reset: () => void;
}

export const useEditorStore = create<EditorStore>((set, get) => ({
  files: {},
  activeFile: "config.json",
  unsavedChanges: new Set(),

  loadFile: (file, content) => {
    set(state => ({
      files: {
        ...state.files,
        [file.name]: {
          ...file,
          content,
        },
      },
    }));
  },

  updateContent: (filename, content) => {
    set(state => {
      const newUnsaved = new Set(state.unsavedChanges);
      const file = state.files[filename];

      // Only mark as unsaved if it's not readonly and content actually changed
      if (!file?.readonly) {
        // Check if content differs from last saved version
        // For now, always mark as unsaved when updated
        newUnsaved.add(filename);
      }

      return {
        files: {
          ...state.files,
          [filename]: {
            ...state.files[filename],
            content,
          },
        },
        unsavedChanges: newUnsaved,
      };
    });
  },

  setActiveFile: filename => {
    set({ activeFile: filename });
  },

  markSaved: filename => {
    set(state => {
      const newUnsaved = new Set(state.unsavedChanges);
      newUnsaved.delete(filename);
      return { unsavedChanges: newUnsaved };
    });
  },

  hasUnsaved: () => {
    return get().unsavedChanges.size > 0;
  },

  getUnsavedFiles: () => {
    return Array.from(get().unsavedChanges);
  },

  reset: () => {
    set({
      files: {},
      activeFile: "config.json",
      unsavedChanges: new Set(),
    });
  },
}));
