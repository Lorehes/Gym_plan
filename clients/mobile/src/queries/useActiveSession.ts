import { useQuery } from '@tanstack/react-query';

import { getActiveSession } from '@/api/workout';
import { useAuthStore } from '@/auth/authStore';

import { queryKeys } from './keys';

// 앱 진입 시 진행 중 세션 확인. 인증 후 1회만 자동 fetch — 이후 명시 호출 또는
// 사용자 풀-리프레시로만 갱신. 세션이 없을 땐 null 반환 (서버 명시).
export function useActiveSession() {
  const userId = useAuthStore((s) => s.user?.id ?? null);

  return useQuery({
    queryKey: queryKeys.workout.active(userId),
    queryFn: getActiveSession,
    enabled: userId !== null,
    staleTime: 0,
    // 화면 전환 때마다 다시 부르지 않도록 캐시는 유지.
    gcTime: 60 * 60 * 1000,
  });
}
