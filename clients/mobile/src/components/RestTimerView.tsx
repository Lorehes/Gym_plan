import { StyleSheet, Text, View } from 'react-native';

import { Button } from '@/components/Button';
import { Card } from '@/components/Card';
import { MuscleBadge } from '@/components/Badge';
import { RestTimer } from '@/components/RestTimer';
import type { SSEStatus } from '@/hooks/useTimerSSE';
import { colors, spacing, textStyles } from '@/theme';
import type { ExerciseProgress } from '@/workout/types';

// docs/design/components/timer.md
// 휴식 타이머 화면 — 카운트다운 + 다음 세트 미리 표시 + 명시적 스킵 + SSE 상태 표시.

export interface NextSetPreview {
  // 다음에 진행할 운동(같은 운동의 다음 세트일 수도, 새 운동일 수도).
  exercise: ExerciseProgress;
  setNo: number;
  isNewExercise: boolean;
  isFinalSet: boolean;
}

interface Props {
  initialSeconds: number;
  next: NextSetPreview | null;   // null이면 "운동 종료" 다음에 보일 휴식.
  sseStatus: SSEStatus;
  onComplete: () => void;
  onSkip: () => void;
}

export function RestTimerView({ initialSeconds, next, sseStatus, onComplete, onSkip }: Props) {
  return (
    <Card padding="lg" style={{ gap: spacing.lg }}>
      <View style={styles.headerRow}>
        <Text style={[textStyles.bodySm, { color: colors.textMuted, fontWeight: '600' }]}>
          휴식 중
        </Text>
        <SSEStatusBadge status={sseStatus} />
      </View>

      <RestTimer
        initialSeconds={initialSeconds}
        onComplete={onComplete}
        onSkip={onSkip}
      />

      <View style={styles.divider} />

      {next ? <NextSetPreviewBlock preview={next} /> : <FinishedHint />}

      <Button variant="secondary" size="lg" onPress={onSkip}>
        휴식 건너뛰기
      </Button>
    </Card>
  );
}

function NextSetPreviewBlock({ preview }: { preview: NextSetPreview }) {
  const { exercise, setNo, isNewExercise, isFinalSet } = preview;
  return (
    <View style={{ gap: spacing.md }}>
      <View style={styles.headerRow}>
        <Text style={[textStyles.caption, { color: colors.textMuted, letterSpacing: 0.5 }]}>
          {isNewExercise ? '다음 운동' : '다음 세트'}
        </Text>
        {isFinalSet && (
          <View style={styles.finalBadge}>
            <Text style={styles.finalBadgeText}>마지막 세트</Text>
          </View>
        )}
      </View>

      <View style={styles.headerRow}>
        <Text
          style={[textStyles.h2, { color: colors.text, flex: 1 }]}
          numberOfLines={1}
        >
          {exercise.exerciseName}
        </Text>
        <MuscleBadge group={exercise.muscleGroup} />
      </View>

      <View style={styles.numberRow}>
        <NumberStat value={String(setNo)} unit={`/ ${exercise.targetSets} 세트`} />
        <View style={styles.statSeparator} />
        <NumberStat value={String(exercise.currentReps)} unit="회" />
        <View style={styles.statSeparator} />
        <NumberStat
          value={
            Number.isInteger(exercise.currentWeightKg)
              ? String(exercise.currentWeightKg)
              : exercise.currentWeightKg.toFixed(1)
          }
          unit="kg"
        />
      </View>
    </View>
  );
}

function NumberStat({ value, unit }: { value: string; unit: string }) {
  return (
    <View style={{ alignItems: 'center', flex: 1 }}>
      <Text style={styles.statNumber}>{value}</Text>
      <Text style={styles.statUnit}>{unit}</Text>
    </View>
  );
}

function FinishedHint() {
  return (
    <View style={{ gap: spacing.xs, alignItems: 'center' }}>
      <Text style={{ fontSize: 32 }}>🏁</Text>
      <Text style={[textStyles.h3, { color: colors.text }]}>마지막 휴식</Text>
      <Text style={[textStyles.body, { color: colors.textMuted, textAlign: 'center' }]}>
        타이머가 끝나면 운동을 마무리해주세요.
      </Text>
    </View>
  );
}

function SSEStatusBadge({ status }: { status: SSEStatus }) {
  // SSE 연결 끊김 시 "로컬 모드"로 표기 — 사용자에게 fallback이 활성임을 알림.
  if (status === 'connected') {
    return (
      <View style={[styles.statusBadge, { backgroundColor: 'rgba(74,222,128,0.15)' }]}>
        <View style={[styles.statusDot, { backgroundColor: colors.success }]} />
        <Text style={[styles.statusText, { color: colors.success }]}>실시간 동기화</Text>
      </View>
    );
  }
  if (status === 'connecting') {
    return (
      <View style={[styles.statusBadge, { backgroundColor: 'rgba(252,211,77,0.15)' }]}>
        <View style={[styles.statusDot, { backgroundColor: colors.warning }]} />
        <Text style={[styles.statusText, { color: colors.warning }]}>연결 중…</Text>
      </View>
    );
  }
  // disconnected | disabled — 로컬 모드.
  return (
    <View style={[styles.statusBadge, { backgroundColor: colors.elevated }]}>
      <View style={[styles.statusDot, { backgroundColor: colors.textMuted }]} />
      <Text style={[styles.statusText, { color: colors.textMuted }]}>로컬 모드</Text>
    </View>
  );
}

const styles = StyleSheet.create({
  headerRow: {
    flexDirection: 'row',
    alignItems: 'center',
    justifyContent: 'space-between',
    gap: spacing.sm,
  },
  divider: {
    height: 1,
    backgroundColor: colors.borderSubtle,
  },
  numberRow: {
    flexDirection: 'row',
    alignItems: 'center',
  },
  statSeparator: {
    width: 1,
    height: 32,
    backgroundColor: colors.borderSubtle,
  },
  statNumber: {
    fontSize: 32,
    fontWeight: '700',
    color: colors.text,
    letterSpacing: -0.5,
    fontVariant: ['tabular-nums'],
  },
  statUnit: {
    fontSize: 12,
    color: colors.textMuted,
    marginTop: 2,
  },
  finalBadge: {
    paddingHorizontal: 8,
    paddingVertical: 4,
    borderRadius: 999,
    backgroundColor: 'rgba(248,113,113,0.15)',
  },
  finalBadgeText: {
    fontSize: 11,
    fontWeight: '700',
    color: colors.error,
    letterSpacing: 0.5,
  },
  statusBadge: {
    flexDirection: 'row',
    alignItems: 'center',
    gap: 6,
    paddingHorizontal: 10,
    paddingVertical: 4,
    borderRadius: 999,
  },
  statusDot: {
    width: 8,
    height: 8,
    borderRadius: 4,
  },
  statusText: {
    fontSize: 11,
    fontWeight: '600',
    letterSpacing: 0.3,
  },
});
