import Card from "@/components/Card";
import { WeatherIcon } from "./fetchWeather";
import styles from "./sidebar.module.scss";

const WeatherSidebar = ({
  weatherData,
}: {
  weatherData: { icon: WeatherIcon; temp: string; windSpeed: string } | undefined;
}) => {
  return (
    <Card padding={false}>
      <div className={styles.container}>
        <div className={styles.temp}>
          <span className={styles.icon}>{weatherData?.icon}</span>
          <span>{weatherData?.temp}</span>
        </div>
        <div className={styles.wind}>
          <span className={styles.icon}>&#xea14;</span>
          <span>{weatherData?.windSpeed}</span>
        </div>
      </div>
    </Card>
  );
};

export default WeatherSidebar;
