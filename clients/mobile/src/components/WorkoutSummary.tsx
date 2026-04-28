import { ScrollView, StyleSheet, Text, View } from 'react-native';
import { useSafeAreaInsets } from 'react-native-safe-area-context';

import type { SessionComplete } from '@/api/workout';
import { Badge } from '@/components/Badge';
import { Button } from '@/components/Button';
import { Card } from '@/components/Card';
import { colors, radius, spacing, textStyles } from '@/theme';
import type { NewPRRecord } from '@/workout/prHelpers';

interface Props {
  result: SessionComplete;
  newPRs: NewPRRecord[];
  onClose: () => void;
}

export function WorkoutSummary({ result, newPRs, onClose }: Props) {
  const insets = useSafeAreaInsets();
  const minutes = Math.floor(result.durationSec / 60);
  const seconds = result.durationSec % 60;

  return (
    <ScrollView
      style={{ flex: 1, backgroundColor: colors.background }}
      contentContainerStyle={{
        padding: spacing.lg,
        paddingTop: insets.top + spacing.lg,
        paddingBottom: insets.bottom + spacing['2xl'],
        gap: spacing.lg,
      }}
    >
      <View style={{ alignItems: 'center', gap: spacing.sm, paddingVertical: spacing.lg }}>
        <Text style={{ fontSize: 64 }}>{newPRs.length > 0 ? '🏆' : '💪'}</Text>
        <Text style={[textStyles.display, { color: colors.text, textAlign: 'center' }]}>
          운동 완료!
        </Text>
        <Text style={[textStyles.body, { color: colors.textMuted, textAlign: 'center' }]}>
          오늘도 한 발 더 나아갔어요.
        </Text>
      </View>

      {newPRs.length > 0 && (
        <Card padding="lg" style={styles.prCard}>
          <View style={styles.prHeader}>
            <Text style={[textStyles.h3, { color: colors.success }]}>🏆 신기록 달성</Text>
            <Badge tone="accent">{`${newPRs.length}개`}</Badge>
          </View>
          <View style={{ gap: spacing.md }}>
            {newPRs.map((pr) => (
              <PRRow key={pr.exerciseId} pr={pr} />
            ))}
          </View>
        </Card>
      )}

      <Card padding="lg" style={{ gap: spacing.md }}>
        <Text style={[textStyles.h3, { color: colors.text }]}>오늘의 기록</Text>
        <View style={styles.statsGrid}>
          <StatBlock
            label="총 볼륨"
            value={formatVolume(result.totalVolume)}
            unit="kg"
            highlight
          />
          <StatBlock
            label="소요 시간"
            value={minutes > 0 ? `${minutes}` : `${seconds}`}
            unit={minutes > 0 ? `분 ${seconds}초` : '초'}
          />
          <StatBlock
            label="완료 세트"
            value={String(result.totalSets)}
            unit="세트"
          />
        </View>
      </Card>

      <View style={{ flex: 1 }} />

      <Button variant="primary" size="full" onPress={onClose}>
        홈으로
      </Button>
    </ScrollView>
  );
}

function PRRow({ pr }: { pr: NewPRRecord }) {
  return (
    <View style={styles.prRow}>
      <View style={{ flex: 1, gap: 2 }}>
        <Text style={[textStyles.bodyLg, { color: colors.text, fontWeight: '600' }]} numberOfLines={1}>
          {pr.exerciseName}
        </Text>
        <Text style={[textStyles.caption, { color: colors.textMuted }]}>
          {pr.achievedWeightKg}kg × {pr.achievedReps}회
        </Text>
      </View>
      <View style={{ alignItems: 'flex-end', gap: 2 }}>
        {pr.maxWeightDelta && (
          <Text style={[textStyles.bodySm, { color: colors.success, fontWeight: '700' }]}>
            +{(pr.maxWeightDelta.next - pr.maxWeightDelta.previous).toFixed(1)}kg
          </Text>
        )}
        {pr.est1RMDelta && (
          <Text style={[textStyles.caption, { color: colors.textMuted }]}>
            1RM {pr.est1RMDelta.next.toFixed(1)}kg
          </Text>
        )}
      </View>
    </View>
  );
}

interface StatBlockProps {
  label: string;
  value: string;
  unit: string;
  highlight?: boolean;
}

function StatBlock({ label, value, unit, highlight }: StatBlockProps) {
  return (
    <View style={[styles.statBlock, highlight && styles.statBlockHighlight]}>
      <Text style={[textStyles.caption, { color: colors.textMuted }]}>{label}</Text>
      <View style={{ flexDirection: 'row', alignItems: 'baseline', gap: 4 }}>
        <Text
          style={[
            { fontSize: 32, fontWeight: '800', color: colors.text, fontVariant: ['tabular-nums'] },
            highlight && { color: colors.accent },
          ]}
        >
          {value}
        </Text>
        <Text style={[textStyles.bodySm, { color: colors.textMuted }]}>{unit}</Text>
      </View>
    </View>
  );
}

function formatVolume(kg: number): string {
  if (kg >= 1000) return `${(kg / 1000).toFixed(1)}t`;
  return Number.isInteger(kg) ? String(kg) : kg.toFixed(1);
}

const styles = StyleSheet.create({
  prCard: {
    gap: spacing.md,
    borderWidth: 1.5,
    borderColor: colors.success,
  },
  prHeader: {
    flexDirection: 'row',
    alignItems: 'center',
    justifyContent: 'space-between',
  },
  prRow: {
    flexDirection: 'row',
    alignItems: 'center',
    gap: spacing.md,
    paddingVertical: spacing.xs,
    borderBottomWidth: 1,
    borderBottomColor: colors.borderSubtle,
    paddingBottom: spacing.sm,
  },
  statsGrid: {
    flexDirection: 'row',
    gap: spacing.sm,
  },
  statBlock: {
    flex: 1,
    padding: spacing.md,
    borderRadius: radius.lg,
    backgroundColor: colors.surface,
    gap: spacing.xs,
  },
  statBlockHighlight: {
    borderWidth: 1.5,
    borderColor: colors.accent,
  },
});
