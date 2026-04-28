import type { MuscleGroup } from '@/components/Badge';

export type SetLogStatus = 'pending' | 'logged' | 'failed';

export interface LocalSetLog {
  setNo: number;
  reps: number;
  weightKg: number;
  isSuccess: boolean;
  loggedAt: number; // local epoch ms
  status: SetLogStatus;
}

export interface ExerciseProgress {
  exerciseId: number;
  exerciseName: string;
  muscleGroup: MuscleGroup;
  targetSets: number;
  targetReps: number;
  targetWeightKg: number;
  restSeconds: number;

  // 다음 세트 입력값 (사용자가 탭으로 수정 가능).
  currentReps: number;
  currentWeightKg: number;

  completedSets: LocalSetLog[];
}
