import handleCommand from "@/helpers/handleCommand";
import { useHttpRequestListener } from "@/hooks/useRemoteStore";
import { info } from "@tauri-apps/plugin-log";

const RemoteConfig = () => {
  useHttpRequestListener(event => {
    if (event.path === "/api/command") {
      const body = JSON.parse(event.body);
      return handleCommand(body.command, body.data);
    }

    info(JSON.stringify(event));
    return { status: "ok", result: { event } };
  });

  return null;
};

export default RemoteConfig;
