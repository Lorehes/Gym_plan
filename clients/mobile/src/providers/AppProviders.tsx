import { type ReactNode } from 'react';
import AsyncStorage from '@react-native-async-storage/async-storage';
import { createAsyncStoragePersister } from '@tanstack/query-async-storage-persister';
import { PersistQueryClientProvider } from '@tanstack/react-query-persist-client';

import { useReactQueryOnlineSync } from '@/hooks/useOnlineStatus';
import { queryClient } from './queryClient';

// performance-goals.md: 모바일 첫 화면 로딩 < 1초.
// AsyncStorage에 RQ 캐시 영속화 → 앱 시작 즉시 표시 → 백그라운드에서 갱신.
const persister = createAsyncStoragePersister({
  storage: AsyncStorage,
  key: 'gymplan.rq-cache.v1',
  throttleTime: 1_000,
});

interface Props {
  children: ReactNode;
}

function OnlineSync() {
  useReactQueryOnlineSync();
  return null;
}

export function AppProviders({ children }: Props) {
  return (
    <PersistQueryClientProvider
      client={queryClient}
      persistOptions={{
        persister,
        maxAge: 24 * 60 * 60 * 1000, // 24h
        // 사용자별 격리는 query key의 userId로, 토큰 스키마 변경 시 buster 증가.
        buster: 'v1',
      }}
    >
      <OnlineSync />
      {children}
    </PersistQueryClientProvider>
  );
}
