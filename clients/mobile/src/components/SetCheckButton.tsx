import { Pressable, StyleSheet, Text } from 'react-native';
import { useSafeAreaInsets } from 'react-native-safe-area-context';
import * as Haptics from 'expo-haptics';

// docs/design/components/set-check.md — 화면 하단 고정, 80dp+, accent → success.

export type SetCheckStatus = 'idle' | 'done';

interface Props {
  status: SetCheckStatus;
  isLastSet: boolean;
  isLastExercise: boolean;
  disabled?: boolean;
  onCheck: () => void;
  onUncheck: () => void;
}

export function SetCheckButton({
  status,
  isLastSet,
  isLastExercise,
  disabled,
  onCheck,
  onUncheck,
}: Props) {
  const insets = useSafeAreaInsets();
  const isDone = status === 'done';

  const handlePress = async () => {
    if (disabled) return;
    if (status === 'idle') {
      await Haptics.impactAsync(Haptics.ImpactFeedbackStyle.Heavy);
      onCheck();
    }
  };

  const handleLongPress = async () => {
    if (status === 'done') {
      await Haptics.notificationAsync(Haptics.NotificationFeedbackType.Warning);
      onUncheck();
    }
  };

  const label = isDone
    ? '완료!'
    : isLastSet && isLastExercise
      ? '운동 완료 (마지막 세트)'
      : isLastSet
        ? '마지막 세트 완료'
        : '세트 완료';

  return (
    <Pressable
      onPress={handlePress}
      onLongPress={handleLongPress}
      delayLongPress={800}
      disabled={disabled}
      accessibilityRole="button"
      accessibilityLabel={isDone ? '세트 완료 (길게 눌러 취소)' : label}
      accessibilityState={{ selected: isDone, disabled: !!disabled }}
      style={({ pressed }) => [
        styles.button,
        { paddingBottom: insets.bottom + 12 },
        isDone ? styles.done : styles.idle,
        pressed && styles.pressed,
        disabled && styles.disabled,
      ]}
    >
      <Text style={styles.icon}>✓</Text>
      <Text style={styles.label}>{label}</Text>
    </Pressable>
  );
}

const styles = StyleSheet.create({
  button: {
    position: 'absolute',
    bottom: 0,
    left: 0,
    right: 0,
    minHeight: 80,
    flexDirection: 'row',
    alignItems: 'center',
    justifyContent: 'center',
    gap: 12,
    paddingTop: 16,
    paddingHorizontal: 24,
  },
  idle: { backgroundColor: '#F97316' },     // accent-500
  done:  { backgroundColor: '#22C55E' },    // success-500
  pressed: { opacity: 0.88, transform: [{ scale: 0.99 }] },
  disabled: { opacity: 0.5 },
  icon: { fontSize: 28, fontWeight: '700', color: '#FFFFFF' },
  label: { fontSize: 20, fontWeight: '700', color: '#FFFFFF', letterSpacing: 0.3 },
});
