import { apiClient } from './client';

// docs/api/analytics-service.md

export type AnalyticsPeriod = 'WEEK' | 'MONTH';

export interface AnalyticsSummary {
  period: AnalyticsPeriod;
  totalSessions: number;
  totalVolume: number;
  totalDurationSec: number;
  avgDurationSec: number;
  mostTrainedMuscle: string;
}

export interface PersonalRecord {
  exerciseId: string;
  exerciseName: string;
  maxWeightKg: number;
  maxReps: number;
  estimated1RM: number;
  achievedAt: string;
}

export async function fetchSummary(period: AnalyticsPeriod = 'WEEK'): Promise<AnalyticsSummary> {
  const res = await apiClient.get<AnalyticsSummary>('/analytics/summary', { params: { period } });
  return res.data;
}

export async function fetchPersonalRecords(): Promise<PersonalRecord[]> {
  const res = await apiClient.get<PersonalRecord[]>('/analytics/personal-records');
  return res.data;
}

// Epley 공식 — analytics-service.md와 동일.
export function estimate1RM(weightKg: number, reps: number): number {
  if (reps <= 0) return 0;
  return weightKg * (1 + reps / 30);
}
