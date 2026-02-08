import { fetch } from "@tauri-apps/plugin-http";
import { info } from "@tauri-apps/plugin-log";
import { Config } from ".";

const icons = {
  "01d": "\uea02",
  "01n": "\uea01",
  "02d": "\uea03",
  "02n": "\uea04",
  "03d": "\uea05",
  "03n": "\uea06",
  "04d": "\uea07",
  "04n": "\uea08",
  "09d": "\uea09",
  "09n": "\uea0a",
  "10d": "\uea0b",
  "10n": "\uea0c",
  "11d": "\uea0d",
  "11n": "\uea0e",
  "1232n": "\uea0f",
  "13d": "\uea10",
  "13n": "\uea11",
  "50d": "\uea12",
  "50n": "\uea13",
  wind: "\uea14",
} as const;

export type WeatherIcon = (typeof icons)[keyof typeof icons];

const fetchWeather = async (config: Config) => {
  info("[Weather] Refetching weather data");
  if (!config.apiKey || !config.postcode || !config.country || !config.units) {
    throw new Error("[Weather] API Key, Postcode, Country, and Units must be set in the config file.");
  }

  const request = await fetch(
    `https://api.openweathermap.org/data/2.5/weather?zip=${config.postcode},${config.country}&appid=${config.apiKey}&units=${config.units}`,
  );
  const response = await request.json();
  const icon = response["weather"][0]["icon"];
  const temp = response["main"]["temp"];
  const windSpeed = response["wind"]["speed"];
  return {
    icon: icons[icon as keyof typeof icons] || icons["01d"],
    temp: `${Math.round(temp)}ºC`,
    windSpeed: `${Math.round(windSpeed)} mph`,
  };
};

export default fetchWeather;
