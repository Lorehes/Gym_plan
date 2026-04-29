import { apiClient } from './client';
import type { MuscleGroup } from '@/components/Badge';

// docs/api/plan-service.md

// 백엔드 ExerciseItemResponse(plan-service PlanResponses.kt) 와 1:1 대응.
// targetWeightKg 는 BigDecimal? — 맨몸 운동(BODYWEIGHT)에서 null.
// notes 는 운동 항목 메모 (예: "가슴 수축 집중").
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

// dayOfWeek 는 Int? — /plans/today 응답은 사실상 오늘 요일(non-null)이지만
// 백엔드 직렬화 계약을 따름. 0=월, 1=화, ..., 6=일 (0-based, 서버와 동일).
export interface TodayPlan {
  planId: number;
  name: string;
  dayOfWeek: number | null;
  exercises: PlanExercise[];
}

// 오늘 요일에 배정된 루틴이 없으면 null.
export async function fetchTodayPlan(): Promise<TodayPlan | null> {
  const res = await apiClient.get<TodayPlan | null>('/plans/today');
  return res.data;
}
