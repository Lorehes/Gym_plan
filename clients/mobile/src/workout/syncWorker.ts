import { useEffect } from 'react';
import { onlineManager } from '@tanstack/react-query';

import { useWorkoutStore } from './workoutStore';
import { drainOnce, subscribeDrain } from './syncQueue';

// React Query onlineManager (NetInfo 연결됨)를 활용 — 단일 진실.
// 온라인 전환 시마다 drain 트리거.
export function useSyncWorker() {
  useEffect(() => {
    // 온라인 상태 변화 구독 — false → true 일 때만 drain.
    const unsub = onlineManager.subscribe((online) => {
      if (online) drainOnce();
    });

    // 초기 시도: 이미 온라인이면 잔여 큐 즉시 드레인.
    if (onlineManager.isOnline()) drainOnce();

    return unsub;
  }, []);

  // 큐 이벤트를 workoutStore와 동기화.
  useEffect(() => {
    return subscribeDrain((event) => {
      const currentSessionId = useWorkoutStore.getState().session?.sessionId ?? null;
      if (currentSessionId !== event.item.sessionId) return;

      if (event.type === 'success') {
        useWorkoutStore
          .getState()
          .markSetStatus(event.item.exerciseLocalIndex, event.item.setNo, 'logged');
      } else if (event.type === 'dead') {
        useWorkoutStore
          .getState()
          .markSetStatus(event.item.exerciseLocalIndex, event.item.setNo, 'failed');
      }
      // 'progress'는 store에 영향 없음 — pending 유지.
    });
  }, []);
}
