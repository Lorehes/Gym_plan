import {
  ActivityIndicator,
  RefreshControl,
  ScrollView,
  StyleSheet,
  Text,
  View,
} from 'react-native';
import { useSafeAreaInsets } from 'react-native-safe-area-context';

import { useAuthStore } from '@/auth/authStore';
import { Badge, MuscleBadge, type MuscleGroup } from '@/components/Badge';
import { Button } from '@/components/Button';
import { Card } from '@/components/Card';
import { OfflineBanner } from '@/components/OfflineBanner';
import { useOnlineStatus } from '@/hooks/useOnlineStatus';
import type { MainTabScreenProps } from '@/navigation/types';
import { usePlanToday } from '@/queries/usePlanToday';
import type { TodayPlan, PlanExercise } from '@/api/plan';
import { colors, radius, spacing, textStyles } from '@/theme';

const WEEKDAY_LABEL = ['', '월', '화', '수', '목', '금', '토', '일'] as const;

function todayLabel(): { dayOfWeek: number; label: string } {
  // ISO 8601: 1=Mon ... 7=Sun (서버와 동일)
  const jsDay = new Date().getDay(); // 0=Sun..6=Sat
  const isoDay = jsDay === 0 ? 7 : jsDay;
  return { dayOfWeek: isoDay, label: WEEKDAY_LABEL[isoDay] ?? '' };
}

function uniqueMuscleGroups(exercises: PlanExercise[]): MuscleGroup[] {
  const seen = new Set<MuscleGroup>();
  const out: MuscleGroup[] = [];
  for (const e of exercises) {
    if (!seen.has(e.muscleGroup)) {
      seen.add(e.muscleGroup);
      out.push(e.muscleGroup);
    }
  }
  return out;
}

function estimateDurationMin(exercises: PlanExercise[]): number {
  // 거친 추정: 세트당 (3분 작업) + 휴식. 화면 표시용.
  const seconds = exercises.reduce((sum, e) => {
    const setSeconds = e.targetSets * (3 * 60 + e.restSeconds);
    return sum + setSeconds;
  }, 0);
  return Math.max(1, Math.round(seconds / 60));
}

export function HomeScreen({ navigation }: MainTabScreenProps<'Home'>) {
  const insets = useSafeAreaInsets();
  const user = useAuthStore((s) => s.user);
  const { isOnline } = useOnlineStatus();
  const query = usePlanToday();
  const today = todayLabel();

  // 캐시는 즉시 표시 — `data`가 있으면 무조건 우선. `isFetching`은 보조 인디케이터.
  const plan = query.data;
  const showInitialLoading = query.isLoading && plan === undefined;
  const showError = query.isError && plan === undefined;

  return (
    <ScrollView
      style={{ flex: 1, backgroundColor: colors.background }}
      contentContainerStyle={{
        padding: spacing.lg,
        paddingBottom: spacing['2xl'] + insets.bottom,
        gap: spacing.lg,
      }}
      refreshControl={
        <RefreshControl
          refreshing={query.isFetching && !showInitialLoading}
          onRefresh={() => void query.refetch()}
          tintColor={colors.accent}
          enabled={isOnline}
        />
      }
    >
      <OfflineBanner />

      <View style={{ gap: spacing.xs }}>
        <Text style={{ ...textStyles.bodySm, color: colors.textMuted }}>
          {today.label}요일 · 안녕하세요{user ? `, ${user.nickname}님` : ''}
        </Text>
        <Text style={{ ...textStyles.h1, color: colors.text }}>오늘의 루틴</Text>
      </View>

      {showInitialLoading ? (
        <RoutineSkeleton />
      ) : showError ? (
        <ErrorState onRetry={() => void query.refetch()} />
      ) : plan === null ? (
        <RestDayEmptyState />
      ) : plan ? (
        <>
          <RoutineCard plan={plan} dayLabel={today.label} />
          <BackgroundRefreshHint show={query.isFetching} />
        </>
      ) : null}

      <View style={{ flex: 1 }} />

      {plan && (
        <Button
          variant="accent"
          size="full"
          onPress={() => navigation.navigate('Workout')}
        >
          운동 시작
        </Button>
      )}
    </ScrollView>
  );
}

interface RoutineCardProps {
  plan: TodayPlan;
  dayLabel: string;
}

function RoutineCard({ plan, dayLabel }: RoutineCardProps) {
  const muscleGroups = uniqueMuscleGroups(plan.exercises);
  const durationMin = estimateDurationMin(plan.exercises);

  return (
    <Card variant="default" padding="lg" style={{ gap: spacing.md }}>
      <View style={styles.cardHeader}>
        <Text style={{ ...textStyles.h2, color: colors.text, flexShrink: 1 }} numberOfLines={2}>
          {plan.name}
        </Text>
        <Badge tone="primary">{`${dayLabel}요일`}</Badge>
      </View>

      {muscleGroups.length > 0 && (
        <View style={styles.badgeRow}>
          {muscleGroups.map((g) => (
            <MuscleBadge key={g} group={g} />
          ))}
        </View>
      )}

      <View style={styles.divider} />

      <View style={{ gap: spacing.sm }}>
        {plan.exercises.slice(0, 4).map((e) => (
          <ExerciseRow key={e.id} exercise={e} />
        ))}
        {plan.exercises.length > 4 && (
          <Text style={{ ...textStyles.bodySm, color: colors.textMuted }}>
            외 {plan.exercises.length - 4}개
          </Text>
        )}
      </View>

      <View style={styles.divider} />

      <View style={styles.metaRow}>
        <MetaItem label="운동" value={`${plan.exercises.length}개`} />
        <MetaItem label="예상 시간" value={`약 ${durationMin}분`} />
      </View>
    </Card>
  );
}

function ExerciseRow({ exercise }: { exercise: PlanExercise }) {
  // 맨몸 운동(BODYWEIGHT)은 targetWeightKg=null — "맨몸"으로 표기.
  const weightLabel = exercise.targetWeightKg == null ? '맨몸' : `${exercise.targetWeightKg}kg`;
  return (
    <View style={styles.exerciseRow}>
      <Text style={{ ...textStyles.body, color: colors.text, flex: 1 }} numberOfLines={1}>
        {exercise.exerciseName}
      </Text>
      <Text style={{ ...textStyles.bodySm, color: colors.textMuted }}>
        {exercise.targetSets}×{exercise.targetReps} · {weightLabel}
      </Text>
    </View>
  );
}

function MetaItem({ label, value }: { label: string; value: string }) {
  return (
    <View style={{ flex: 1 }}>
      <Text style={{ ...textStyles.caption, color: colors.textMuted }}>{label}</Text>
      <Text style={{ ...textStyles.bodyLg, color: colors.text, fontWeight: '600' }}>{value}</Text>
    </View>
  );
}

function RestDayEmptyState() {
  return (
    <Card variant="outline" padding="lg" style={{ gap: spacing.sm, alignItems: 'center' }}>
      <Text style={{ fontSize: 48 }}>🛌</Text>
      <Text style={{ ...textStyles.h3, color: colors.text }}>오늘은 쉬는 날입니다</Text>
      <Text style={{ ...textStyles.body, color: colors.textMuted, textAlign: 'center' }}>
        오늘 요일에 배정된 루틴이 없어요. {'\n'}회복도 훈련의 일부입니다.
      </Text>
    </Card>
  );
}

function ErrorState({ onRetry }: { onRetry: () => void }) {
  return (
    <Card variant="default" padding="lg" style={{ gap: spacing.md, alignItems: 'center' }}>
      <Text style={{ ...textStyles.h3, color: colors.text }}>루틴을 불러오지 못했습니다</Text>
      <Text style={{ ...textStyles.body, color: colors.textMuted, textAlign: 'center' }}>
        네트워크를 확인하고 다시 시도해주세요.
      </Text>
      <Button variant="secondary" size="md" onPress={onRetry}>
        다시 시도
      </Button>
    </Card>
  );
}

function RoutineSkeleton() {
  return (
    <Card variant="default" padding="lg" style={{ gap: spacing.md, minHeight: 240 }}>
      <View style={[styles.skeletonLine, { width: '60%', height: 24 }]} />
      <View style={[styles.skeletonLine, { width: '40%', height: 16 }]} />
      <View style={styles.divider} />
      <View style={[styles.skeletonLine, { width: '90%' }]} />
      <View style={[styles.skeletonLine, { width: '80%' }]} />
      <View style={[styles.skeletonLine, { width: '85%' }]} />
      <View style={{ flexDirection: 'row', justifyContent: 'center', paddingTop: spacing.md }}>
        <ActivityIndicator color={colors.textMuted} />
      </View>
    </Card>
  );
}

function BackgroundRefreshHint({ show }: { show: boolean }) {
  if (!show) return null;
  return (
    <View style={styles.refreshHint}>
      <ActivityIndicator size="small" color={colors.textMuted} />
      <Text style={{ ...textStyles.caption, color: colors.textMuted }}>최신 정보로 갱신 중…</Text>
    </View>
  );
}

const styles = StyleSheet.create({
  cardHeader: {
    flexDirection: 'row',
    alignItems: 'center',
    justifyContent: 'space-between',
    gap: spacing.md,
  },
  badgeRow: {
    flexDirection: 'row',
    flexWrap: 'wrap',
    gap: spacing.xs,
  },
  divider: {
    height: 1,
    backgroundColor: colors.borderSubtle,
  },
  exerciseRow: {
    flexDirection: 'row',
    alignItems: 'center',
    gap: spacing.md,
  },
  metaRow: {
    flexDirection: 'row',
    gap: spacing.lg,
  },
  skeletonLine: {
    height: 14,
    borderRadius: radius.sm,
    backgroundColor: colors.overlay,
    opacity: 0.5,
  },
  refreshHint: {
    flexDirection: 'row',
    alignItems: 'center',
    gap: spacing.xs,
    justifyContent: 'center',
    paddingVertical: spacing.xs,
  },
});
