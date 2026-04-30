import { apiClient } from './client';
import type { MuscleGroup } from './types';

// docs/api/analytics-service.md
// 웹: summary / volume / frequency / personal-records 모두 호출.

export type AnalyticsPeriod = 'WEEK' | 'MONTH';

export interface AnalyticsSummary {
  period: AnalyticsPeriod;
  totalSessions: number;
  totalVolume: number;
  totalDurationSec: number;
  avgDurationSec: number;
  mostTrainedMuscle: MuscleGroup;
}

export interface VolumePoint {
  date: string; // YYYY-MM-DD
  muscle: MuscleGroup;
  volume: number;
}

export interface FrequencyEntry {
  sessionCount: number;
  totalVolume: number;
}

// 백엔드 응답: { "2026-04-01": { sessionCount, totalVolume }, ... }
export type FrequencyMap = Record<string, FrequencyEntry>;

// PersonalRecordResponse — isReliable 은 Epley 추정 신뢰도 플래그.
export interface PersonalRecord {
  exerciseId: string;
  exerciseName: string;
  maxWeightKg: number;
  maxReps: number;
  estimated1RM: number;
  isReliable: boolean;
  achievedAt: string;
}

export async function fetchSummary(period: AnalyticsPeriod = 'WEEK'): Promise<AnalyticsSummary> {
  const res = await apiClient.get<AnalyticsSummary>('/analytics/summary', { params: { period } });
  return res.data;
}

export async function fetchVolume(
  period: AnalyticsPeriod = 'WEEK',
  muscle?: MuscleGroup,
): Promise<VolumePoint[]> {
  const params: Record<string, string> = { period };
  if (muscle) params.muscle = muscle;
  const res = await apiClient.get<VolumePoint[]>('/analytics/volume', { params });
  return res.data;
}

export async function fetchFrequency(year: number, month: number): Promise<FrequencyMap> {
  const res = await apiClient.get<FrequencyMap>('/analytics/frequency', {
    params: { year, month },
  });
  return res.data;
}

export async function fetchPersonalRecords(): Promise<PersonalRecord[]> {
  const res = await apiClient.get<PersonalRecord[]>('/analytics/personal-records');
  return res.data;
}

// Epley: weight × (1 + reps/30). docs/api/analytics-service.md
export function estimate1RM(weightKg: number, reps: number): number {
  if (reps <= 0) return 0;
  return weightKg * (1 + reps / 30);
}
