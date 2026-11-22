import { fetchOpenMeteoData } from '../openmeteo';

describe('Step 6.2: Open-Meteo Weather Integration Error Handling', () => {
  const originalFetch = global.fetch;

  afterEach(() => {
    global.fetch = originalFetch;
    jest.restoreAllMocks();
  });

  test('returns null when weather API returns non-200 HTTP status (e.g. 500 Internal Error)', async () => {
    global.fetch = jest.fn().mockImplementation(async (url: string) => {
      if (url.includes('forecast')) {
        return { ok: false, status: 500, statusText: 'Internal Server Error' } as any;
      }
      return {
        ok: true,
        status: 200,
        json: async () => ({ current: { pm2_5: 25.0 } }),
      } as any;
    });

    const result = await fetchOpenMeteoData(23.8103, 90.4125);
    expect(result).toBeNull();
  });

  test('returns null when air quality API returns non-200 HTTP status (e.g. 429 Too Many Requests)', async () => {
    global.fetch = jest.fn().mockImplementation(async (url: string) => {
      if (url.includes('air-quality')) {
        return { ok: false, status: 429, statusText: 'Too Many Requests' } as any;
      }
      return {
        ok: true,
        status: 200,
        json: async () => ({ current: { temperature_2m: 30.0, relative_humidity_2m: 60 } }),
      } as any;
    });

    const result = await fetchOpenMeteoData(23.8103, 90.4125);
    expect(result).toBeNull();
  });

  test('returns null when API fetch throws network connection error', async () => {
    global.fetch = jest.fn().mockRejectedValue(new Error('ECONNREFUSED connect to api.open-meteo.com'));

    const result = await fetchOpenMeteoData(23.8103, 90.4125);
    expect(result).toBeNull();
  });

  test('returns null when API fetch times out via AbortSignal', async () => {
    const abortError = new Error('The operation was aborted');
    abortError.name = 'AbortError';
    global.fetch = jest.fn().mockRejectedValue(abortError);

    const result = await fetchOpenMeteoData(23.8103, 90.4125, { timeoutMs: 100 });
    expect(result).toBeNull();
  });

  test('strictly rejects fake fallbacks: returns null instead of fabricating 28°C, 65%, or 35 AQI when metrics are null/missing', async () => {
    global.fetch = jest.fn().mockImplementation(async () => {
      return {
        ok: true,
        status: 200,
        json: async () => ({
          current: {}, // Empty metrics! No temperature_2m, relative_humidity_2m, or pm2_5
        }),
      } as any;
    });

    const result = await fetchOpenMeteoData(23.8103, 90.4125);
    // Must strictly be null (data-unavailable), NOT fake 28°C or 65% humidity or 35 PM2.5
    expect(result).toBeNull();
  });

  test('successfully parses valid Open-Meteo metrics and preserves authentic values', async () => {
    global.fetch = jest.fn().mockImplementation(async (url: string) => {
      if (url.includes('forecast')) {
        return {
          ok: true,
          status: 200,
          json: async () => ({
            current: {
              temperature_2m: 33.2,
              relative_humidity_2m: 72,
              apparent_temperature: 39.5,
              time: '2026-11-17T12:00',
            },
            hourly: {
              time: ['2026-11-17T12:00', '2026-11-17T13:00'],
              temperature_2m: [33.2, 34.0],
              relative_humidity_2m: [72, 70],
              apparent_temperature: [39.5, 41.0],
            },
          }),
        } as any;
      }
      return {
        ok: true,
        status: 200,
        json: async () => ({
          current: {
            pm2_5: 48.5,
          },
          hourly: {
            time: ['2026-11-17T12:00', '2026-11-17T13:00'],
            pm2_5: [48.5, 52.0],
          },
        }),
      } as any;
    });

    const result = await fetchOpenMeteoData(23.8103, 90.4125);
    expect(result).not.toBeNull();
    expect(result?.status).toBe('AVAILABLE');
    expect(result?.temperatureC).toBe(33.2);
    expect(result?.relativeHumidityPct).toBe(72);
    expect(result?.apparentTemperatureC).toBe(39.5);
    expect(result?.pm25).toBe(48.5);
    expect(result?.usAqi).toBeGreaterThan(100);
    expect(result?.hourlyForecast).toHaveLength(2);
    expect(result?.hourlyForecast?.[0].temperatureC).toBe(33.2);
  });

  test('does not fabricate dummy 28°C / 65% in hourly forecast when individual forecast point is incomplete', async () => {
    global.fetch = jest.fn().mockImplementation(async (url: string) => {
      if (url.includes('forecast')) {
        return {
          ok: true,
          status: 200,
          json: async () => ({
            current: {
              temperature_2m: 30.0,
              relative_humidity_2m: 60,
              apparent_temperature: 32.0,
              time: '2026-11-17T12:00',
            },
            hourly: {
              time: ['2026-11-17T12:00', '2026-11-17T13:00'],
              temperature_2m: [30.0, null], // Second point has null temp
              relative_humidity_2m: [60, null],
            },
          }),
        } as any;
      }
      return {
        ok: true,
        status: 200,
        json: async () => ({
          current: {
            pm2_5: 20.0,
          },
          hourly: {
            time: ['2026-11-17T12:00', '2026-11-17T13:00'],
            pm2_5: [20.0, null],
          },
        }),
      } as any;
    });

    const result = await fetchOpenMeteoData(23.8103, 90.4125);
    expect(result).not.toBeNull();
    // Incomplete hourly point must NOT be populated with fake 28°C / 65% / 35 AQI
    expect(result?.hourlyForecast).toHaveLength(1);
    expect(result?.hourlyForecast?.[0].temperatureC).toBe(30.0);
  });
});
