import { QueryClient } from '@tanstack/react-query';

import { ApiException } from '@/api/types';

export const queryClient = new QueryClient({
  defaultOptions: {
    queries: {
      staleTime: 30_000,
      // gcTime > maxAge of persister여야 영속화된 캐시가 hydrate 시 살아남음.
      gcTime: 24 * 60 * 60 * 1000,
      retry: (failureCount, error) => {
        // 4xx는 재시도 의미 없음.
        if (error instanceof ApiException && error.status >= 400 && error.status < 500) {
          return false;
        }
        return failureCount < 2;
      },
      refetchOnWindowFocus: false,
    },
    mutations: {
      retry: 0,
    },
  },
});
