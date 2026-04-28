import { apiClient } from './client';
import type { MuscleGroup } from '@/components/Badge';

// docs/api/plan-service.md

export interface PlanExercise {
  id: number;
  exerciseId: number;
  exerciseName: string;
  muscleGroup: MuscleGroup;
  orderIndex: number;
  targetSets: number;
  targetReps: number;
  targetWeightKg: number;
  restSeconds: number;
}

export interface TodayPlan {
  planId: number;
  name: string;
  dayOfWeek: number; // 1 (Mon) ~ 7 (Sun), ISO 8601
  exercises: PlanExercise[];
}

// 오늘 요일에 배정된 루틴이 없으면 null.
export async function fetchTodayPlan(): Promise<TodayPlan | null> {
  const res = await apiClient.get<TodayPlan | null>('/plans/today');
  return res.data;
}
