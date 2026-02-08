import { attachConsole, debug, info, warn } from "@tauri-apps/plugin-log";
import React from "react";
import ReactDOM from "react-dom/client";
import App from "./App";

// Override default console methods to also log to Tauri's console
await attachConsole();
const originalLog = console.log;
const originalWarn = console.warn;
const originalError = console.error;

console.debug = (...args) => {
  originalLog(...args);
  debug(args.map(s => (typeof s === "string" ? s : JSON.stringify(s))).join(" "), { file: "frontend", line: 0 });
};

console.log = (...args) => {
  originalLog(...args);
  info(args.map(s => (typeof s === "string" ? s : JSON.stringify(s))).join(" "), { file: "frontend", line: 0 });
};

console.warn = (...args) => {
  originalWarn(...args);
  warn(args.map(s => (typeof s === "string" ? s : JSON.stringify(s))).join(" "), { file: "frontend", line: 0 });
};

console.error = (...args) => {
  originalError(...args);
  warn(args.map(s => (typeof s === "string" ? s : JSON.stringify(s))).join(" "), { file: "frontend", line: 0 });
};

ReactDOM.createRoot(document.getElementById("root") as HTMLElement).render(
  <React.StrictMode>
    <App />
  </React.StrictMode>,
);
