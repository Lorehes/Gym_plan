import { useQuery } from '@tanstack/react-query';

import {
  fetchFrequency,
  fetchPersonalRecords,
  fetchSummary,
  fetchVolume,
  type AnalyticsPeriod,
} from '@/api/analytics';

const STALE = 5 * 60 * 1000; // 5분 — 분석 데이터는 빠르게 바뀌지 않음

export const analyticsKeys = {
  summary: (period: AnalyticsPeriod) => ['analytics', 'summary', period] as const,
  volume: (period: AnalyticsPeriod) => ['analytics', 'volume', period] as const,
  frequency: (year: number, month: number) => ['analytics', 'frequency', year, month] as const,
  personalRecords: () => ['analytics', 'personal-records'] as const,
};

export function useSummaryQuery(period: AnalyticsPeriod) {
  return useQuery({
    queryKey: analyticsKeys.summary(period),
    queryFn: () => fetchSummary(period),
    staleTime: STALE,
  });
}

export function useVolumeQuery(period: AnalyticsPeriod) {
  return useQuery({
    queryKey: analyticsKeys.volume(period),
    queryFn: () => fetchVolume(period),
    staleTime: STALE,
  });
}

export function useFrequencyQuery(year: number, month: number) {
  return useQuery({
    queryKey: analyticsKeys.frequency(year, month),
    queryFn: () => fetchFrequency(year, month),
    staleTime: STALE,
  });
}

export function usePersonalRecordsQuery() {
  return useQuery({
    queryKey: analyticsKeys.personalRecords(),
    queryFn: fetchPersonalRecords,
    staleTime: STALE,
  });
}
