import { StyleSheet } from 'react-native';

import { colors, spacing, textStyles } from '@/theme';

export const screenStyles = StyleSheet.create({
  container: {
    flex: 1,
    backgroundColor: colors.background,
    padding: spacing.lg,
    gap: spacing.md,
  },
  title: { ...textStyles.h1, color: colors.text },
  body: { ...textStyles.body, color: colors.textMuted },
});
