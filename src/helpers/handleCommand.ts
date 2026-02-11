import useConfigStore from "@/hooks/useConfigStore";

const handleCommand = (command: string, data: any): { status: "ok"; result: Record<string, any> | string } => {
  console.log("Received command:", command, data);

  if (command === "get_config") {
    const config = useConfigStore.getState().config;
    return { status: "ok", result: config };
  } else if (command === "set_config") {
    useConfigStore.getState().editConfig(data);
    setTimeout(() => window.location.reload(), 100);
    return { status: "ok", result: "Config updated" };
  } else if (command === "refresh") {
    setTimeout(() => window.location.reload(), 100);
    return { status: "ok", result: "Refresh event dispatched" };
  }

  return { status: "ok", result: { command, data } };
};

export default handleCommand;
