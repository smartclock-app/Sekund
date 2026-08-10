import useAlertsStore from "@/hooks/useAlertsStore";
import useConfigStore from "@/hooks/useConfigStore";
import { fetch } from "@tauri-apps/plugin-http";
import { info, warn } from "@tauri-apps/plugin-log";

const handleDeepLink = async (urls: string[]) => {
  info(`Received deep link: ${urls}`);
  const pattern = /^sekund:\/\/([^?]+)\?(.+)$/;

  for (const url of urls) {
    const match = url.match(pattern);
    if (!match) {
      warn(`Received deep link with unrecognized format: ${url}`);
      continue;
    }

    const action = match[1];
    const query = new URLSearchParams(match[2]);
    if (action === "set_config") {
      info("Handling deep link for setting config");
      const configUrl = query.get("u");
      if (configUrl) {
        info(`Fetching config from deep link URL: ${configUrl}`);
        const response = await fetch(configUrl);
        if (response.status === 200) {
          info("Config fetched successfully from deep link URL");
          const config = await response.json();
          const configStore = useConfigStore.getState();

          const result = configStore.configSchema?.safeParse(config);
          if (!result?.success) {
            useAlertsStore
              .getState()
              .pushAlert("Config", { title: "Config", subtitle: "Configuration from deep link is invalid" });
            return;
          }

          configStore.editConfig(config);
          useAlertsStore.getState().pushAlert("Config", {
            title: "Config",
            subtitle: "Configuration updated from deep link. Reloading in 5 seconds...",
          });
          await new Promise(resolve => setTimeout(resolve, 5000));
          window.location.reload();
        } else {
          useAlertsStore.getState().pushAlert("Config", {
            title: "Config",
            subtitle: `Failed to fetch configuration from deep link URL: ${response.statusText}`,
          });
        }
      }
    } else {
      warn(`Received deep link with unrecognized action: ${action}`);
    }
  }
};

export default handleDeepLink;
