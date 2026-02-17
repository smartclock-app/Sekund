import useConfigStore from "@/hooks/useConfigStore";
import { dispatchEvent, EventType } from "@/hooks/useEventListener";
import useVariablesStore from "@/hooks/useVariablesStore";
import { BaseDirectory, readTextFile } from "@tauri-apps/plugin-fs";
import { info } from "@tauri-apps/plugin-log";

const handleCommand = async (
  command: string,
  data: any,
): Promise<{ status: "ok"; result: Record<string, any> | string }> => {
  info(`[Remote Config] Received command: ${command} with data: ${JSON.stringify(data)}`);

  if (command === "get_config") {
    const config = useConfigStore.getState().config;
    return { status: "ok", result: config };
  } else if (command === "set_config") {
    useConfigStore.getState().editConfig(data);
    setTimeout(() => window.location.reload(), 100);
    return { status: "ok", result: "Config updated" };
  } else if (command === "get_variables") {
    const variables = useVariablesStore.getState().variables;
    return { status: "ok", result: variables };
  } else if (command === "set_variables") {
    useVariablesStore.getState().setVariables(data);
    setTimeout(() => window.location.reload(), 100);
    return { status: "ok", result: "Variables updated" };
  } else if (command === "get_logs") {
    const logs = await readTextFile("Smart Clock.log", { baseDir: BaseDirectory.AppLog });
    const last100Lines = logs
      .split("\n")
      .slice(-100)
      .join("\n")
      .replaceAll(/\[\d{4}-\d{2}-\d{2}\]|\[(?!INFO|WARN|ERROR|DEBUG|TRACE)(?!\d{2}:\d{2}:\d{2})[^\]]+\](?=\[)/gm, "");
    return { status: "ok", result: last100Lines };
  } else if (command === "skip_photo") {
    dispatchEvent(EventType.SkipPhoto);
    return { status: "ok", result: "Photo Skipped" };
  } else if (command === "refresh") {
    setTimeout(() => window.location.reload(), 100);
    return { status: "ok", result: "Refresh event dispatched" };
  }

  return { status: "ok", result: { command, data } };
};

export default handleCommand;
