import { apiClient } from './client';
import type { MuscleGroup } from './types';

// docs/api/plan-service.md
// ⚠️ 웹 전용: 루틴 CRUD 전체. /plans/today 는 모바일 전용이라 여기 없음.

// 백엔드 ExerciseItemResponse 와 1:1 대응.
// targetWeightKg: BigDecimal? — BODYWEIGHT 운동에서 null.
// notes: String? — 운동 메모.
export interface PlanExercise {
  id: number;
  exerciseId: number;
  exerciseName: string;
  muscleGroup: MuscleGroup;
  orderIndex: number;
  targetSets: number;
  targetReps: number;
  targetWeightKg: number | null;
  restSeconds: number;
  notes: string | null;
}

// dayOfWeek: Int? — 백엔드(plan-service PlanRequests.kt) 기준 0=월 ~ 6=일, null=무요일.
// (docs/api/plan-service.md 예시는 1-based 로 기술돼 있으나 실제 코드 동작은 0-based.)
export interface PlanSummary {
  planId: number;
  name: string;
  dayOfWeek: number | null;
  exerciseCount: number;
  isTemplate: boolean;
}

export interface PlanDetail {
  planId: number;
  name: string;
  description: string | null;
  dayOfWeek: number | null;
  isTemplate: boolean;
  exercises: PlanExercise[];
}

export interface CreatePlanRequest {
  name: string;
  description?: string;
  dayOfWeek?: number | null;
}

export interface UpdatePlanRequest {
  name?: string;
  description?: string;
  dayOfWeek?: number | null;
}

export interface AddPlanExerciseRequest {
  exerciseId: number;
  exerciseName: string;
  muscleGroup: string;
  orderIndex: number;
  targetSets: number;
  targetReps: number;
  targetWeightKg?: number | null;
  restSeconds: number;
  notes?: string | null;
}

export interface UpdatePlanExerciseRequest {
  orderIndex?: number;
  targetSets?: number;
  targetReps?: number;
  targetWeightKg?: number | null;
  restSeconds?: number;
  notes?: string | null;
}

export async function fetchPlans(): Promise<PlanSummary[]> {
  const res = await apiClient.get<PlanSummary[]>('/plans');
  return res.data;
}

export async function fetchPlan(planId: number): Promise<PlanDetail> {
  const res = await apiClient.get<PlanDetail>(`/plans/${planId}`);
  return res.data;
}

export async function createPlan(body: CreatePlanRequest): Promise<PlanDetail> {
  const res = await apiClient.post<PlanDetail>('/plans', body);
  return res.data;
}

export async function updatePlan(planId: number, body: UpdatePlanRequest): Promise<PlanDetail> {
  const res = await apiClient.put<PlanDetail>(`/plans/${planId}`, body);
  return res.data;
}

export async function deletePlan(planId: number): Promise<void> {
  await apiClient.delete(`/plans/${planId}`);
}

export async function addPlanExercise(
  planId: number,
  body: AddPlanExerciseRequest,
): Promise<PlanExercise> {
  const res = await apiClient.post<PlanExercise>(`/plans/${planId}/exercises`, body);
  return res.data;
}

export async function updatePlanExercise(
  planId: number,
  exerciseItemId: number,
  body: UpdatePlanExerciseRequest,
): Promise<PlanExercise> {
  const res = await apiClient.put<PlanExercise>(
    `/plans/${planId}/exercises/${exerciseItemId}`,
    body,
  );
  return res.data;
}

export async function deletePlanExercise(planId: number, exerciseItemId: number): Promise<void> {
  await apiClient.delete(`/plans/${planId}/exercises/${exerciseItemId}`);
}

export async function reorderPlanExercises(
  planId: number,
  orderedIds: number[],
): Promise<void> {
  await apiClient.put(`/plans/${planId}/exercises/reorder`, { orderedIds });
}
