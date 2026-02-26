import useAlertsStore from "@/hooks/useAlertsStore";
import useConfigStore from "@/hooks/useConfigStore";
import { fetch } from "@tauri-apps/plugin-http";
import { info } from "@tauri-apps/plugin-log";

const handleDeepLink = async (urls: string[]) => {
  info(`Received deep link: ${urls}`);
  for (const url of urls) {
    const parsedUrl = new URL(url);
    if (parsedUrl.hostname === "set_config") {
      const configUrl = parsedUrl.searchParams.get("u");
      if (configUrl) {
        const response = await fetch(configUrl);
        if (response.status === 200) {
          const config = await response.json();
          const configStore = useConfigStore.getState();

          const result = configStore.configSchema?.safeParse(config);
          if (!result?.success) {
            useAlertsStore.getState().pushAlert("Config", "Configuration from deep link is invalid");
            return;
          }

          configStore.setConfig(config);
          useAlertsStore
            .getState()
            .pushAlert("Config", "Configuration updated from deep link\nReloading in 5 seconds...");
          await new Promise(resolve => setTimeout(resolve, 5000));
          window.location.reload();
        }
      }
    }
  }
};

export default handleDeepLink;
