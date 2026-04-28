import { apiClient } from './client';
import type { MuscleGroup } from '@/components/Badge';

// docs/api/workout-service.md

export interface SessionStartRequest {
  planId?: number;
  planName?: string;
}

export interface SessionStart {
  sessionId: string;
  startedAt: string;
  status: 'IN_PROGRESS' | 'COMPLETED' | 'CANCELLED';
}

export interface SetLogRequest {
  exerciseId: string;
  exerciseName: string;
  muscleGroup: MuscleGroup;
  setNo: number;
  reps: number;
  weightKg: number;
  isSuccess: boolean;
}

export interface SessionComplete {
  sessionId: string;
  durationSec: number;
  totalVolume: number;
  totalSets: number;
}

// 백엔드 SessionDetailResponse.exercises[].sets[] 와 1:1 대응.
// completedAt 은 mobile 에서 표시에 사용하지 않지만 스키마 보존.
export interface ActiveSetEntry {
  setNo: number;
  reps: number;
  weightKg: number;
  isSuccess: boolean;
  completedAt: string;
}

export interface ActiveExercise {
  exerciseId: string;
  exerciseName: string;
  muscleGroup: MuscleGroup;
  sets: ActiveSetEntry[];
}

// 백엔드 SessionDetailResponse 와 1:1 대응 (workout-service SessionResponses.kt).
// planId 는 백엔드가 String 으로 직렬화 — TodayPlan.planId(number) 와 비교 시 변환 필요.
// status 는 활성 세션 조회이므로 IN_PROGRESS 만 도착 (completedAt IS NULL 조건).
export interface ActiveSession {
  sessionId: string;
  planId: string | null;
  planName: string | null;
  startedAt: string;
  completedAt: string | null;
  status: 'IN_PROGRESS';
  totalVolume: number;
  totalSets: number;
  durationSec: number;
  notes: string | null;
  exercises: ActiveExercise[];
}

export async function startSession(body: SessionStartRequest): Promise<SessionStart> {
  const res = await apiClient.post<SessionStart>('/sessions', body);
  return res.data;
}

export async function getActiveSession(): Promise<ActiveSession | null> {
  const res = await apiClient.get<ActiveSession | null>('/sessions/active');
  return res.data;
}

export async function logSet(sessionId: string, body: SetLogRequest): Promise<void> {
  await apiClient.post(`/sessions/${sessionId}/sets`, body);
}

export async function deleteSet(
  sessionId: string,
  setNo: number,
  exerciseId: string,
): Promise<void> {
  await apiClient.delete(`/sessions/${sessionId}/sets/${setNo}/${exerciseId}`);
}

export async function completeSession(
  sessionId: string,
  notes?: string,
): Promise<SessionComplete> {
  const res = await apiClient.post<SessionComplete>(
    `/sessions/${sessionId}/complete`,
    { notes: notes ?? '' },
  );
  return res.data;
}

// 세션 중도 포기 — /complete와 대칭 패턴(POST /cancel).
// 서버 status를 CANCELLED로 마킹하고 큐 정리. analytics 이벤트는 발행하지 않음.
// (주의: workout-service.md에 명시되지 않은 엔드포인트 — 백엔드 추가 필요.
// 미구현 시 404/405 반환되며, 호출자가 graceful fallback 처리.)
export async function cancelSession(sessionId: string): Promise<void> {
  await apiClient.post(`/sessions/${sessionId}/cancel`);
}
