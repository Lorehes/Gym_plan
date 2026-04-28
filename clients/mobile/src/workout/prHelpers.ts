import { estimate1RM, type PersonalRecord } from '@/api/analytics';

import type { ExerciseProgress, LocalSetLog } from './types';

export interface NewPRRecord {
  exerciseId: number;
  exerciseName: string;
  // 갱신된 항목 — 동시에 둘 다일 수도 있음.
  maxWeightDelta?: { previous: number; next: number };
  est1RMDelta?: { previous: number; next: number };
  // 신기록 세트의 무게/횟수 (대표값).
  achievedWeightKg: number;
  achievedReps: number;
}

// 세션에서 갱신된 PR 후보를 추출. 비교 기준은 세션 시작 시점 스냅샷.
// failed 세트는 제외 (서버에 도달하지 못했을 가능성).
export function computeNewPRs(
  exercises: ExerciseProgress[],
  snapshot: PersonalRecord[],
): NewPRRecord[] {
  const snapshotByExId = new Map<string, PersonalRecord>();
  for (const pr of snapshot) snapshotByExId.set(pr.exerciseId, pr);

  const results: NewPRRecord[] = [];

  for (const ex of exercises) {
    const successfulSets = ex.completedSets.filter(isSuccessfulSet);
    if (successfulSets.length === 0) continue;

    const localBest = bestSet(successfulSets);
    if (!localBest) continue;

    const prev = snapshotByExId.get(String(ex.exerciseId));
    const localWeight = localBest.weightKg;
    const local1RM = estimate1RM(localBest.weightKg, localBest.reps);

    const prevWeight = prev?.maxWeightKg ?? 0;
    const prev1RM = prev?.estimated1RM ?? 0;

    const weightImproved = localWeight > prevWeight + 1e-6;
    const oneRmImproved = local1RM > prev1RM + 1e-6;
    if (!weightImproved && !oneRmImproved) continue;

    results.push({
      exerciseId: ex.exerciseId,
      exerciseName: ex.exerciseName,
      maxWeightDelta: weightImproved ? { previous: prevWeight, next: localWeight } : undefined,
      est1RMDelta: oneRmImproved ? { previous: prev1RM, next: local1RM } : undefined,
      achievedWeightKg: localBest.weightKg,
      achievedReps: localBest.reps,
    });
  }

  return results;
}

function isSuccessfulSet(s: LocalSetLog): boolean {
  return s.status !== 'failed' && s.isSuccess;
}

// "최고 세트" 선정: 최대 무게 우선, 동일 무게면 최대 횟수.
function bestSet(sets: LocalSetLog[]): LocalSetLog | null {
  if (sets.length === 0) return null;
  return sets.reduce((best, cur) => {
    if (cur.weightKg > best.weightKg) return cur;
    if (cur.weightKg === best.weightKg && cur.reps > best.reps) return cur;
    return best;
  }, sets[0]!);
}
