import { apiClient } from './client';
import type { MuscleGroup, Page } from './types';

// docs/api/workout-service.md
// ⚠️ 웹은 회고 엔드포인트만: GET /sessions/history, GET /sessions/{id}
// 진행 중 세션(POST/sets/complete/cancel/active)과 SSE 는 모바일 전용.

// SessionDetailResponse — /sessions/{id} 와 동일.
// nested exercises[].sets[] 구조 (flat sets 아님!).
// planId 는 String (MongoDB) — plan-service 의 number 와 비교 시 변환 필요.

export type SessionStatus = 'IN_PROGRESS' | 'COMPLETED' | 'CANCELLED';

export interface SessionSetEntry {
  setNo: number;
  reps: number;
  weightKg: number;
  isSuccess: boolean;
  completedAt: string;
}

export interface SessionExercise {
  exerciseId: string;
  exerciseName: string;
  muscleGroup: MuscleGroup;
  sets: SessionSetEntry[];
}

export interface SessionDetail {
  sessionId: string;
  planId: string | null;
  planName: string | null;
  startedAt: string;
  completedAt: string | null;
  status: SessionStatus;
  totalVolume: number;
  totalSets: number;
  durationSec: number;
  notes: string | null;
  exercises: SessionExercise[];
}

export interface SessionHistoryParams {
  page?: number;
  size?: number;
  sort?: string; // 예: 'startedAt,desc'
}

export async function fetchSessionHistory(
  params: SessionHistoryParams = {},
): Promise<Page<SessionDetail>> {
  const res = await apiClient.get<Page<SessionDetail>>('/sessions/history', {
    params: { sort: 'startedAt,desc', ...params },
  });
  return res.data;
}

export async function fetchSession(sessionId: string): Promise<SessionDetail> {
  const res = await apiClient.get<SessionDetail>(`/sessions/${sessionId}`);
  return res.data;
}
