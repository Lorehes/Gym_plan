import { useEffect, useRef, useState } from 'react';
import {
  Pressable,
  StyleSheet,
  Text,
  TextInput,
  View,
} from 'react-native';

import { colors, radius, spacing } from '@/theme';

// docs/design/components/input.md
// 무게/횟수 빠른 수정 — 평소엔 숫자만 표시, 탭하면 인라인 편집(decimal-pad + selectTextOnFocus).
// 운동 중 한 손/엄지 조작 우선 — 80dp 높이.

interface Props {
  label: string;
  unit?: string;
  value: number;
  onCommit: (next: number) => void;
  step?: number;
  decimal?: boolean;
  disabled?: boolean;
}

export function NumberCell({
  label,
  unit,
  value,
  onCommit,
  step = 1,
  decimal = false,
  disabled,
}: Props) {
  const [editing, setEditing] = useState(false);
  const [text, setText] = useState(formatValue(value, decimal));
  const inputRef = useRef<TextInput>(null);

  useEffect(() => {
    if (!editing) setText(formatValue(value, decimal));
  }, [value, editing, decimal]);

  const enterEdit = () => {
    if (disabled) return;
    setEditing(true);
    requestAnimationFrame(() => inputRef.current?.focus());
  };

  const commit = () => {
    setEditing(false);
    const parsed = decimal ? parseFloat(text) : parseInt(text, 10);
    if (!Number.isFinite(parsed) || parsed < 0) {
      setText(formatValue(value, decimal));
      return;
    }
    if (parsed !== value) onCommit(parsed);
  };

  const adjust = (delta: number) => {
    if (disabled) return;
    const next = Math.max(0, value + delta);
    onCommit(next);
  };

  return (
    <View style={styles.container}>
      <Text style={styles.label}>{label}</Text>

      <View style={styles.row}>
        <StepButton sign="-" onPress={() => adjust(-step)} disabled={disabled} />

        <Pressable
          onPress={enterEdit}
          accessibilityRole="adjustable"
          accessibilityLabel={`${label} ${value}${unit ?? ''}, 탭하여 수정`}
          style={[styles.value, editing && styles.valueEditing]}
        >
          {editing ? (
            <TextInput
              ref={inputRef}
              keyboardType={decimal ? 'decimal-pad' : 'number-pad'}
              value={text}
              onChangeText={setText}
              onBlur={commit}
              onSubmitEditing={commit}
              selectTextOnFocus
              returnKeyType="done"
              maxLength={6}
              style={styles.input}
            />
          ) : (
            <Text style={styles.number}>{formatValue(value, decimal)}</Text>
          )}
        </Pressable>

        <StepButton sign="+" onPress={() => adjust(step)} disabled={disabled} />
      </View>

      {unit && <Text style={styles.unit}>{unit}</Text>}
    </View>
  );
}

interface StepButtonProps {
  sign: '+' | '-';
  onPress: () => void;
  disabled?: boolean;
}

function StepButton({ sign, onPress, disabled }: StepButtonProps) {
  return (
    <Pressable
      onPress={onPress}
      disabled={disabled}
      hitSlop={8}
      accessibilityRole="button"
      accessibilityLabel={sign === '+' ? '증가' : '감소'}
      style={({ pressed }) => [
        styles.stepBtn,
        pressed && !disabled && styles.stepBtnPressed,
        disabled && styles.stepBtnDisabled,
      ]}
    >
      <Text style={styles.stepBtnText}>{sign}</Text>
    </Pressable>
  );
}

function formatValue(value: number, decimal: boolean) {
  if (decimal) {
    // 정수면 정수로, 아니면 소수점 1자리.
    return Number.isInteger(value) ? String(value) : value.toFixed(1);
  }
  return String(value);
}

const styles = StyleSheet.create({
  container: {
    alignItems: 'center',
    gap: spacing.xs,
  },
  label: {
    fontSize: 12,
    fontWeight: '500',
    color: colors.textMuted,
    letterSpacing: 0.3,
  },
  row: {
    flexDirection: 'row',
    alignItems: 'center',
    gap: spacing.sm,
  },
  stepBtn: {
    width: 48,
    height: 48,
    borderRadius: radius.lg,
    backgroundColor: colors.elevated,
    alignItems: 'center',
    justifyContent: 'center',
    borderWidth: 1,
    borderColor: colors.borderDefault,
  },
  stepBtnPressed: { opacity: 0.7, transform: [{ scale: 0.96 }] },
  stepBtnDisabled: { opacity: 0.4 },
  stepBtnText: { fontSize: 24, fontWeight: '600', color: colors.text, lineHeight: 26 },
  value: {
    minWidth: 96,
    height: 80,
    borderRadius: radius.xl,
    backgroundColor: colors.elevated,
    borderWidth: 1.5,
    borderColor: colors.borderDefault,
    alignItems: 'center',
    justifyContent: 'center',
    paddingHorizontal: spacing.md,
  },
  valueEditing: { borderColor: colors.accent },
  number: {
    fontSize: 36,
    fontWeight: '700',
    color: colors.text,
    letterSpacing: -0.5,
    fontVariant: ['tabular-nums'],
  },
  input: {
    fontSize: 36,
    fontWeight: '700',
    color: colors.text,
    textAlign: 'center',
    letterSpacing: -0.5,
    minWidth: 72,
    padding: 0,
  },
  unit: { fontSize: 14, color: colors.textMuted, marginTop: 2 },
});
