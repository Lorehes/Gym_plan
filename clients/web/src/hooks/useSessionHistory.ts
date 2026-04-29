import { useQuery } from '@tanstack/react-query';

import { fetchSession, fetchSessionHistory } from '@/api/workout';

export const sessionKeys = {
  history: () => ['sessions', 'history'] as const,
  detail: (sessionId: string) => ['sessions', 'detail', sessionId] as const,
};

// 최근 200개 세션을 한 번에 가져와 월별 필터링은 클라이언트에서 처리.
// API는 startDate/endDate 필터를 지원하지 않으므로 페이지 크기로 커버.
export function useSessionHistoryQuery() {
  return useQuery({
    queryKey: sessionKeys.history(),
    queryFn: () => fetchSessionHistory({ size: 200, sort: 'startedAt,desc' }),
    staleTime: 60_000,
  });
}

export function useSessionDetailQuery(sessionId: string) {
  return useQuery({
    queryKey: sessionKeys.detail(sessionId),
    queryFn: () => fetchSession(sessionId),
    staleTime: 5 * 60_000,
    enabled: !!sessionId,
  });
}
