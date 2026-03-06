import { WidgetComponent, WidgetLocation } from "@/helpers/types";
import useEventListener, { EventType } from "@/hooks/useEventListener";
import { useEffect, useState } from "react";
import { Config } from ".";
import fetchWeather, { WeatherIcon } from "./fetchWeather";
import WeatherMain from "./Main";
import WeatherSidebar from "./Sidebar";

const Component: WidgetComponent<Config> = ({ config, location }) => {
  const [weatherData, setWeatherData] = useState<{ icon: WeatherIcon; temp: string; windSpeed: string }>();

  useEffect(() => {
    fetchWeather(config).then(data => {
      if (data.icon && data.temp && data.windSpeed) {
        setWeatherData(data);
      }
    });
  }, [config]);

  useEventListener(EventType.Refresh, () => {
    fetchWeather(config).then(data => {
      if (data.icon && data.temp && data.windSpeed) {
        setWeatherData(data);
      }
    });
  });

  if (location === WidgetLocation.Sidebar) return <WeatherSidebar weatherData={weatherData} />;
  return <WeatherMain weatherData={weatherData} />;
};

export default Component;
