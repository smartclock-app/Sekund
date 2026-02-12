import Editor, { OnChange, useMonaco } from "@monaco-editor/react";
import { BaseDirectory } from "@tauri-apps/plugin-fs";
import { useEffect, useState } from "react";
import { BASE_DIRECTORY } from "../../helpers/types";
import useConfigStore from "../../hooks/useConfigStore";
import useRouter from "../../hooks/useRouter";
import styles from "./editor.module.scss";
import { loadFile, saveFile } from "./editorUtils";
import { useEditorStore, type EditorFile } from "./useEditorStore";

const EDITOR_FILES: EditorFile[] = [
  {
    name: "config.json",
    language: "json",
    baseDir: BASE_DIRECTORY,
    content: "",
    readonly: false,
  },
  {
    name: "variables.css",
    language: "css",
    baseDir: BASE_DIRECTORY,
    content: "",
    readonly: false,
  },
  {
    name: "Smart Clock.log",
    language: "log",
    baseDir: BaseDirectory.AppLog,
    content: "",
    readonly: true,
  },
];

export default function EditorScreen() {
  const monaco = useMonaco();
  const { goBack } = useRouter();
  const {
    files,
    activeFile,
    setActiveFile,
    updateContent,
    loadFile: storeLoadFile,
    markSaved,
    hasUnsaved,
    unsavedChanges,
  } = useEditorStore();
  const [errorMessage, setErrorMessage] = useState<string>("");
  const [isDrawerOpen, setIsDrawerOpen] = useState(false);
  const [pendingNavigation, setPendingNavigation] = useState<(() => void) | null>(null);
  const configStore = useConfigStore();

  // Load all files on mount
  useEffect(() => {
    const initializeFiles = async () => {
      for (const file of EDITOR_FILES) {
        const result = await loadFile(file.name, file.baseDir);
        if (result.error) {
          setErrorMessage(`Failed to load ${file.name}: ${result.error}`);
          storeLoadFile(file, "");
        } else {
          storeLoadFile(file, result.content);
        }
      }
    };

    // Suppress NotAllowedError from Monaco
    const handleError = (event: ErrorEvent) => {
      if (event.error?.message?.includes("NotAllowedError")) {
        event.preventDefault();
      }
    };

    window.addEventListener("error", handleError);
    initializeFiles();

    return () => {
      window.removeEventListener("error", handleError);
    };
  }, [storeLoadFile]);

  const handleEditorChange: OnChange = value => {
    if (value !== undefined) {
      updateContent(activeFile, value);
    }
  };

  const handleSaveCurrentFile = async () => {
    const currentFile = files[activeFile];
    if (!currentFile) return;

    setErrorMessage("");
    const result = await saveFile(activeFile, currentFile.content);

    if (result.success) {
      markSaved(activeFile);
    } else {
      setErrorMessage(result.error || "Unknown error saving file");
    }
  };

  const handleTabClick = (tabName: string) => {
    if (activeFile === tabName) return;
    setActiveFile(tabName);
  };

  const handleBackClick = () => {
    if (hasUnsaved()) {
      setPendingNavigation(() => goBack);
      setIsDrawerOpen(true);
    } else {
      goBack();
    }
  };

  const handleDiscardChanges = () => {
    // Reset store and fire pending navigation
    useEditorStore.setState({ unsavedChanges: new Set() });
    setIsDrawerOpen(false);
    if (pendingNavigation) {
      pendingNavigation();
      setPendingNavigation(null);
    }
  };

  const handleKeepEditing = () => {
    setIsDrawerOpen(false);
    setPendingNavigation(null);
  };

  const currentFile = files[activeFile];
  const isReadOnly = currentFile?.readonly || false;

  return (
    <div className={styles.editorScreen}>
      {/* Header with back button */}
      <div className={styles.header}>
        <button className={styles.backButton} onClick={handleBackClick} title="Go back">
          ← Back
        </button>
        <div className={styles.tabs}>
          {EDITOR_FILES.map(file => (
            <button
              key={file.name}
              className={`${styles.tab} ${activeFile === file.name ? styles.active : ""} ${
                unsavedChanges.has(file.name) ? styles.unsaved : ""
              }`}
              onClick={() => handleTabClick(file.name)}
            >
              {file.name}
              {unsavedChanges.has(file.name) && <span className={styles.dot}>•</span>}
            </button>
          ))}
        </div>
      </div>

      {/* Error message */}
      {errorMessage && <div className={styles.errorMessage}>{errorMessage}</div>}

      {/* Editor */}
      <div className={styles.editorContainer}>
        {currentFile && (
          <Editor
            key={activeFile}
            height="100%"
            language={currentFile.language}
            value={currentFile.content}
            onChange={handleEditorChange}
            onMount={(_, m: typeof monaco) => {
              // Set up schema for config.json on mount
              try {
                if (!m) throw new Error("Monaco instance not available");

                if (configStore.configSchema && configStore.version) {
                  const schemaData = configStore.configSchema.toJSONSchema();
                  const schemaUri = `inmemory://model/schema-${configStore.version}.json`;

                  console.log("Setting Monaco schema for config.json:", schemaUri, schemaData);

                  m.json.jsonDefaults.setDiagnosticsOptions({
                    validate: true,
                    allowComments: false,
                    schemas: [
                      {
                        uri: schemaUri,
                        fileMatch: ["config.json"],
                        schema: schemaData as any,
                      },
                    ],
                  });
                } else {
                  throw new Error("Config schema or version not available");
                }
              } catch (error) {
                // Silently ignore schema setup errors
                console.log("Error setting up Monaco schema:", error);
              }
            }}
            options={{
              readOnly: isReadOnly,
              minimap: { enabled: false },
              fontSize: 14,
              fontFamily: '"Monaco", "Menlo", "Consolas", monospace',
              wordWrap: "on",
              scrollBeyondLastLine: false,
            }}
          />
        )}
      </div>

      {/* Save button (only for non-readonly files) */}
      {!isReadOnly && (
        <div className={styles.footer}>
          <button
            className={styles.saveButton}
            onClick={handleSaveCurrentFile}
            disabled={!unsavedChanges.has(activeFile)}
          >
            Save
          </button>
        </div>
      )}

      {/* Confirmation dialog for discarding changes */}
      {isDrawerOpen && (
        <div className={styles.drawer}>
          <div className={styles.drawerContent}>
            <h3>Unsaved Changes</h3>
            <p>You have unsaved changes. Do you want to discard them?</p>
            <div className={styles.drawerButtons}>
              <button className={styles.keepButton} onClick={handleKeepEditing}>
                Keep Editing
              </button>
              <button className={styles.discardButton} onClick={handleDiscardChanges}>
                Discard Changes
              </button>
            </div>
          </div>
        </div>
      )}
    </div>
  );
}
