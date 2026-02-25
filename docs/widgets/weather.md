# Weather Widget

Display current weather conditions using the [OpenWeatherMap](https://openweathermap.org/) API.

## Details

| Property | Value |
|----------|-------|
| **Type** | `widget` |
| **Allowed Locations** | `main`, `sidebar` |
| **Widget Name** | `weather` |

## Configuration

Add `"weather"` to `layout.main` or `layout.sidebar` and configure it under `widgets.weather`:

```json
{
  "layout": {
    "main": ["weather"]
  },
  "widgets": {
    "weather": {
      "apiKey": "your-openweathermap-api-key",
      "postcode": "SW1A 1AA",
      "country": "GB",
      "units": "metric"
    }
  }
}
```

## Options Reference

| Field | Type | Default | Description |
|-------|------|---------|-------------|
| `apiKey` | string | `""` | OpenWeatherMap API key |
| `postcode` | string | `""` | Postal / ZIP code for weather location. UK postcodes are validated with a full UK postcode regex when `country` is `"GB"`. |
| `country` | string (2-letter ISO) | `"GB"` | ISO 3166-1 alpha-2 country code |
| `units` | `"metric"` \| `"imperial"` | `"metric"` | Unit system for temperature and wind speed |

## Setup Notes

A free OpenWeatherMap API key is sufficient for current-weather data. Sign up at [https://openweathermap.org/api](https://openweathermap.org/api) and use the **Current Weather Data** API.
