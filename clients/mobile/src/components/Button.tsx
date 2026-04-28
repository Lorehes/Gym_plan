import { ActivityIndicator, Pressable, StyleSheet, Text, type PressableProps } from 'react-native';

import { colors, radius } from '@/theme';

// docs/design/components/button.md — 모바일 최소 48dp.

export type ButtonVariant = 'primary' | 'accent' | 'secondary' | 'outline' | 'ghost' | 'danger';
export type ButtonSize = 'sm' | 'md' | 'lg' | 'xl' | 'full';

interface Props extends Omit<PressableProps, 'style' | 'children'> {
  variant?: ButtonVariant;
  size?: ButtonSize;
  loading?: boolean;
  children: string;
}

const SIZE_HEIGHT: Record<ButtonSize, number> = {
  sm: 36, md: 44, lg: 52, xl: 64, full: 64,
};
const SIZE_PADDING: Record<ButtonSize, number> = {
  sm: 12, md: 16, lg: 20, xl: 24, full: 24,
};
const SIZE_FONT: Record<ButtonSize, number> = {
  sm: 14, md: 16, lg: 18, xl: 20, full: 20,
};

const VARIANT_BG: Record<ButtonVariant, string> = {
  primary: colors.primary,
  accent: colors.accent,
  secondary: colors.elevated,
  outline: 'transparent',
  ghost: 'transparent',
  danger: colors.error,
};

const VARIANT_FG: Record<ButtonVariant, string> = {
  primary: '#FFFFFF',
  accent: '#FFFFFF',
  secondary: colors.text,
  outline: colors.primary,
  ghost: colors.textMuted,
  danger: '#FFFFFF',
};

export function Button({
  variant = 'primary',
  size = 'lg',
  loading = false,
  disabled,
  children,
  ...rest
}: Props) {
  const isDisabled = disabled || loading;
  const fg = VARIANT_FG[variant];

  return (
    <Pressable
      accessibilityRole="button"
      accessibilityState={{ disabled: !!isDisabled, busy: loading }}
      hitSlop={size === 'sm' ? { top: 6, bottom: 6, left: 6, right: 6 } : undefined}
      disabled={isDisabled}
      style={({ pressed }) => [
        styles.base,
        {
          height: SIZE_HEIGHT[size],
          paddingHorizontal: SIZE_PADDING[size],
          backgroundColor: VARIANT_BG[variant],
          width: size === 'full' ? '100%' : undefined,
        },
        variant === 'outline' && { borderWidth: 1.5, borderColor: colors.primary },
        pressed && !isDisabled && styles.pressed,
        isDisabled && styles.disabled,
      ]}
      {...rest}
    >
      {loading ? (
        <ActivityIndicator color={fg} size="small" />
      ) : (
        <Text style={[styles.text, { color: fg, fontSize: SIZE_FONT[size] }]}>{children}</Text>
      )}
    </Pressable>
  );
}

const styles = StyleSheet.create({
  base: {
    flexDirection: 'row',
    alignItems: 'center',
    justifyContent: 'center',
    borderRadius: radius.lg,
    gap: 8,
  },
  text: { fontWeight: '600', letterSpacing: 0.2 },
  pressed: { opacity: 0.85, transform: [{ scale: 0.98 }] },
  disabled: { opacity: 0.4 },
});
