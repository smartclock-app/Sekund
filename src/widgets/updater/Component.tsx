import { WidgetComponent } from "@/helpers/types";
import { Config } from ".";
import Android from "./Android";

const Component: WidgetComponent<Config> = ({ config }) => {
  return <Android config={config} />;
  // const currentPlatform = platform();
  // if (currentPlatform == "android") return <Android config={config} />;
  // else if (["linux", "macos", "windows"].includes(currentPlatform)) return <Desktop config={config} />;
  // return null;
};

export default Component;
