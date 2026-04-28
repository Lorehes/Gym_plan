import React from 'react';
import { renderHook, act } from '@testing-library/react-native';
import NetInfo from '@react-native-community/netinfo';
import { onlineManager } from '@tanstack/react-query';

import { ApiException } from '@/api/types';
import * as workoutApi from '@/api/workout';
import { drainOnce, useSyncQueue } from '@/workout/syncQueue';

import { useReactQueryOnlineSync } from '../useOnlineStatus';

jest.mock('@/api/workout', () => ({
  __esModule: true,
  logSet: jest.fn(),
}));

const logSetMock = workoutApi.logSet as jest.MockedFunction<typeof workoutApi.logSet>;

beforeEach(() => {
  logSetMock.mockReset();
  useSyncQueue.setState({ items: [] });
  onlineManager.setOnline(true);
  // NetInfo mock 초기화
  (NetInfo as unknown as { __reset: () => void }).__reset();
});

describe('useReactQueryOnlineSync', () => {
  it('NetInfo addEventListener를 등록한다 (NetInfo → onlineManager 다리)', () => {
    renderHook(() => useReactQueryOnlineSync());
    expect(NetInfo.addEventListener).toHaveBeenCalled();
  });

  it('NetInfo 상태 변화 → onlineManager.setOnline 동기화', () => {
    renderHook(() => useReactQueryOnlineSync());

    act(() => {
      (NetInfo as unknown as { __setState: (s: any) => void }).__setState({
        isConnected: false,
        isInternetReachable: false,
      });
    });
    expect(onlineManager.isOnline()).toBe(false);

    act(() => {
      (NetInfo as unknown as { __setState: (s: any) => void }).__setState({
        isConnected: true,
        isInternetReachable: true,
      });
    });
    expect(onlineManager.isOnline()).toBe(true);
  });

  it('isInternetReachable=null(미확정)도 온라인으로 취급', () => {
    renderHook(() => useReactQueryOnlineSync());

    act(() => {
      (NetInfo as unknown as { __setState: (s: any) => void }).__setState({
        isConnected: true,
        isInternetReachable: null,
      });
    });
    expect(onlineManager.isOnline()).toBe(true);
  });

  it('네트워크 복구 시 큐 drain (onlineManager 구독자에 의해)', async () => {
    // 시나리오: 오프라인 상태에서 큐 적재 → 온라인 전환 → drain
    onlineManager.setOnline(false);
    logSetMock.mockResolvedValue(undefined);

    useSyncQueue.getState().enqueue({
      sessionId: 'S1',
      exerciseLocalIndex: 0,
      setNo: 1,
      payload: {
        exerciseId: '11',
        exerciseName: 'Bench',
        muscleGroup: 'CHEST',
        setNo: 1,
        reps: 10,
        weightKg: 60,
        isSuccess: true,
      },
    });

    // 외부에서 syncQueue가 onlineManager에 의존하지 않으므로,
    // 여기서는 NetInfo → onlineManager 동기화 + 그 결과 drain이 동작하는 흐름을 검증.
    const unsub = onlineManager.subscribe((online) => {
      if (online) void drainOnce();
    });

    renderHook(() => useReactQueryOnlineSync());

    await act(async () => {
      (NetInfo as unknown as { __setState: (s: any) => void }).__setState({
        isConnected: true,
        isInternetReachable: true,
      });
      await Promise.resolve();
      await Promise.resolve();
      await Promise.resolve();
    });

    expect(onlineManager.isOnline()).toBe(true);
    expect(logSetMock).toHaveBeenCalled();
    expect(useSyncQueue.getState().items).toHaveLength(0);
    unsub();
  });

  it('네트워크 끊김 시 onlineManager가 false로 전환', () => {
    renderHook(() => useReactQueryOnlineSync());

    act(() => {
      (NetInfo as unknown as { __setState: (s: any) => void }).__setState({
        isConnected: true,
        isInternetReachable: false, // 인터넷 도달 불가
      });
    });
    expect(onlineManager.isOnline()).toBe(false);
  });
});
