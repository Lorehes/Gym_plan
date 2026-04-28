import { create } from 'zustand';

import type { PlanExercise, TodayPlan } from '@/api/plan';
import type { PersonalRecord } from '@/api/analytics';
import type { ActiveSession, ActiveSetEntry } from '@/api/workout';
import type { MuscleGroup } from '@/components/Badge';

import type { ExerciseProgress, LocalSetLog, SetLogStatus } from './types';

// 운동 실행 화면 전용 로컬 상태.
// performance-goals.md: 세트 체크인 → 즉시 로컬 업데이트, API는 백그라운드.
// 실패 시 마지막 pending 항목을 rollback 또는 failed로 표시.

interface ActiveSessionMeta {
  sessionId: string;
  startedAt: string;
  planId: number | null;
  planName: string | null;
}

interface WorkoutState {
  session: ActiveSessionMeta | null;
  exercises: ExerciseProgress[];
  currentExerciseIndex: number;

  // 세션 시작 시점의 PR 스냅샷 — 종료 시 신기록 비교에 사용.
  prSnapshot: PersonalRecord[];

  // 세션 시작 — 서버 응답으로 sessionId 받은 직후 호출.
  start: (session: ActiveSessionMeta, plan: TodayPlan) => void;

  // 앱 진입 시 진행 중 세션 복구. plan이 active.planId와 일치하면 plan 기반으로
  // 풀 정보 복구, 일치하지 않으면 active.sets에서 운동 구조를 추론.
  recoverFromActive: (active: ActiveSession, plan: TodayPlan | null) => void;

  setPRSnapshot: (snapshot: PersonalRecord[]) => void;

  // 세트 추가 (Optimistic). 다음 setNo와 함께 pending 상태로 추가하고
  // setNo를 반환 — 호출자가 이 값으로 markLogged/markFailed.
  appendPendingSet: () => { exerciseIndex: number; setNo: number } | null;

  markSetStatus: (exerciseIndex: number, setNo: number, status: SetLogStatus) => void;
  removeSet: (exerciseIndex: number, setNo: number) => void;

  // 다음 세트 입력값 수정 (탭 편집).
  updateCurrentTargets: (exerciseIndex: number, reps: number, weightKg: number) => void;

  // 다음 운동으로 이동. 마지막이면 false 반환.
  goToNextExercise: () => boolean;

  reset: () => void;
}

// active.sets에서 운동 골격 추론 — plan 정보 없이 복구해야 할 때만 사용.
// targetSets는 알 수 없어 기록된 setNo의 최댓값으로 대체. 최소 1.
// targetReps/Weight/restSeconds는 합리적 기본값 — 사용자가 운동 중 탭으로 정정 가능.
function reconstructFromSets(sets: ActiveSetEntry[]): ExerciseProgress[] {
  const seen = new Map<
    string,
    {
      exerciseId: number;
      exerciseName: string;
      muscleGroup: MuscleGroup;
      maxSetNo: number;
      latestReps: number;
      latestWeightKg: number;
      firstAppearance: number; // 입력 순서 보존용.
    }
  >();
  let order = 0;
  for (const s of sets) {
    const existing = seen.get(s.exerciseId);
    if (existing) {
      if (s.setNo > existing.maxSetNo) {
        existing.maxSetNo = s.setNo;
        existing.latestReps = s.reps;
        existing.latestWeightKg = s.weightKg;
      }
    } else {
      seen.set(s.exerciseId, {
        exerciseId: Number.parseInt(s.exerciseId, 10),
        exerciseName: s.exerciseName,
        muscleGroup: s.muscleGroup,
        maxSetNo: s.setNo,
        latestReps: s.reps,
        latestWeightKg: s.weightKg,
        firstAppearance: order++,
      });
    }
  }

  return Array.from(seen.values())
    .sort((a, b) => a.firstAppearance - b.firstAppearance)
    .map<ExerciseProgress>((e) => ({
      exerciseId: e.exerciseId,
      exerciseName: e.exerciseName,
      muscleGroup: e.muscleGroup,
      targetSets: Math.max(1, e.maxSetNo),
      targetReps: e.latestReps,
      targetWeightKg: e.latestWeightKg,
      restSeconds: 90,
      currentReps: e.latestReps,
      currentWeightKg: e.latestWeightKg,
      completedSets: [],
    }));
}

function planExerciseToProgress(e: PlanExercise): ExerciseProgress {
  return {
    exerciseId: e.exerciseId,
    exerciseName: e.exerciseName,
    muscleGroup: e.muscleGroup,
    targetSets: e.targetSets,
    targetReps: e.targetReps,
    targetWeightKg: e.targetWeightKg,
    restSeconds: e.restSeconds,
    currentReps: e.targetReps,
    currentWeightKg: e.targetWeightKg,
    completedSets: [],
  };
}

export const useWorkoutStore = create<WorkoutState>((set, get) => ({
  session: null,
  exercises: [],
  currentExerciseIndex: 0,
  prSnapshot: [],

  start: (session, plan) => {
    set({
      session,
      exercises: plan.exercises
        .slice()
        .sort((a, b) => a.orderIndex - b.orderIndex)
        .map(planExerciseToProgress),
      currentExerciseIndex: 0,
      prSnapshot: [], // 별도 fetch 후 setPRSnapshot으로 채움.
    });
  },

  recoverFromActive: (active, plan) => {
    const planMatches = plan != null && plan.planId === active.planId;

    // 운동 골격: plan과 active.planId 일치 시 plan 사용, 아니면 sets에서 추론.
    const baseExercises: ExerciseProgress[] = planMatches
      ? plan.exercises
          .slice()
          .sort((a, b) => a.orderIndex - b.orderIndex)
          .map(planExerciseToProgress)
      : reconstructFromSets(active.sets);

    // active.sets를 exerciseId 기준으로 그룹핑 후 각 운동의 completedSets에 주입.
    const setsByExId = new Map<string, ActiveSetEntry[]>();
    for (const s of active.sets) {
      const list = setsByExId.get(s.exerciseId) ?? [];
      list.push(s);
      setsByExId.set(s.exerciseId, list);
    }

    const recoveredExercises: ExerciseProgress[] = baseExercises.map((ex) => {
      const matchingSets = (setsByExId.get(String(ex.exerciseId)) ?? [])
        .slice()
        .sort((a, b) => a.setNo - b.setNo);

      if (matchingSets.length === 0) return ex;

      const completedSets: LocalSetLog[] = matchingSets.map((s) => ({
        setNo: s.setNo,
        reps: s.reps,
        weightKg: s.weightKg,
        isSuccess: s.isSuccess,
        loggedAt: 0, // 서버 기록 — 정확한 시각 모름. 화면에서 사용 안 함.
        status: 'logged',
      }));

      // 다음 세트 입력값은 마지막 성공 세트의 reps/weight로 미리 채움.
      const last = matchingSets[matchingSets.length - 1]!;
      return {
        ...ex,
        completedSets,
        currentReps: last.reps,
        currentWeightKg: last.weightKg,
      };
    });

    // 다음 진행 운동 = 첫 미완료 운동. 모두 완료면 마지막 인덱스 유지(완료 다이얼로그로 진입).
    let nextIndex = recoveredExercises.findIndex(
      (ex) => ex.completedSets.length < ex.targetSets,
    );
    if (nextIndex === -1) nextIndex = Math.max(0, recoveredExercises.length - 1);

    set({
      session: {
        sessionId: active.sessionId,
        startedAt: active.startedAt,
        planId: active.planId,
        planName: active.planName,
      },
      exercises: recoveredExercises,
      currentExerciseIndex: nextIndex,
      prSnapshot: [],
    });
  },

  setPRSnapshot: (snapshot) => set({ prSnapshot: snapshot }),

  appendPendingSet: () => {
    const { exercises, currentExerciseIndex } = get();
    const target = exercises[currentExerciseIndex];
    if (!target) return null;

    const setNo = target.completedSets.length + 1;
    const newLog: LocalSetLog = {
      setNo,
      reps: target.currentReps,
      weightKg: target.currentWeightKg,
      isSuccess: true,
      loggedAt: Date.now(),
      status: 'pending',
    };

    const next = exercises.slice();
    next[currentExerciseIndex] = {
      ...target,
      completedSets: [...target.completedSets, newLog],
    };
    set({ exercises: next });
    return { exerciseIndex: currentExerciseIndex, setNo };
  },

  markSetStatus: (exerciseIndex, setNo, status) => {
    const { exercises } = get();
    const target = exercises[exerciseIndex];
    if (!target) return;
    const next = exercises.slice();
    next[exerciseIndex] = {
      ...target,
      completedSets: target.completedSets.map((s) =>
        s.setNo === setNo ? { ...s, status } : s,
      ),
    };
    set({ exercises: next });
  },

  removeSet: (exerciseIndex, setNo) => {
    const { exercises } = get();
    const target = exercises[exerciseIndex];
    if (!target) return;
    const next = exercises.slice();
    next[exerciseIndex] = {
      ...target,
      // setNo 재정렬 — 삭제 이후 세트 번호를 -1.
      completedSets: target.completedSets
        .filter((s) => s.setNo !== setNo)
        .map((s) => (s.setNo > setNo ? { ...s, setNo: s.setNo - 1 } : s)),
    };
    set({ exercises: next });
  },

  updateCurrentTargets: (exerciseIndex, reps, weightKg) => {
    const { exercises } = get();
    const target = exercises[exerciseIndex];
    if (!target) return;
    const next = exercises.slice();
    next[exerciseIndex] = { ...target, currentReps: reps, currentWeightKg: weightKg };
    set({ exercises: next });
  },

  goToNextExercise: () => {
    const { currentExerciseIndex, exercises } = get();
    const nextIdx = currentExerciseIndex + 1;
    if (nextIdx >= exercises.length) return false;
    set({ currentExerciseIndex: nextIdx });
    return true;
  },

  reset: () =>
    set({ session: null, exercises: [], currentExerciseIndex: 0, prSnapshot: [] }),
}));

// Selector 헬퍼 — 화면에서 자주 사용.
export function selectCurrentExercise(state: WorkoutState): ExerciseProgress | null {
  return state.exercises[state.currentExerciseIndex] ?? null;
}

export function selectTotalProgress(state: WorkoutState): {
  totalSets: number;
  doneSets: number;
} {
  let total = 0;
  let done = 0;
  for (const ex of state.exercises) {
    total += ex.targetSets;
    done += ex.completedSets.filter((s) => s.status !== 'failed').length;
  }
  return { totalSets: total, doneSets: done };
}
