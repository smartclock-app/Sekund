import { WidgetComponent, WidgetLocation } from "@/helpers/types";
import useEventListener, { EventType } from "@/hooks/useEventListener";
import { warn } from "@tauri-apps/plugin-log";
import { useEffect, useState } from "react";
import { Config } from ".";
import fetchWeather, { WeatherIcon } from "./fetchWeather";
import WeatherMain from "./Main";
import WeatherSidebar from "./Sidebar";

type WeatherData = { icon: WeatherIcon; temp: string; windSpeed: string };

const refreshWeather = (config: Config, setWeatherData: (data: WeatherData) => void) => {
  fetchWeather(config)
    .then(data => {
      if (data.icon && data.temp && data.windSpeed) {
        setWeatherData(data);
      }
    })
    .catch(e => warn(`[Weather] Failed to fetch weather, keeping last known data: ${e}`));
};

const Component: WidgetComponent<Config> = ({ config, location }) => {
  const [weatherData, setWeatherData] = useState<WeatherData>();

  useEffect(() => {
    refreshWeather(config, setWeatherData);
  }, [config]);

  useEventListener(EventType.Refresh, () => {
    refreshWeather(config, setWeatherData);
  });

  if (location === WidgetLocation.Sidebar) return <WeatherSidebar weatherData={weatherData} />;
  return <WeatherMain weatherData={weatherData} />;
};

export default Component;
