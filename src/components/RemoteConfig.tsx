import { useHttpRequestListener } from "@/hooks/useHttpStore";
import useRemoteConfigStore from "@/hooks/useRemoteConfig";
import { info } from "@tauri-apps/plugin-log";

const RemoteConfig = () => {
  useHttpRequestListener(async event => {
    if (event.path === "/api/command") {
      const { command, data } = JSON.parse(event.body);
      info(`[Remote Config] Received command: ${command} with data: ${JSON.stringify(data)}`);

      const handler = useRemoteConfigStore.getState().getHandler(command);
      if (handler) return await handler(data);

      return { status: "error", error: "Unknown command" };
    }

    info(JSON.stringify(event));
    return { status: "error", error: "404" };
  });

  return null;
};

export default RemoteConfig;
