import React from 'react';
import { Alert } from 'react-native';
import { act, render, waitFor } from '@testing-library/react-native';

import type { ActiveSession } from '@/api/workout';
import type { TodayPlan } from '@/api/plan';
import { useWorkoutStore } from '@/workout/workoutStore';

import { useSessionRecovery } from '../useSessionRecovery';

const mockActive = jest.fn();
const mockPlan = jest.fn();

jest.mock('@/queries/useActiveSession', () => ({
  useActiveSession: () => mockActive(),
}));
jest.mock('@/queries/usePlanToday', () => ({
  usePlanToday: () => mockPlan(),
}));
jest.mock('@/api/analytics', () => ({
  __esModule: true,
  fetchPersonalRecords: jest.fn(() => Promise.resolve([])),
}));

function Harness() {
  useSessionRecovery();
  return null;
}

const planSame: TodayPlan = {
  planId: 200,
  name: 'Pull',
  dayOfWeek: 2,
  exercises: [
    {
      id: 1,
      exerciseId: 21,
      exerciseName: 'Row',
      muscleGroup: 'BACK',
      orderIndex: 1,
      targetSets: 3,
      targetReps: 10,
      targetWeightKg: 50,
      restSeconds: 60,
    },
  ],
};

const activeMatching: ActiveSession = {
  sessionId: 'S100',
  planId: '200',
  planName: 'Pull',
  startedAt: '2026-04-28T10:00:00Z',
  completedAt: null,
  status: 'IN_PROGRESS',
  totalVolume: 0,
  totalSets: 0,
  durationSec: 0,
  notes: null,
  exercises: [],
};

beforeEach(() => {
  useWorkoutStore.getState().reset();
  mockActive.mockReset();
  mockPlan.mockReset();
  (Alert.alert as jest.Mock).mockClear();
});

describe('useSessionRecovery', () => {
  it('진행 중 세션 없음 → 복구 안 함', async () => {
    mockActive.mockReturnValue({ data: null, isPending: false });
    mockPlan.mockReturnValue({ data: null, isPending: false });

    render(<Harness />);
    await waitFor(() => {
      // wait a tick — should remain reset
      expect(useWorkoutStore.getState().session).toBeNull();
    });
    expect(Alert.alert).not.toHaveBeenCalled();
  });

  it('active 응답이 pending이면 복구 시도하지 않음 (GET /sessions/active 대기)', async () => {
    mockActive.mockReturnValue({ data: undefined, isPending: true });
    mockPlan.mockReturnValue({ data: null, isPending: false });

    render(<Harness />);
    expect(useWorkoutStore.getState().session).toBeNull();
    expect(Alert.alert).not.toHaveBeenCalled();
  });

  it('planId 일치 시: plan 기반 복구 + 알림', async () => {
    mockActive.mockReturnValue({ data: activeMatching, isPending: false });
    mockPlan.mockReturnValue({ data: planSame, isPending: false });

    render(<Harness />);
    await waitFor(() => {
      expect(useWorkoutStore.getState().session?.sessionId).toBe('S100');
    });
    expect(useWorkoutStore.getState().exercises).toHaveLength(1);
    expect(Alert.alert).toHaveBeenCalled();
    const message = (Alert.alert as jest.Mock).mock.calls[0][1];
    expect(message).toContain('이어서');
  });

  it('plan 변경(불일치) 시: 자유 운동 모드 fallback + "자유 운동" 메시지', async () => {
    const planChanged: TodayPlan = { ...planSame, planId: 999 };
    const activeWithSets: ActiveSession = {
      ...activeMatching,
      exercises: [
        {
          exerciseId: '21',
          exerciseName: 'Row',
          muscleGroup: 'BACK',
          sets: [
            {
              setNo: 1,
              reps: 10,
              weightKg: 50,
              isSuccess: true,
              completedAt: '2026-04-28T10:05:00Z',
            },
          ],
        },
      ],
    };
    mockActive.mockReturnValue({ data: activeWithSets, isPending: false });
    mockPlan.mockReturnValue({ data: planChanged, isPending: false });

    render(<Harness />);
    await waitFor(() => {
      expect(useWorkoutStore.getState().session?.sessionId).toBe('S100');
    });

    // 자유 운동: sets에서 추론 — 1개 운동
    expect(useWorkoutStore.getState().exercises).toHaveLength(1);
    const message = (Alert.alert as jest.Mock).mock.calls[0][1];
    expect(message).toContain('자유 운동');
  });

  it('이미 로컬 세션이 있으면 복구하지 않음 (사용자 진행 중 보호)', async () => {
    useWorkoutStore.getState().start(
      { sessionId: 'LOCAL', startedAt: 'now', planId: '200', planName: 'Pull' },
      planSame,
    );
    mockActive.mockReturnValue({ data: activeMatching, isPending: false });
    mockPlan.mockReturnValue({ data: planSame, isPending: false });

    render(<Harness />);
    // 다음 마이크로태스크까지 기다려도 변화 없어야 함
    await waitFor(() => Promise.resolve());
    expect(useWorkoutStore.getState().session?.sessionId).toBe('LOCAL');
    expect(Alert.alert).not.toHaveBeenCalled();
  });

  it('동일 sessionId에 대해 두 번 복구하지 않는다 (idempotent)', async () => {
    mockActive.mockReturnValue({ data: activeMatching, isPending: false });
    mockPlan.mockReturnValue({ data: planSame, isPending: false });

    const { rerender } = render(<Harness />);
    await waitFor(() => {
      expect(useWorkoutStore.getState().session?.sessionId).toBe('S100');
    });

    // 사용자가 명시적으로 reset 했다고 가정 — recoveredFor ref가 같으면 다시 안 함
    act(() => {
      useWorkoutStore.getState().reset();
    });
    rerender(<Harness />);

    await waitFor(() => Promise.resolve());
    expect(useWorkoutStore.getState().session).toBeNull();
    // Alert는 첫 복구에서만 호출
    expect((Alert.alert as jest.Mock).mock.calls.length).toBe(1);
  });

  it('active.planId 있는데 plan 응답이 pending이면 대기', async () => {
    mockActive.mockReturnValue({ data: activeMatching, isPending: false });
    mockPlan.mockReturnValue({ data: undefined, isPending: true });

    render(<Harness />);
    await waitFor(() => Promise.resolve());
    expect(useWorkoutStore.getState().session).toBeNull();
  });
});
