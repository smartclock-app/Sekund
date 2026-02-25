import { BaseDirectory, readTextFile } from "@tauri-apps/plugin-fs";
import { BASE_DIRECTORY } from "../../helpers/types";
import useConfigStore from "../../hooks/useConfigStore";
import useVariablesStore from "../../hooks/useVariablesStore";

export interface FileLoadResult {
  content: string;
  error?: string;
}

export interface SaveResult {
  success: boolean;
  error?: string;
}

/**
 * Load a file from disk
 */
export async function loadFile(
  filename: string,
  baseDir: BaseDirectory | typeof BASE_DIRECTORY,
): Promise<FileLoadResult> {
  try {
    const content = await readTextFile(filename, { baseDir });
    return { content };
  } catch (error) {
    const errorMessage = error instanceof Error ? error.message : "Unknown error loading file";
    return { content: "", error: errorMessage };
  }
}

/**
 * Validate JSON schema for config.json
 */
export function validateJsonSchema(content: string): { valid: boolean; error?: string } {
  try {
    const parsed = JSON.parse(content);
    const schema = useConfigStore.getState().configSchema;

    if (!schema) {
      return { valid: false, error: "Schema not initialized" };
    }

    const result = schema.safeParse(parsed);
    if (!result.success) {
      const errors = result.error.issues.map((err: any) => {
        const path = err.path.join(".");
        return `${path || "root"}: ${err.message}`;
      });
      return { valid: false, error: errors.join("; ") };
    }

    return { valid: true };
  } catch (error) {
    const errorMessage = error instanceof Error ? error.message : "Invalid JSON";
    return { valid: false, error: errorMessage };
  }
}

/**
 * Save config.json file
 */
export async function saveConfigJson(content: string): Promise<SaveResult> {
  try {
    // First validate the schema
    const validation = validateJsonSchema(content);
    if (!validation.valid) {
      return { success: false, error: validation.error };
    }

    // Parse and save
    const parsed = JSON.parse(content);
    await useConfigStore.getState().editConfig(parsed);
    return { success: true };
  } catch (error) {
    const errorMessage = error instanceof Error ? error.message : "Error saving config";
    return { success: false, error: errorMessage };
  }
}

/**
 * Save variables.css file
 */
export async function saveVariablesCss(content: string): Promise<SaveResult> {
  try {
    await useVariablesStore.getState().setVariables(content, true);
    return { success: true };
  } catch (error) {
    const errorMessage = error instanceof Error ? error.message : "Error saving variables";
    return { success: false, error: errorMessage };
  }
}

/**
 * Save the appropriate file based on filename
 */
export async function saveFile(filename: string, content: string): Promise<SaveResult> {
  if (filename === "config.json") {
    return saveConfigJson(content);
  } else if (filename === "variables.css") {
    return saveVariablesCss(content);
  } else if (filename === "Sekund.log") {
    return { success: false, error: "Cannot save read-only log file" };
  }
  return { success: false, error: `Unknown file: ${filename}` };
}
