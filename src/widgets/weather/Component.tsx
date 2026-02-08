import { WidgetComponent, WidgetLocation } from "@/helpers/types";
import { Config } from ".";
import WeatherMain from "./Main";
import WeatherSidebar from "./Sidebar";

const Component: WidgetComponent<Config> = ({ config, location }) => {
  if (location === WidgetLocation.Sidebar) return <WeatherSidebar config={config} />;
  return <WeatherMain config={config} />;
};

export default Component;
