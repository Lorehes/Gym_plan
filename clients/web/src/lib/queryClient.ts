import { QueryClient } from '@tanstack/react-query';

import { ApiException } from '@/api/types';

// 인증 만료(401)는 인터셉터가 처리하므로 retry 하지 않는다.
export const queryClient = new QueryClient({
  defaultOptions: {
    queries: {
      staleTime: 30_000,
      gcTime: 5 * 60_000,
      retry: (failureCount, error) => {
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
