import { apiClient } from './client';
import type { Difficulty, Equipment, MuscleGroup, Page } from './types';

// docs/api/exercise-catalog.md — 모든 엔드포인트 ⚠️ 웹 전용.

export interface ExerciseSearchItem {
  exerciseId: number;
  name: string;
  nameEn: string;
  muscleGroup: MuscleGroup;
  equipment: Equipment;
  difficulty: Difficulty;
}

export interface ExerciseDetail extends ExerciseSearchItem {
  description: string | null;
  videoUrl: string | null;
  isCustom: boolean;
  createdBy: number | null;
}

export interface SearchExerciseParams {
  q?: string;
  muscle?: MuscleGroup;
  equipment?: Equipment;
  page?: number;
  size?: number;
}

export interface MuscleGroupItem {
  code: MuscleGroup;
  name: string;
}

export interface CreateCustomExerciseRequest {
  name: string;
  nameEn?: string;
  muscleGroup: MuscleGroup;
  equipment: Equipment;
  difficulty: Difficulty;
  description?: string;
  videoUrl?: string;
}

export async function searchExercises(
  params: SearchExerciseParams = {},
): Promise<Page<ExerciseSearchItem>> {
  const res = await apiClient.get<Page<ExerciseSearchItem>>('/exercises', { params });
  return res.data;
}

export async function fetchExercise(exerciseId: number): Promise<ExerciseDetail> {
  const res = await apiClient.get<ExerciseDetail>(`/exercises/${exerciseId}`);
  return res.data;
}

export async function fetchMuscleGroups(): Promise<MuscleGroupItem[]> {
  const res = await apiClient.get<MuscleGroupItem[]>('/exercises/muscle-groups');
  return res.data;
}

export async function createCustomExercise(
  body: CreateCustomExerciseRequest,
): Promise<ExerciseDetail> {
  const res = await apiClient.post<ExerciseDetail>('/exercises', body);
  return res.data;
}
