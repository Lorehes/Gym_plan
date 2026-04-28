import { StyleSheet, View } from 'react-native';

import { colors } from '@/theme';
import type { LocalSetLog } from '@/workout/types';

// 세트 인디케이터 — set-check.md "● 완료 / ◯ 미완료 / 큰 점 = 현재"
// failed 상태도 시각화 (border-error).

interface Props {
  total: number;
  completedSets: LocalSetLog[];
  currentIndex: number; // 다음에 수행할 세트의 0-based 인덱스 (= 완료된 세트 수)
}

export function SetIndicator({ total, completedSets, currentIndex }: Props) {
  return (
    <View style={styles.row} accessibilityLabel={`세트 진행: ${completedSets.length}/${total}`}>
      {Array.from({ length: total }).map((_, i) => {
        const log = completedSets[i];
        if (log) {
          if (log.status === 'failed') {
            return <View key={i} style={[styles.dot, styles.dotFailed]} />;
          }
          if (log.status === 'pending') {
            return <View key={i} style={[styles.dot, styles.dotPending]} />;
          }
          return <View key={i} style={[styles.dot, styles.dotDone]} />;
        }
        if (i === currentIndex) {
          return <View key={i} style={[styles.dotCurrent]} />;
        }
        return <View key={i} style={[styles.dot, styles.dotTodo]} />;
      })}
    </View>
  );
}

const styles = StyleSheet.create({
  row: {
    flexDirection: 'row',
    alignItems: 'center',
    gap: 8,
  },
  dot: {
    width: 12,
    height: 12,
    borderRadius: 6,
  },
  dotDone:    { backgroundColor: colors.success },
  dotPending: { backgroundColor: colors.success, opacity: 0.5 },
  dotFailed:  { backgroundColor: 'transparent', borderWidth: 1.5, borderColor: colors.error },
  dotTodo: {
    backgroundColor: colors.borderDefault,
    borderWidth: 1.5,
    borderColor: colors.borderStrong,
  },
  dotCurrent: {
    width: 16,
    height: 16,
    borderRadius: 8,
    backgroundColor: colors.accent,
  },
});
