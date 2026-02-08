import { CalendarExtensionComponent } from "@/helpers/types";
import { info } from "@tauri-apps/plugin-log";
import { Config } from ".";

const Component: CalendarExtensionComponent<Config> = config => {
  info(JSON.stringify(config));

  return [];
};

export default Component;
