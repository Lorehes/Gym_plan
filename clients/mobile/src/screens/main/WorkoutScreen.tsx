import { useCallback, useState } from 'react';
import {
  ActivityIndicator,
  Alert,
  Pressable,
  ScrollView,
  StyleSheet,
  Text,
  View,
} from 'react-native';
import { useSafeAreaInsets } from 'react-native-safe-area-context';
import { useMutation } from '@tanstack/react-query';
import { useKeepAwake } from 'expo-keep-awake';

import {
  cancelSession,
  completeSession,
  deleteSet,
  startSession,
  type SessionComplete,
} from '@/api/workout';
import { fetchPersonalRecords } from '@/api/analytics';
import { ApiException } from '@/api/types';
import type { TodayPlan } from '@/api/plan';
import { Badge, MuscleBadge } from '@/components/Badge';
import { Button } from '@/components/Button';
import { Card } from '@/components/Card';
import { NumberCell } from '@/components/NumberCell';
import { OfflineBanner } from '@/components/OfflineBanner';
import { RestTimerView, type NextSetPreview } from '@/components/RestTimerView';
import { SetCheckButton } from '@/components/SetCheckButton';
import { SetIndicator } from '@/components/SetIndicator';
import { SyncStatusBanner } from '@/components/SyncStatusBanner';
import { WorkoutSummary } from '@/components/WorkoutSummary';
import { useTimerSSE } from '@/hooks/useTimerSSE';
import { usePlanToday } from '@/queries/usePlanToday';
import { colors, spacing, textStyles } from '@/theme';
import { computeNewPRs, type NewPRRecord } from '@/workout/prHelpers';
import { kickDrain, useSyncQueue } from '@/workout/syncQueue';
import { useSyncWorker } from '@/workout/syncWorker';
import {
  selectCurrentExercise,
  selectTotalProgress,
  useWorkoutStore,
} from '@/workout/workoutStore';
import type { ExerciseProgress } from '@/workout/types';
import type { MainTabScreenProps } from '@/navigation/types';

// 세트 체크 버튼 영역 — ScrollView 하단 패딩 = 버튼 높이 + safe area.
const CHECK_BUTTON_HEIGHT = 80;

export function WorkoutScreen({ navigation }: MainTabScreenProps<'Workout'>) {
  // performance-goals.md 기반: 운동 중 화면 꺼짐 방지.
  useKeepAwake();
  // 백그라운드 sync worker — 온라인 복귀 시 자동 drain + workoutStore 동기화.
  useSyncWorker();
  const insets = useSafeAreaInsets();

  const { data: plan, isLoading: planLoading } = usePlanToday();
  const session = useWorkoutStore((s) => s.session);
  const start = useWorkoutStore((s) => s.start);
  const setPRSnapshot = useWorkoutStore((s) => s.setPRSnapshot);
  const reset = useWorkoutStore((s) => s.reset);

  // 운동 종료 결과 — 표시 후 reset.
  const [completed, setCompleted] = useState<{
    result: SessionComplete;
    newPRs: NewPRRecord[];
  } | null>(null);

  const startMutation = useMutation({
    mutationFn: () =>
      startSession(plan ? { planId: plan.planId, planName: plan.name } : {}),
    onSuccess: async (data) => {
      if (!plan) return;
      start(
        {
          sessionId: data.sessionId,
          startedAt: data.startedAt,
          // 백엔드 SessionDetailResponse.planId 와 동일하게 String 으로 보존.
          planId: String(plan.planId),
          planName: plan.name,
        },
        plan,
      );
      // PR 스냅샷 — 세션 시작 시점 기준. 실패해도 운동 진행에 영향 없음.
      try {
        const prs = await fetchPersonalRecords();
        setPRSnapshot(prs);
      } catch {
        // 무시 — 신기록 비교만 비활성. 사용자에겐 영향 없음.
      }
    },
    onError: (error) => {
      const msg =
        error instanceof ApiException && error.code === 'SESSION_ALREADY_ACTIVE'
          ? '이미 진행 중인 세션이 있어요. 앱을 다시 시작해주세요.'
          : '세션을 시작하지 못했습니다.';
      Alert.alert('운동 시작 실패', msg);
    },
  });

  // 세션 종료 후 요약 화면 — 다른 모든 분기보다 우선.
  if (completed) {
    return (
      <WorkoutSummary
        result={completed.result}
        newPRs={completed.newPRs}
        onClose={() => setCompleted(null)}
      />
    );
  }

  if (planLoading && !plan) {
    return (
      <View style={[styles.center, { backgroundColor: colors.background }]}>
        <ActivityIndicator color={colors.accent} size="large" />
      </View>
    );
  }

  if (!plan) {
    return (
      <ScrollView
        style={{ flex: 1, backgroundColor: colors.background }}
        contentContainerStyle={[styles.padded, { paddingTop: insets.top + spacing.lg }]}
      >
        <OfflineBanner />
        <Text style={[textStyles.h1, { color: colors.text }]}>운동</Text>
        <Card variant="outline" padding="lg" style={{ alignItems: 'center', gap: spacing.sm }}>
          <Text style={{ fontSize: 48 }}>🛌</Text>
          <Text style={[textStyles.h3, { color: colors.text }]}>오늘은 쉬는 날입니다</Text>
          <Text style={[textStyles.body, { color: colors.textMuted, textAlign: 'center' }]}>
            루틴이 배정되지 않은 날에는 자유 운동 세션 기능을 추후 제공할 예정입니다.
          </Text>
        </Card>
      </ScrollView>
    );
  }

  // 세션 미시작 — 시작 버튼만 노출.
  if (!session) {
    return (
      <ScrollView
        style={{ flex: 1, backgroundColor: colors.background }}
        contentContainerStyle={[
          styles.padded,
          { paddingTop: insets.top + spacing.lg, paddingBottom: insets.bottom + spacing['2xl'] },
        ]}
      >
        <OfflineBanner />
        <View style={{ gap: spacing.xs }}>
          <Text style={[textStyles.bodySm, { color: colors.textMuted }]}>오늘의 루틴</Text>
          <Text style={[textStyles.h1, { color: colors.text }]}>{plan.name}</Text>
        </View>

        <Card padding="lg" style={{ gap: spacing.sm }}>
          <Text style={[textStyles.h3, { color: colors.text }]}>준비 운동 잊지 마세요</Text>
          <Text style={[textStyles.body, { color: colors.textMuted }]}>
            세션을 시작하면 첫 번째 운동인 “{plan.exercises[0]?.exerciseName}”부터 진행합니다.
          </Text>
        </Card>

        <View style={{ flex: 1, minHeight: spacing['3xl'] }} />

        <Button
          variant="accent"
          size="full"
          loading={startMutation.isPending}
          onPress={() => startMutation.mutate()}
        >
          세션 시작
        </Button>
      </ScrollView>
    );
  }

  return (
    <ActiveSession
      plan={plan}
      onCancel={reset}
      onCompleted={(result, newPRs) => setCompleted({ result, newPRs })}
      onAbort={() => {
        reset();
        navigation.navigate('Home');
      }}
    />
  );
}

interface ActiveSessionProps {
  plan: TodayPlan;
  onCancel: () => void;
  onCompleted: (result: SessionComplete, newPRs: NewPRRecord[]) => void;
  // 세션 중도 포기 — 서버 처리 후 호출. 로컬 정리 + Home 이동.
  onAbort: () => void;
}

function ActiveSession({ plan, onCancel, onCompleted, onAbort }: ActiveSessionProps) {
  const insets = useSafeAreaInsets();
  const sessionId = useWorkoutStore((s) => s.session?.sessionId ?? null);
  const exercise = useWorkoutStore(selectCurrentExercise);
  const progress = useWorkoutStore(selectTotalProgress);
  const exerciseIndex = useWorkoutStore((s) => s.currentExerciseIndex);
  const exerciseCount = useWorkoutStore((s) => s.exercises.length);
  const allExercises = useWorkoutStore((s) => s.exercises);

  const appendPendingSet = useWorkoutStore((s) => s.appendPendingSet);
  const removeSet = useWorkoutStore((s) => s.removeSet);
  const updateCurrentTargets = useWorkoutStore((s) => s.updateCurrentTargets);
  const goToNextExercise = useWorkoutStore((s) => s.goToNextExercise);
  const reset = useWorkoutStore((s) => s.reset);

  // 휴식 중: 어떤 세트의 휴식 중인지. null이면 입력 모드.
  const [restingSetNo, setRestingSetNo] = useState<number | null>(null);
  // 운동 완료 처리 후 마지막 다이얼로그.
  const [completing, setCompleting] = useState(false);

  // 세트 로깅 — 직접 mutation 대신 syncQueue로 enqueue. drain worker가
  // 성공 시 markSetStatus(logged), 3회 실패 시 markSetStatus(failed) 자동 처리.
  const enqueueSync = useSyncQueue((s) => s.enqueue);

  const completeMutation = useMutation({
    mutationFn: () => completeSession(sessionId!),
    onSuccess: (data) => {
      // 종료 시점의 store 스냅샷으로 PR 비교.
      const snap = useWorkoutStore.getState();
      const newPRs = computeNewPRs(snap.exercises, snap.prSnapshot);
      reset();
      onCompleted(data, newPRs);
    },
    onError: () => {
      Alert.alert('완료 처리 실패', '네트워크 상태를 확인하고 다시 시도해주세요.');
      setCompleting(false);
    },
  });

  const clearForSession = useSyncQueue((s) => s.clearForSession);

  // 세션 중도 포기 — Alert로 한 번 더 확인 후 서버 cancel + 로컬 정리.
  const cancelMutation = useMutation({
    mutationFn: () => cancelSession(sessionId!),
    onSuccess: () => {
      clearForSession(sessionId!);
      onAbort();
    },
    onError: (error) => {
      // 백엔드가 아직 /cancel 엔드포인트를 구현하지 않은 경우 — 로컬은 정리하되 경고.
      // 추후 useSessionRecovery가 다음 진입 시 zombie 세션을 다시 가져올 수 있음.
      if (error instanceof ApiException && (error.status === 404 || error.status === 405)) {
        clearForSession(sessionId!);
        onAbort();
        Alert.alert(
          '세션 정리 완료',
          '서버 측 정리는 보류됐습니다. 다음 접속 시 자동 복구될 수 있어요.',
        );
        return;
      }
      Alert.alert(
        '취소 실패',
        '네트워크 또는 서버 오류로 세션을 취소하지 못했어요. 다시 시도해주세요.',
      );
    },
  });

  const promptCancel = () => {
    Alert.alert(
      '세션을 포기하시겠어요?',
      '지금까지의 기록은 보관되지만 운동 통계로는 집계되지 않아요.',
      [
        { text: '계속 운동', style: 'cancel' },
        {
          text: '포기',
          style: 'destructive',
          onPress: () => cancelMutation.mutate(),
        },
      ],
    );
  };

  // SSE — 보조 채널. 포그라운드에서는 로컬 타이머가 기준이라 별다른 동작 없음.
  // 끊김 시 RestTimerView가 "로컬 모드" 배지 표시 — 추가 fallback 처리는 불필요.
  const { status: sseStatus } = useTimerSSE({
    sessionId,
    enabled: !!sessionId,
  });

  const handleCheck = useCallback(() => {
    if (!exercise || !sessionId) return;

    const result = appendPendingSet();
    if (!result) return;

    // 즉시 휴식 타이머 시작 (마지막 세트가 아니어도 일단 표시) — 리듬 유지.
    setRestingSetNo(result.setNo);

    // syncQueue로 enqueue + 즉시 drain 시도. 오프라인이면 큐에 남아 복구 시 자동 동기화.
    enqueueSync({
      sessionId,
      exerciseLocalIndex: result.exerciseIndex,
      setNo: result.setNo,
      payload: {
        exerciseId: String(exercise.exerciseId),
        exerciseName: exercise.exerciseName,
        muscleGroup: exercise.muscleGroup,
        setNo: result.setNo,
        reps: exercise.currentReps,
        weightKg: exercise.currentWeightKg,
        isSuccess: true,
      },
    });
    kickDrain();
  }, [exercise, sessionId, appendPendingSet, enqueueSync]);

  // 길게 눌러 직전 세트 취소 (실수 정정).
  const handleUncheck = useCallback(() => {
    if (!exercise || restingSetNo == null || !sessionId) return;
    const setNo = restingSetNo;
    setRestingSetNo(null);

    // 로컬 즉시 제거.
    removeSet(exerciseIndex, setNo);

    // 서버에서도 제거 시도 — 아직 POST가 미완료여도 mutate는 무관.
    deleteSet(sessionId, setNo, String(exercise.exerciseId)).catch(() => {
      // 보조 처리: 실패해도 로컬은 이미 정정. 다음 동기화 시점에 reconcile.
    });
  }, [exercise, restingSetNo, sessionId, exerciseIndex, removeSet]);

  // 휴식 타이머 종료 — 다음 세트로 진행.
  const handleTimerComplete = useCallback(() => {
    if (!exercise) return;
    setRestingSetNo(null);

    // 현재 운동의 세트가 모두 완료되면 자동으로 다음 운동.
    const completedCount = exercise.completedSets.filter((s) => s.status !== 'failed').length;
    if (completedCount >= exercise.targetSets) {
      const advanced = goToNextExercise();
      if (!advanced) {
        // 모든 운동 완료 → 완료 다이얼로그 노출.
        setCompleting(true);
      }
    }
  }, [exercise, goToNextExercise]);

  if (!exercise) {
    return (
      <View style={[styles.center, { backgroundColor: colors.background }]}>
        <ActivityIndicator color={colors.accent} />
      </View>
    );
  }

  const completedActiveCount = exercise.completedSets.filter((s) => s.status !== 'failed').length;
  const isLastSet = completedActiveCount + 1 >= exercise.targetSets;
  const isLastExercise = exerciseIndex >= exerciseCount - 1;
  const allDone = completing || (isLastExercise && completedActiveCount >= exercise.targetSets);

  const buttonStatus = restingSetNo != null ? 'done' : 'idle';
  const isResting = restingSetNo != null;

  return (
    <View style={{ flex: 1, backgroundColor: colors.background }}>
      <ScrollView
        contentContainerStyle={[
          styles.padded,
          {
            paddingTop: insets.top + spacing.lg,
            paddingBottom: CHECK_BUTTON_HEIGHT + insets.bottom + spacing.xl,
          },
        ]}
      >
        <OfflineBanner />
        <SyncStatusBanner />

        <View style={{ gap: spacing.xs }}>
          <View style={styles.row}>
            <Text style={[textStyles.bodySm, { color: colors.textMuted, flex: 1 }]}>
              {exerciseIndex + 1} / {exerciseCount} · {plan.name}
            </Text>
            <Pressable
              onPress={promptCancel}
              disabled={cancelMutation.isPending}
              accessibilityRole="button"
              accessibilityLabel="세션 포기"
              hitSlop={10}
              style={({ pressed }) => [
                styles.cancelBtn,
                pressed && { opacity: 0.6 },
                cancelMutation.isPending && { opacity: 0.4 },
              ]}
            >
              {cancelMutation.isPending ? (
                <ActivityIndicator size="small" color={colors.error} />
              ) : (
                <Text style={styles.cancelBtnText}>포기</Text>
              )}
            </Pressable>
          </View>
          <View style={styles.row}>
            <Text
              style={[textStyles.h1, { color: colors.text, flex: 1 }]}
              numberOfLines={1}
            >
              {exercise.exerciseName}
            </Text>
            <MuscleBadge group={exercise.muscleGroup} />
          </View>
        </View>

        <ProgressBar done={progress.doneSets} total={progress.totalSets} />

        <Card padding="lg" style={{ gap: spacing.lg }}>
          <View style={styles.indicatorRow}>
            <SetIndicator
              total={exercise.targetSets}
              completedSets={exercise.completedSets}
              currentIndex={exercise.completedSets.length}
            />
            <Badge tone="neutral">
              {`${completedActiveCount}/${exercise.targetSets} 세트`}
            </Badge>
          </View>

          <View style={styles.cellRow}>
            <NumberCell
              label="무게"
              unit="kg"
              value={exercise.currentWeightKg}
              decimal
              step={2.5}
              disabled={isResting}
              onCommit={(v) => updateCurrentTargets(exerciseIndex, exercise.currentReps, v)}
            />
            <NumberCell
              label="횟수"
              unit="회"
              value={exercise.currentReps}
              step={1}
              disabled={isResting}
              onCommit={(v) => updateCurrentTargets(exerciseIndex, v, exercise.currentWeightKg)}
            />
          </View>

          <Text style={[textStyles.caption, { color: colors.textMuted, textAlign: 'center' }]}>
            목표 {exercise.targetSets}세트 · {exercise.targetReps}회 · {exercise.targetWeightKg}kg ·
            휴식 {exercise.restSeconds}s
          </Text>
        </Card>

        {isResting && (
          <RestTimerView
            key={restingSetNo}
            initialSeconds={exercise.restSeconds}
            next={computeNextPreview(
              exercise,
              completedActiveCount,
              isLastExercise,
              exerciseIndex,
              allExercises,
            )}
            sseStatus={sseStatus}
            onComplete={handleTimerComplete}
            onSkip={handleTimerComplete}
          />
        )}

        {allDone && (
          <Card padding="lg" style={{ gap: spacing.md, alignItems: 'center' }}>
            <Text style={{ fontSize: 48 }}>🏁</Text>
            <Text style={[textStyles.h2, { color: colors.text }]}>모든 세트 완료!</Text>
            <Text style={[textStyles.body, { color: colors.textMuted, textAlign: 'center' }]}>
              세션을 마무리하면 운동 기록이 저장돼요.
            </Text>
            <Button
              variant="accent"
              size="full"
              loading={completeMutation.isPending}
              onPress={() => completeMutation.mutate()}
            >
              운동 종료
            </Button>
            <Button variant="ghost" size="md" onPress={onCancel}>
              그냥 닫기
            </Button>
          </Card>
        )}
      </ScrollView>

      {!allDone && (
        <SetCheckButton
          status={buttonStatus}
          isLastSet={isLastSet}
          isLastExercise={isLastExercise}
          onCheck={handleCheck}
          onUncheck={handleUncheck}
        />
      )}
    </View>
  );
}

// 휴식 종료 후 진행할 세트 계산.
// 같은 운동의 다음 세트가 남았으면 그 세트, 없으면 다음 운동의 1세트, 그것도 없으면 null(운동 종료).
function computeNextPreview(
  current: ExerciseProgress,
  completedActiveCount: number,
  isLastExercise: boolean,
  currentIndex: number,
  allExercises: ExerciseProgress[],
): NextSetPreview | null {
  const nextSetNo = completedActiveCount + 1;
  if (nextSetNo <= current.targetSets) {
    return {
      exercise: current,
      setNo: nextSetNo,
      isNewExercise: false,
      isFinalSet: nextSetNo === current.targetSets,
    };
  }
  if (isLastExercise) return null;
  const nextEx = allExercises[currentIndex + 1];
  if (!nextEx) return null;
  return {
    exercise: nextEx,
    setNo: 1,
    isNewExercise: true,
    isFinalSet: nextEx.targetSets === 1,
  };
}

interface ProgressBarProps {
  done: number;
  total: number;
}

function ProgressBar({ done, total }: ProgressBarProps) {
  const pct = total > 0 ? Math.min(1, done / total) : 0;
  return (
    <View style={styles.progressContainer}>
      <View style={styles.progressBg}>
        <View style={[styles.progressFill, { width: `${pct * 100}%` }]} />
      </View>
      <Text style={[textStyles.caption, { color: colors.textMuted }]}>
        전체 진행 {done}/{total}
      </Text>
    </View>
  );
}

const styles = StyleSheet.create({
  padded: {
    paddingHorizontal: spacing.lg,
    gap: spacing.lg,
  },
  row: {
    flexDirection: 'row',
    alignItems: 'center',
    gap: spacing.md,
  },
  center: {
    flex: 1,
    alignItems: 'center',
    justifyContent: 'center',
  },
  indicatorRow: {
    flexDirection: 'row',
    alignItems: 'center',
    justifyContent: 'space-between',
    gap: spacing.md,
  },
  cellRow: {
    flexDirection: 'row',
    justifyContent: 'space-around',
    gap: spacing.md,
  },
  progressContainer: {
    gap: 4,
  },
  progressBg: {
    height: 6,
    backgroundColor: colors.elevated,
    borderRadius: 3,
    overflow: 'hidden',
  },
  progressFill: {
    height: 6,
    backgroundColor: colors.accent,
    borderRadius: 3,
  },
  cancelBtn: {
    paddingHorizontal: 10,
    paddingVertical: 4,
    borderRadius: 999,
    borderWidth: 1,
    borderColor: 'rgba(248,113,113,0.4)',
    minHeight: 28,
    minWidth: 48,
    alignItems: 'center',
    justifyContent: 'center',
  },
  cancelBtnText: {
    fontSize: 12,
    fontWeight: '600',
    color: colors.error,
    letterSpacing: 0.3,
  },
});
