import { useEffect, useRef } from 'react';
import { Alert } from 'react-native';

import { fetchPersonalRecords } from '@/api/analytics';
import { useActiveSession } from '@/queries/useActiveSession';
import { usePlanToday } from '@/queries/usePlanToday';
import { useWorkoutStore } from '@/workout/workoutStore';

// 앱 진입(인증 직후) 시 진행 중 세션을 workoutStore에 복구.
// 동작 조건:
//  1) /sessions/active 응답이 활성 세션을 가짐
//  2) 로컬 store는 비어있음 (이미 시작된 세션이 있으면 덮어쓰지 않음)
//  3) /plans/today 결과가 도착했거나 활성 세션의 planId가 없음(자유 운동)
// MainTabs에서 호출 — 인증된 트리에 들어와야 동작.
export function useSessionRecovery() {
  const { data: active, isPending: activePending } = useActiveSession();
  const { data: plan, isPending: planPending } = usePlanToday();

  const hasLocalSession = useWorkoutStore((s) => s.session !== null);
  const recoverFromActive = useWorkoutStore((s) => s.recoverFromActive);
  const setPRSnapshot = useWorkoutStore((s) => s.setPRSnapshot);

  // 한 sessionId에 대해 복구는 1회만.
  const recoveredFor = useRef<string | null>(null);

  useEffect(() => {
    if (activePending) return;
    if (!active) return; // 진행 중 세션 없음.
    if (hasLocalSession) return; // 사용자가 이미 시작/복구 후 진행 중.
    if (recoveredFor.current === active.sessionId) return;

    // plan이 필요한 케이스: active.planId가 있을 때.
    if (active.planId !== null) {
      if (planPending) return; // 일치 판정 위해 plan 도착까지 대기.
    }

    // 백엔드 planId 는 String 직렬화 — plan.planId(number) 와 비교하려면 변환.
    const activePlanIdNum =
      active.planId !== null ? Number.parseInt(active.planId, 10) : null;
    const planMatches =
      activePlanIdNum !== null && plan != null && plan.planId === activePlanIdNum;

    // plan이 일치하면 그대로, 아니면 sets에서 추론.
    recoverFromActive(active, planMatches ? plan : null);
    recoveredFor.current = active.sessionId;

    // PR 스냅샷도 함께 — 신기록 비교는 복구된 세션의 종료 시점에 사용.
    fetchPersonalRecords()
      .then(setPRSnapshot)
      .catch(() => {
        // 무시 — 신기록 강조만 비활성, 운동 흐름엔 영향 없음.
      });

    Alert.alert(
      '진행 중인 세션을 복구했어요',
      planMatches
        ? '이전 세션의 기록을 그대로 이어서 진행할 수 있어요.'
        : '오늘 루틴과 다른 세션이라 자유 운동 모드로 이어서 진행합니다.',
    );
  }, [active, activePending, plan, planPending, hasLocalSession, recoverFromActive, setPRSnapshot]);
}
