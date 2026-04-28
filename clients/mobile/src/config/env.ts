import Constants from 'expo-constants';

type Env = {
  apiBaseUrl: string;
};

type EnvExtra = {
  apiBaseUrl?: { development?: string; production?: string };
};

const extra = (Constants.expoConfig?.extra ?? {}) as EnvExtra;
const isDev = __DEV__;

const apiBaseUrl =
  (isDev ? extra.apiBaseUrl?.development : extra.apiBaseUrl?.production) ??
  'http://localhost:8080/api/v1';

export const env: Env = { apiBaseUrl };
