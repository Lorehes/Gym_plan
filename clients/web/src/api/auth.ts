import { apiClient } from './client';

// docs/api/user-service.md — /api/v1/auth, /api/v1/users
// 모바일과 동일 계약.

export interface LoginRequest {
  email: string;
  password: string;
}

export interface RegisterRequest {
  email: string;
  password: string;
  nickname: string;
}

export interface LoginResponse {
  userId: number;
  nickname: string;
  accessToken: string;
  refreshToken: string;
}

export interface RegisterResponse extends LoginResponse {
  email: string;
}

export interface MeResponse {
  userId: number;
  email: string;
  nickname: string;
  profileImg: string | null;
  createdAt: string;
}

export interface UpdateMeRequest {
  nickname?: string;
  profileImg?: string | null;
}

export async function login(body: LoginRequest): Promise<LoginResponse> {
  const res = await apiClient.post<LoginResponse>('/auth/login', body);
  return res.data;
}

export async function register(body: RegisterRequest): Promise<RegisterResponse> {
  const res = await apiClient.post<RegisterResponse>('/auth/register', body);
  return res.data;
}

export async function logout(): Promise<void> {
  await apiClient.post('/auth/logout');
}

export async function fetchMe(): Promise<MeResponse> {
  const res = await apiClient.get<MeResponse>('/users/me');
  return res.data;
}

// 웹 전용 — docs/api/user-service.md (PUT /users/me 는 ⚠️ 웹 전용).
export async function updateMe(body: UpdateMeRequest): Promise<MeResponse> {
  const res = await apiClient.put<MeResponse>('/users/me', body);
  return res.data;
}
