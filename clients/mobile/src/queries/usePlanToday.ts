import { useQuery } from '@tanstack/react-query';

import { fetchTodayPlan } from '@/api/plan';
import { useAuthStore } from '@/auth/authStore';

import { queryKeys } from './keys';

// performance-goals.md: AsyncStorage에 캐시된 데이터를 즉시 표시 → 백그라운드 갱신.
// staleTime 0 → 마운트 시 항상 refetch (캐시는 즉시 노출되지만 fresh 보장).
export function usePlanToday() {
  const userId = useAuthStore((s) => s.user?.id ?? null);

  return useQuery({
    queryKey: queryKeys.plan.today(userId),
    queryFn: fetchTodayPlan,
    enabled: userId !== null,
    staleTime: 0,
  });
}
