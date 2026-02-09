import { WeatherIcon } from "./fetchWeather";
import styles from "./main.module.scss";

const WeatherMain = ({
  weatherData,
}: {
  weatherData: { icon: WeatherIcon; temp: string; windSpeed: string } | undefined;
}) => {
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
