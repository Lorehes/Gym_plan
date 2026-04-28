import React from 'react';
import { renderHook, act } from '@testing-library/react-native';
import { onlineManager } from '@tanstack/react-query';

import { ApiException } from '@/api/types';
import * as workoutApi from '@/api/workout';
import type { TodayPlan } from '@/api/plan';

import { useSyncWorker } from '../syncWorker';
import { drainOnce, useSyncQueue } from '../syncQueue';
import { useWorkoutStore } from '../workoutStore';

jest.mock('@/api/workout', () => ({
  __esModule: true,
  logSet: jest.fn(),
}));

const logSetMock = workoutApi.logSet as jest.MockedFunction<typeof workoutApi.logSet>;

const plan: TodayPlan = {
  planId: 200,
  name: 'Push',
  dayOfWeek: 1,
  exercises: [
    {
      id: 1,
      exerciseId: 11,
      exerciseName: 'Bench',
      muscleGroup: 'CHEST',
      orderIndex: 1,
      targetSets: 3,
      targetReps: 10,
      targetWeightKg: 60,
      restSeconds: 90,
    },
  ],
};

function mountWorker() {
  return renderHook(() => useSyncWorker());
}

function payload(setNo: number) {
  return {
    exerciseId: '11',
    exerciseName: 'Bench',
    muscleGroup: 'CHEST' as const,
    setNo,
    reps: 10,
    weightKg: 60,
    isSuccess: true,
  };
}

function enqueue(sessionId: string, setNo: number) {
  return useSyncQueue.getState().enqueue({
    sessionId,
    exerciseLocalIndex: 0,
    setNo,
    payload: payload(setNo),
  });
}

beforeEach(() => {
  logSetMock.mockReset();
  useSyncQueue.setState({ items: [] });
  useWorkoutStore.getState().reset();
  onlineManager.setOnline(true);
});

describe('useSyncWorker', () => {
  it('성공 drain → workoutStore의 pending이 logged로 전이', async () => {
    useWorkoutStore.getState().start(
      { sessionId: 'CUR', startedAt: 'now', planId: 200, planName: 'Push' },
      plan,
    );
    const r = useWorkoutStore.getState().appendPendingSet();
    expect(r).toBeTruthy();

    logSetMock.mockResolvedValue(undefined);
    enqueue('CUR', 1);

    mountWorker();
    await act(async () => {
      await drainOnce();
    });

    const set = useWorkoutStore.getState().exercises[0]!.completedSets[0]!;
    expect(set.status).toBe('logged');
  });

  it('sessionId 가드: 다른 세션의 success 이벤트는 현재 store에 영향 없음', async () => {
    useWorkoutStore.getState().start(
      { sessionId: 'CUR', startedAt: 'now', planId: 200, planName: 'Push' },
      plan,
    );
    useWorkoutStore.getState().appendPendingSet();

    logSetMock.mockResolvedValue(undefined);
    enqueue('OTHER', 1); // 이전 세션 잔재

    mountWorker();
    await act(async () => {
      await drainOnce();
    });

    // 현재 세션 세트는 여전히 pending
    const set = useWorkoutStore.getState().exercises[0]!.completedSets[0]!;
    expect(set.status).toBe('pending');
  });

  it('dead 이벤트 → 현재 세션이면 failed로 전이', async () => {
    useWorkoutStore.getState().start(
      { sessionId: 'CUR', startedAt: 'now', planId: 200, planName: 'Push' },
      plan,
    );
    useWorkoutStore.getState().appendPendingSet();

    logSetMock.mockRejectedValueOnce(new ApiException('SESSION_NOT_FOUND', 'gone', 404));
    enqueue('CUR', 1);

    mountWorker();
    await act(async () => {
      await drainOnce();
    });

    const set = useWorkoutStore.getState().exercises[0]!.completedSets[0]!;
    expect(set.status).toBe('failed');
  });

  it('백오프 동작: 5xx 실패 후 같은 항목은 즉시 다시 호출되지 않는다', async () => {
    useWorkoutStore.getState().start(
      { sessionId: 'CUR', startedAt: 'now', planId: 200, planName: 'Push' },
      plan,
    );
    useWorkoutStore.getState().appendPendingSet();

    logSetMock.mockRejectedValue(new ApiException('SRV', 'boom', 500));
    enqueue('CUR', 1);

    mountWorker();

    await act(async () => {
      await drainOnce();
    });
    expect(logSetMock).toHaveBeenCalledTimes(1);

    // 즉시 재드레인해도 backoff로 인해 호출 차단 (대기 후 재시도가 예약됨)
    await act(async () => {
      await drainOnce();
    });
    expect(logSetMock).toHaveBeenCalledTimes(1);

    const item = useSyncQueue.getState().items[0]!;
    expect(item.status).toBe('queued');
    expect(item.attempts).toBe(1);
  });

  it('진행 이벤트(progress)는 현재 세션이어도 store status를 변경하지 않는다', async () => {
    useWorkoutStore.getState().start(
      { sessionId: 'CUR', startedAt: 'now', planId: 200, planName: 'Push' },
      plan,
    );
    useWorkoutStore.getState().appendPendingSet();

    // 5xx 두 번 → progress 이벤트가 emit되지만 worker는 무시 → pending 유지
    logSetMock.mockRejectedValue(new ApiException('SRV', 'boom', 500));
    enqueue('CUR', 1);

    mountWorker();
    await act(async () => {
      await drainOnce();
    });

    const set = useWorkoutStore.getState().exercises[0]!.completedSets[0]!;
    expect(set.status).toBe('pending');
  });
});
