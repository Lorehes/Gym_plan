import type { ActiveSession } from '@/api/workout';
import type { TodayPlan } from '@/api/plan';

import {
  selectCurrentExercise,
  selectTotalProgress,
  useWorkoutStore,
} from '../workoutStore';

const planA: TodayPlan = {
  planId: 100,
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
      notes: null,
    },
    {
      id: 2,
      exerciseId: 12,
      exerciseName: 'OHP',
      muscleGroup: 'SHOULDERS',
      orderIndex: 2,
      targetSets: 2,
      targetReps: 8,
      targetWeightKg: 40,
      restSeconds: 60,
      notes: null,
    },
  ],
};

beforeEach(() => {
  useWorkoutStore.getState().reset();
});

describe('workoutStore', () => {
  describe('세션 상태 관리', () => {
    it('start: plan으로 운동 목록을 초기화하고 currentIndex=0', () => {
      useWorkoutStore.getState().start(
        { sessionId: 'S1', startedAt: 'now', planId: '100', planName: 'Push' },
        planA,
      );
      const s = useWorkoutStore.getState();
      expect(s.session?.sessionId).toBe('S1');
      expect(s.exercises).toHaveLength(2);
      expect(s.exercises[0]!.exerciseName).toBe('Bench');
      expect(s.currentExerciseIndex).toBe(0);
    });

    it('reset: 모든 세션 상태 초기화', () => {
      useWorkoutStore.getState().start(
        { sessionId: 'S1', startedAt: 'now', planId: '100', planName: 'Push' },
        planA,
      );
      useWorkoutStore.getState().reset();
      const s = useWorkoutStore.getState();
      expect(s.session).toBeNull();
      expect(s.exercises).toEqual([]);
    });
  });

  describe('세트 추가/수정/삭제', () => {
    beforeEach(() => {
      useWorkoutStore.getState().start(
        { sessionId: 'S1', startedAt: 'now', planId: '100', planName: 'Push' },
        planA,
      );
    });

    it('appendPendingSet: setNo가 1부터 증가, status=pending', () => {
      const r1 = useWorkoutStore.getState().appendPendingSet();
      const r2 = useWorkoutStore.getState().appendPendingSet();
      expect(r1).toEqual({ exerciseIndex: 0, setNo: 1 });
      expect(r2).toEqual({ exerciseIndex: 0, setNo: 2 });

      const sets = useWorkoutStore.getState().exercises[0]!.completedSets;
      expect(sets.map((s) => s.status)).toEqual(['pending', 'pending']);
      expect(sets.map((s) => s.setNo)).toEqual([1, 2]);
    });

    it('markSetStatus: pending → logged 전이', () => {
      useWorkoutStore.getState().appendPendingSet();
      useWorkoutStore.getState().markSetStatus(0, 1, 'logged');
      const set = useWorkoutStore.getState().exercises[0]!.completedSets[0]!;
      expect(set.status).toBe('logged');
    });

    it('markSetStatus: pending → failed 전이', () => {
      useWorkoutStore.getState().appendPendingSet();
      useWorkoutStore.getState().markSetStatus(0, 1, 'failed');
      const set = useWorkoutStore.getState().exercises[0]!.completedSets[0]!;
      expect(set.status).toBe('failed');
    });

    it('removeSet: 삭제 후 뒤따르는 setNo 재정렬', () => {
      const s = useWorkoutStore.getState();
      s.appendPendingSet();
      s.appendPendingSet();
      s.appendPendingSet();
      s.removeSet(0, 2);
      const sets = useWorkoutStore.getState().exercises[0]!.completedSets;
      expect(sets.map((x) => x.setNo)).toEqual([1, 2]);
      expect(sets).toHaveLength(2);
    });

    it('updateCurrentTargets: 다음 세트 입력값 수정', () => {
      useWorkoutStore.getState().updateCurrentTargets(0, 12, 70);
      const ex = useWorkoutStore.getState().exercises[0]!;
      expect(ex.currentReps).toBe(12);
      expect(ex.currentWeightKg).toBe(70);
    });
  });

  describe('completed state 분기', () => {
    beforeEach(() => {
      useWorkoutStore.getState().start(
        { sessionId: 'S1', startedAt: 'now', planId: '100', planName: 'Push' },
        planA,
      );
    });

    it('goToNextExercise: 마지막이 아니면 true와 함께 인덱스 증가', () => {
      const ok = useWorkoutStore.getState().goToNextExercise();
      expect(ok).toBe(true);
      expect(useWorkoutStore.getState().currentExerciseIndex).toBe(1);
    });

    it('goToNextExercise: 마지막에서 호출하면 false (완료 분기)', () => {
      useWorkoutStore.getState().goToNextExercise(); // 0 → 1
      const ok = useWorkoutStore.getState().goToNextExercise(); // 1 → ?
      expect(ok).toBe(false);
      expect(useWorkoutStore.getState().currentExerciseIndex).toBe(1);
    });

    it('selectTotalProgress: failed 세트는 doneSets에 미포함', () => {
      const s = useWorkoutStore.getState();
      s.appendPendingSet(); // 1
      s.appendPendingSet(); // 2
      s.markSetStatus(0, 1, 'logged');
      s.markSetStatus(0, 2, 'failed');

      const { totalSets, doneSets } = selectTotalProgress(useWorkoutStore.getState());
      expect(totalSets).toBe(5); // 3 + 2
      expect(doneSets).toBe(1); // logged 하나만
    });

    it('selectCurrentExercise: 현재 인덱스 운동을 반환', () => {
      const ex = selectCurrentExercise(useWorkoutStore.getState());
      expect(ex?.exerciseName).toBe('Bench');
    });
  });

  describe('recoverFromActive', () => {
    const active: ActiveSession = {
      sessionId: 'S2',
      planId: '100',
      planName: 'Push',
      startedAt: 'now',
      completedAt: null,
      status: 'IN_PROGRESS',
      totalVolume: 0,
      totalSets: 0,
      durationSec: 0,
      notes: null,
      exercises: [
        {
          exerciseId: '11',
          exerciseName: 'Bench',
          muscleGroup: 'CHEST',
          sets: [
            {
              setNo: 1,
              reps: 10,
              weightKg: 60,
              isSuccess: true,
              completedAt: '2026-04-28T10:00:00Z',
            },
          ],
        },
      ],
    };

    it('plan 일치 시: plan 골격 + 기록된 세트 복구', () => {
      useWorkoutStore.getState().recoverFromActive(active, planA);
      const s = useWorkoutStore.getState();
      expect(s.session?.sessionId).toBe('S2');
      expect(s.exercises).toHaveLength(2);
      expect(s.exercises[0]!.completedSets).toHaveLength(1);
      expect(s.exercises[0]!.completedSets[0]!.status).toBe('logged');
      // 첫 미완료(=Bench, 1/3)에 위치
      expect(s.currentExerciseIndex).toBe(0);
    });

    it('plan 불일치 시: sets에서 추론 (자유 운동 fallback)', () => {
      useWorkoutStore.getState().recoverFromActive(active, null);
      const s = useWorkoutStore.getState();
      expect(s.exercises).toHaveLength(1);
      expect(s.exercises[0]!.exerciseName).toBe('Bench');
      // targetSets는 maxSetNo=1로 추론, 이미 1세트 기록 → 모두 완료 → 마지막 인덱스
      expect(s.exercises[0]!.targetSets).toBe(1);
    });

    it('plan은 있지만 planId가 다르면 sets 추론으로 fallback', () => {
      const otherPlan: TodayPlan = { ...planA, planId: 999 };
      useWorkoutStore.getState().recoverFromActive(active, otherPlan);
      const s = useWorkoutStore.getState();
      // 추론 모드 — Bench 한 종목만
      expect(s.exercises).toHaveLength(1);
    });
  });
});
