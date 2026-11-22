/**
 * Open-Meteo API Client (Weather & Air Quality)
 * No API key required. Attribution: Open-Meteo.com
 * P2: Fetches and parses hourly forecasts to evaluate worst-case environmental conditions (Policy Rule 2).
 */

import { calculateHeatIndex } from './heatindex';
import { calculateAqiFromPm25 } from '@airsafe/policy';

export interface HourlyForecastPoint {
  time: string;
  temperatureC: number;
  relativeHumidityPct: number;
  apparentTemperatureC: number;
  heatIndexC: number;
  pm25: number;
  usAqi: number;
}

export interface OpenMeteoData {
  status?: 'AVAILABLE' | 'UNAVAILABLE';
  temperatureC: number;
  relativeHumidityPct: number;
  apparentTemperatureC: number;
  pm25: number;
  usAqi: number;
  timestamp: string;
  isModeled: boolean;
  hourlyForecast?: HourlyForecastPoint[];
}

export async function fetchOpenMeteoData(
  latitude: number,
  longitude: number,
  options?: { timeoutMs?: number }
): Promise<OpenMeteoData | null> {
  const timeoutMs = options?.timeoutMs ?? 10000;
  const controller = new AbortController();
  const timer = setTimeout(() => controller.abort(), timeoutMs);

  try {
    // 1. Weather API for Temperature & Humidity with hourly forecast (P2)
    const weatherUrl = `https://api.open-meteo.com/v1/forecast?latitude=${latitude}&longitude=${longitude}&current=temperature_2m,relative_humidity_2m,apparent_temperature&hourly=temperature_2m,relative_humidity_2m,apparent_temperature&timezone=auto`;
    // 2. Air Quality API for PM2.5 and US AQI with hourly forecast (P2)
    const airQualityUrl = `https://air-quality-api.open-meteo.com/v1/air-quality?latitude=${latitude}&longitude=${longitude}&current=pm2_5,us_aqi&hourly=pm2_5,us_aqi&timezone=auto`;

    let weatherRes: Response;
    let airRes: Response;

    try {
      [weatherRes, airRes] = await Promise.all([
        fetch(weatherUrl, { signal: controller.signal }),
        fetch(airQualityUrl, { signal: controller.signal }),
      ]);
    } finally {
      clearTimeout(timer);
    }

    if (!weatherRes.ok || !airRes.ok) {
      console.warn(`Open-Meteo fetch error: weather=${weatherRes.status}, air=${airRes.status}`);
      return null;
    }

    const weatherJson = await weatherRes.json();
    const airJson = await airRes.json();

    const currentWeather = weatherJson?.current || {};
    const currentAir = airJson?.current || {};

    const hourlyWeather = weatherJson?.hourly || {};
    const hourlyAir = airJson?.hourly || {};
    const times: string[] = hourlyWeather.time || hourlyAir.time || [];

    const rawTemp = currentWeather.temperature_2m;
    const rawRh = currentWeather.relative_humidity_2m;
    const rawApp = currentWeather.apparent_temperature;
    const rawPm25 = currentAir.pm2_5;

    // Strict validation: Reject fake fallbacks. If API fails to provide valid numbers, return null
    if (
      typeof rawTemp !== 'number' ||
      !Number.isFinite(rawTemp) ||
      typeof rawRh !== 'number' ||
      !Number.isFinite(rawRh) ||
      typeof rawPm25 !== 'number' ||
      !Number.isFinite(rawPm25)
    ) {
      console.warn('Open-Meteo returned invalid or missing weather/air metrics; marking unavailable.');
      return null;
    }

    const temperatureC = rawTemp;
    const relativeHumidityPct = rawRh;
    const apparentTemperatureC = typeof rawApp === 'number' && Number.isFinite(rawApp) ? rawApp : rawTemp;
    const pm25 = rawPm25;
    const usAqi = calculateAqiFromPm25(pm25);

    const hourlyForecast: HourlyForecastPoint[] = [];
    const limit = Math.min(times.length, 48);
    for (let i = 0; i < limit; i++) {
      const timeStr = times[i];
      const isoTime = timeStr.includes('Z') ? timeStr : new Date(timeStr).toISOString();

      const rawTc = hourlyWeather.temperature_2m?.[i];
      const rawRhHour = hourlyWeather.relative_humidity_2m?.[i];
      const rawAppC = hourlyWeather.apparent_temperature?.[i];
      const rawP25 = hourlyAir.pm2_5?.[i];

      // Strictly include forecast points only if valid metrics exist — zero fake fallbacks (no fake 28°C, 65%, 35)
      if (
        typeof rawTc === 'number' &&
        Number.isFinite(rawTc) &&
        typeof rawRhHour === 'number' &&
        Number.isFinite(rawRhHour) &&
        typeof rawP25 === 'number' &&
        Number.isFinite(rawP25)
      ) {
        const tC = rawTc;
        const rh = rawRhHour;
        const appC = typeof rawAppC === 'number' && Number.isFinite(rawAppC) ? rawAppC : tC;
        const p25Hour = rawP25;
        const aqiVal = calculateAqiFromPm25(p25Hour);
        const hi = calculateHeatIndex(tC, rh);

        hourlyForecast.push({
          time: isoTime,
          temperatureC: tC,
          relativeHumidityPct: rh,
          apparentTemperatureC: appC,
          heatIndexC: hi,
          pm25: p25Hour,
          usAqi: aqiVal,
        });
      }
    }

    return {
      status: 'AVAILABLE',
      temperatureC,
      relativeHumidityPct,
      apparentTemperatureC,
      pm25,
      usAqi,
      timestamp: currentWeather.time ? new Date(currentWeather.time).toISOString() : new Date().toISOString(),
      isModeled: true,
      hourlyForecast,
    };
  } catch (error: any) {
    if (error?.name === 'AbortError') {
      console.warn('Open-Meteo fetch timed out');
    } else {
      console.error('Error fetching Open-Meteo data:', error);
    }
    return null;
  }
}
