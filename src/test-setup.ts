import "@testing-library/jest-dom";
import { clearMocks, mockIPC, mockWindows } from "@tauri-apps/api/mocks";
import { afterEach, beforeEach } from "vitest";

// Set up Tauri mocks per https://v2.tauri.app/develop/tests/
// mockIPC intercepts all IPC calls from @tauri-apps/api/core's invoke()
// mockWindows sets up the window metadata needed by Tauri APIs
beforeEach(() => {
  mockWindows("main");
  mockIPC((cmd) => {
    // plugin:log — all log levels
    if (cmd === "plugin:log|log") return null;

    // plugin:fs — file system operations
    if (cmd === "plugin:fs|exists") return false;
    if (cmd === "plugin:fs|read_text_file") return "";
    if (cmd === "plugin:fs|write_text_file") return null;
    if (cmd === "plugin:fs|mkdir") return null;
    if (cmd === "plugin:fs|read_dir") return [];
    if (cmd === "plugin:fs|remove") return null;

    // plugin:app — app metadata
    if (cmd === "plugin:app|version") return "0.0.0";

    // plugin:event — event system (listen returns a numeric handler id)
    if (cmd === "plugin:event|listen") return 1;
    if (cmd === "plugin:event|unlisten") return null;
    if (cmd === "plugin:event|emit") return null;

    // Application IPC commands
    if (cmd === "start_http_server") return null;
    if (cmd === "stop_http_server") return null;
    if (cmd === "http_respond") return null;

    return null;
  });
});

// Clean up Tauri mock state after each test so tests are isolated
afterEach(() => {
  clearMocks();
});
