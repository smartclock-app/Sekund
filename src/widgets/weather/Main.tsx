import { useEffect, useState } from "react";
import { Config } from ".";
import fetchWeather, { WeatherIcon } from "./fetchWeather";
import styles from "./main.module.scss";

const WeatherMain = ({ config }: { config: Config }) => {
  const [weatherData, setWeatherData] = useState<{ icon: WeatherIcon; temp: string; windSpeed: string }>();

  useEffect(() => {
    fetchWeather(config).then(data => {
      if (data.icon && data.temp && data.windSpeed) {
        setWeatherData(data);
      }
    });
  }, [config]);

  return (
    <div className={styles.container}>
      <div className={styles.temp}>
        <span className={styles.icon}>{weatherData?.icon}</span>
        <span>{weatherData?.temp}</span>
      </div>
      <div className={styles.wind}>
        <span>{weatherData?.windSpeed}</span>
        <span className={styles.icon}>&#xea14;</span>
      </div>
    </div>
  );
};

export default WeatherMain;
