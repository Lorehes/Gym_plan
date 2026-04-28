import { useEffect, useRef, useState } from 'react';
import { Animated, Easing, Pressable, StyleSheet, Text, View } from 'react-native';
import * as Haptics from 'expo-haptics';

import { colors, radius, spacing } from '@/theme';

// docs/design/components/timer.md
// 클라이언트 로컬 타이머가 기준. SSE는 보조(useTimerSSE에서 처리).

interface Props {
  initialSeconds: number;
  onComplete: () => void;
  onSkip: () => void;
}

export function RestTimer({ initialSeconds, onComplete, onSkip }: Props) {
  const [remaining, setRemaining] = useState(initialSeconds);
  const totalRef = useRef(initialSeconds);
  const progress = useRef(new Animated.Value(1)).current;
  const tenSecondHapticFired = useRef(false);
  const completedRef = useRef(false);
  const onCompleteRef = useRef(onComplete);
  onCompleteRef.current = onComplete;

  // 프로그레스 바 애니메이션 — 총 시간 기준 1.0 → 0.0.
  useEffect(() => {
    Animated.timing(progress, {
      toValue: 0,
      duration: initialSeconds * 1000,
      easing: Easing.linear,
      useNativeDriver: false,
    }).start();
    // 의존성: 초기값만 — 마운트 시 1회 실행.
  }, [progress, initialSeconds]);

  useEffect(() => {
    const interval = setInterval(() => {
      setRemaining((prev) => {
        const next = prev - 1;

        if (next === 10 && !tenSecondHapticFired.current) {
          tenSecondHapticFired.current = true;
          void Haptics.notificationAsync(Haptics.NotificationFeedbackType.Warning);
        }

        if (next <= 0 && !completedRef.current) {
          completedRef.current = true;
          void Haptics.notificationAsync(Haptics.NotificationFeedbackType.Success);
          // setTimeout으로 다음 tick에 호출 — setState 중 부모 unmount 방지.
          setTimeout(() => onCompleteRef.current(), 0);
          return 0;
        }
        return next;
      });
    }, 1000);

    return () => clearInterval(interval);
  }, []);

  const isWarning = remaining <= 10;
  const minutes = Math.max(0, Math.floor(remaining / 60));
  const seconds = Math.max(0, remaining % 60);
  const timeStr = `${minutes}:${String(seconds).padStart(2, '0')}`;

  const barWidth = progress.interpolate({
    inputRange: [0, 1],
    outputRange: ['0%', '100%'],
  });

  const addThirty = () => {
    totalRef.current += 30;
    setRemaining((r) => r + 30);
    // 진행 바를 다시 1.0으로 리셋하지 않고, 남은 비율로 재계산.
    progress.stopAnimation((current) => {
      const newValue = Math.min(1, current + 30 / totalRef.current);
      progress.setValue(newValue);
      Animated.timing(progress, {
        toValue: 0,
        duration: (newValue * totalRef.current) * 1000,
        easing: Easing.linear,
        useNativeDriver: false,
      }).start();
    });
  };

  return (
    <View style={styles.container}>
      <Pressable
        onPress={onSkip}
        accessibilityRole="button"
        accessibilityLabel="휴식 건너뛰기"
        style={styles.timerArea}
      >
        <Text style={[styles.time, isWarning && styles.timeWarning]}>{timeStr}</Text>
        <Text style={styles.hint}>탭하여 건너뛰기</Text>
      </Pressable>

      <View style={styles.progressBg}>
        <Animated.View
          style={[styles.progressFill, isWarning && styles.progressWarning, { width: barWidth }]}
        />
      </View>

      <Pressable onPress={addThirty} accessibilityRole="button" style={styles.addBtn}>
        <Text style={styles.addBtnText}>+30초</Text>
      </Pressable>
    </View>
  );
}

const styles = StyleSheet.create({
  container: {
    alignItems: 'center',
    paddingVertical: spacing.xl,
    gap: spacing.lg,
  },
  timerArea: {
    alignItems: 'center',
    paddingVertical: spacing.sm,
    paddingHorizontal: spacing['2xl'],
    minHeight: 48,
  },
  time: {
    fontSize: 52,
    lineHeight: 56,
    fontWeight: '300',
    color: colors.accent,
    letterSpacing: -1,
    fontVariant: ['tabular-nums'],
  },
  timeWarning: { color: colors.error, fontWeight: '500' },
  hint: { fontSize: 12, color: colors.textMuted, marginTop: spacing.xs },
  progressBg: {
    width: '100%',
    height: 4,
    backgroundColor: colors.elevated,
    borderRadius: 2,
    overflow: 'hidden',
  },
  progressFill: {
    height: 4,
    backgroundColor: colors.accent,
    borderRadius: 2,
  },
  progressWarning: { backgroundColor: colors.error },
  addBtn: {
    paddingVertical: 10,
    paddingHorizontal: 20,
    borderRadius: radius.pill,
    borderWidth: 1,
    borderColor: colors.borderDefault,
    minHeight: 48,
    justifyContent: 'center',
  },
  addBtnText: { fontSize: 15, color: colors.textMuted, fontWeight: '500' },
});
