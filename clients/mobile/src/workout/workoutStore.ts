import { create } from 'zustand';

import type { PlanExercise, TodayPlan } from '@/api/plan';
import type { PersonalRecord } from '@/api/analytics';
import type { ActiveExercise, ActiveSession } from '@/api/workout';

import type { ExerciseProgress, LocalSetLog, SetLogStatus } from './types';

// 운동 실행 화면 전용 로컬 상태.
// performance-goals.md: 세트 체크인 → 즉시 로컬 업데이트, API는 백그라운드.
// 실패 시 마지막 pending 항목을 rollback 또는 failed로 표시.

interface ActiveSessionMeta {
  sessionId: string;
  startedAt: string;
  // 백엔드는 String 으로 직렬화. plan.planId(number) 와 비교 시 변환 필요.
  planId: string | null;
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
  // 풀 정보 복구, 일치하지 않으면 active.exercises 에서 운동 구조를 추론.
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

// active.exercises 에서 운동 골격 추론 — plan 정보 없이 복구해야 할 때만 사용.
// targetSets 는 알 수 없어 기록된 sets 의 최대 setNo 로 대체 (최소 1).
// targetReps/Weight/restSeconds 는 합리적 기본값 — 사용자가 운동 중 탭으로 정정 가능.
function reconstructFromExercises(exercises: ActiveExercise[]): ExerciseProgress[] {
  return exercises.map<ExerciseProgress>((ex) => {
    const sortedSets = ex.sets.slice().sort((a, b) => a.setNo - b.setNo);
    const last = sortedSets[sortedSets.length - 1];
    const latestReps = last?.reps ?? 0;
    const latestWeightKg = last?.weightKg ?? 0;
    const maxSetNo = last?.setNo ?? 0;
    return {
      exerciseId: Number.parseInt(ex.exerciseId, 10),
      exerciseName: ex.exerciseName,
      muscleGroup: ex.muscleGroup,
      targetSets: Math.max(1, maxSetNo),
      targetReps: latestReps,
      targetWeightKg: latestWeightKg,
      restSeconds: 90,
      currentReps: latestReps,
      currentWeightKg: latestWeightKg,
      completedSets: [],
    };
  });
}

function planExerciseToProgress(e: PlanExercise): ExerciseProgress {
  // 맨몸 운동(BODYWEIGHT)은 targetWeightKg=null — 로컬 상태에서는 0kg 으로 처리.
  const weight = e.targetWeightKg ?? 0;
  return {
    exerciseId: e.exerciseId,
    exerciseName: e.exerciseName,
    muscleGroup: e.muscleGroup,
    targetSets: e.targetSets,
    targetReps: e.targetReps,
    targetWeightKg: weight,
    restSeconds: e.restSeconds,
    currentReps: e.targetReps,
    currentWeightKg: weight,
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
    // 백엔드 planId 는 String 직렬화. plan.planId(number) 와 비교 시 변환 필요.
    const activePlanIdNum =
      active.planId !== null ? Number.parseInt(active.planId, 10) : null;
    const planMatches =
      plan != null && activePlanIdNum !== null && plan.planId === activePlanIdNum;

    // 운동 골격: plan과 active.planId 일치 시 plan 사용, 아니면 exercises 에서 추론.
    const baseExercises: ExerciseProgress[] = planMatches
      ? plan.exercises
          .slice()
          .sort((a, b) => a.orderIndex - b.orderIndex)
          .map(planExerciseToProgress)
      : reconstructFromExercises(active.exercises);

    // active.exercises 를 exerciseId 기준으로 매핑 후 각 운동의 completedSets 에 주입.
    const exById = new Map<string, ActiveExercise>();
    for (const ex of active.exercises) exById.set(ex.exerciseId, ex);

    const recoveredExercises: ExerciseProgress[] = baseExercises.map((ex) => {
      const matched = exById.get(String(ex.exerciseId));
      const matchingSets = (matched?.sets ?? [])
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
