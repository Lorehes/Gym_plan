import axios, { AxiosError, type AxiosInstance, type InternalAxiosRequestConfig } from 'axios';

import { env } from '@/config/env';
import { tokenStorage } from '@/auth/tokenStorage';
import { ApiException, type ApiResponse } from './types';

type RetriableConfig = InternalAxiosRequestConfig & { _retried?: boolean };

// onUnauthorized: 토큰 갱신 실패 시 호출 — auth store가 등록.
let onUnauthorized: (() => void) | null = null;
export function setUnauthorizedHandler(fn: (() => void) | null) {
  onUnauthorized = fn;
}

// refresh 응답 페이로드 (workout/user 서비스 합의).
interface RefreshResponse {
  accessToken: string;
  refreshToken: string;
}

// 동시 401 요청을 하나의 refresh 호출로 병합.
let refreshPromise: Promise<string> | null = null;

async function refreshAccessToken(api: AxiosInstance): Promise<string> {
  const refreshToken = await tokenStorage.getRefreshToken();
  if (!refreshToken) throw new Error('No refresh token');

  // refresh는 반드시 raw axios로 — 인터셉터 재진입 방지.
  const res = await axios.post<ApiResponse<RefreshResponse>>(
    `${env.apiBaseUrl}/auth/refresh`,
    { refreshToken },
    { headers: { 'Content-Type': 'application/json' }, timeout: 10_000 },
  );
  if (!res.data.success) throw new Error(res.data.error.code);

  const { accessToken, refreshToken: nextRefresh } = res.data.data;
  await tokenStorage.setTokens({ accessToken, refreshToken: nextRefresh });
  return accessToken;
}

export const apiClient: AxiosInstance = axios.create({
  baseURL: env.apiBaseUrl,
  timeout: 15_000,
  headers: { 'Content-Type': 'application/json' },
});

// Request: Authorization 헤더 주입.
apiClient.interceptors.request.use(async (config) => {
  const token = await tokenStorage.getAccessToken();
  if (token) {
    config.headers.set('Authorization', `Bearer ${token}`);
  }
  return config;
});

// Response: envelope 풀기 + 401 refresh 재시도 + 에러 정규화.
apiClient.interceptors.response.use(
  (response) => {
    const body = response.data as ApiResponse<unknown> | undefined;
    if (body && typeof body === 'object' && 'success' in body) {
      if (body.success) {
        response.data = body.data;
        return response;
      }
      throw new ApiException(body.error.code, body.error.message, response.status, body.error.details);
    }
    return response;
  },
  async (error: AxiosError<ApiResponse<unknown>>) => {
    const original = error.config as RetriableConfig | undefined;
    const status = error.response?.status;
    const body = error.response?.data;

    // 401 → refresh 후 1회 재시도.
    const isAuthError =
      status === 401 ||
      (body && typeof body === 'object' && 'error' in body && body.error?.code === 'AUTH_EXPIRED_TOKEN');

    if (isAuthError && original && !original._retried) {
      original._retried = true;
      try {
        refreshPromise ??= refreshAccessToken(apiClient).finally(() => {
          refreshPromise = null;
        });
        const newToken = await refreshPromise;
        original.headers.set('Authorization', `Bearer ${newToken}`);
        return apiClient.request(original);
      } catch (refreshError) {
        await tokenStorage.clear();
        onUnauthorized?.();
        throw new ApiException(
          'AUTH_EXPIRED_TOKEN',
          '세션이 만료되었습니다. 다시 로그인해주세요.',
          401,
        );
      }
    }

    // 서버 에러 응답 → ApiException으로 통일.
    if (body && typeof body === 'object' && 'success' in body && body.success === false) {
      throw new ApiException(
        body.error.code,
        body.error.message,
        status ?? 0,
        body.error.details,
      );
    }

    // 네트워크/타임아웃 등.
    throw new ApiException(
      'NETWORK_ERROR',
      error.message || '네트워크 오류가 발생했습니다.',
      status ?? 0,
    );
  },
);
