// 웹 환경변수 — Vite 는 `VITE_` 프리픽스만 클라이언트로 노출.
// docs/api/common.md: Base URL = http://localhost:8080/api/v1 (로컬).

interface WebEnv {
  apiBaseUrl: string;
  isProd: boolean;
}

const apiBaseUrl =
  (import.meta.env.VITE_API_BASE_URL as string | undefined)?.replace(/\/$/, '') ??
  'http://localhost:8080/api/v1';

export const env: WebEnv = {
  apiBaseUrl,
  isProd: import.meta.env.PROD,
};
