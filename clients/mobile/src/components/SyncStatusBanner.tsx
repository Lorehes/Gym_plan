import { Pressable, StyleSheet, Text, View } from 'react-native';

import { useOnlineStatus } from '@/hooks/useOnlineStatus';
import { colors, radius, spacing, textStyles } from '@/theme';
import { useSyncQueue } from '@/workout/syncQueue';

// 큐에 잔여 동기화가 있을 때 노출. dead 항목은 클릭으로 dismiss.
export function SyncStatusBanner() {
  const { isOnline } = useOnlineStatus();
  const items = useSyncQueue((s) => s.items);
  const clearDead = useSyncQueue((s) => s.clearDead);

  const pending = items.filter((i) => i.status === 'queued' || i.status === 'inflight').length;
  const dead = items.filter((i) => i.status === 'dead').length;

  if (pending === 0 && dead === 0) return null;

  if (dead > 0) {
    return (
      <Pressable
        onPress={clearDead}
        accessibilityRole="button"
        accessibilityLabel="실패한 동기화 항목 비우기"
        style={[styles.banner, styles.dead]}
      >
        <Text style={[textStyles.bodySm, styles.deadText]}>
          ⚠ {dead}개 세트 동기화 실패 — 탭하여 비우기
        </Text>
      </Pressable>
    );
  }

  return (
    <View style={[styles.banner, isOnline ? styles.pending : styles.offline]}>
      <View style={styles.dot} />
      <Text style={[textStyles.bodySm, styles.pendingText]}>
        {isOnline ? `${pending}개 동기화 중…` : `${pending}개 미동기화 — 연결되면 자동 전송`}
      </Text>
    </View>
  );
}

const styles = StyleSheet.create({
  banner: {
    flexDirection: 'row',
    alignItems: 'center',
    gap: spacing.xs,
    paddingVertical: spacing.sm,
    paddingHorizontal: spacing.lg,
    borderRadius: radius.md,
  },
  pending: { backgroundColor: 'rgba(96,165,250,0.15)' },
  offline: { backgroundColor: 'rgba(252,211,77,0.15)' },
  dead:    { backgroundColor: 'rgba(248,113,113,0.15)' },
  dot: {
    width: 8,
    height: 8,
    borderRadius: 4,
    backgroundColor: colors.primary,
  },
  pendingText: { color: colors.text, fontWeight: '500' },
  deadText:    { color: colors.error, fontWeight: '600' },
});
