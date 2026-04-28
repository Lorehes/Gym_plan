// Tokens mirror docs/design/colors.md.
// 모바일은 다크모드 기본 — `colors`는 dark, `lightColors`는 옵션.

export const palette = {
  primary: {
    50: '#EFF6FF', 100: '#DBEAFE', 200: '#BFDBFE',
    300: '#93C5FD', 400: '#60A5FA', 500: '#3B82F6',
    600: '#2563EB', 700: '#1D4ED8', 800: '#1E40AF', 900: '#1E3A8A',
  },
  accent: {
    300: '#FCD34D', 400: '#FB923C',
    500: '#F97316', 600: '#EA580C', 700: '#C2410C',
  },
  neutral: {
    50: '#FAFAFA', 100: '#F5F5F5', 200: '#E5E5E5',
    300: '#D4D4D4', 400: '#A3A3A3', 500: '#737373',
    600: '#525252', 700: '#404040', 800: '#262626', 900: '#171717',
  },
  success: { 100: '#DCFCE7', 500: '#22C55E', dark: '#4ADE80' },
  warning: { 100: '#FEF3C7', 500: '#F59E0B', dark: '#FCD34D' },
  error:   { 100: '#FEE2E2', 500: '#EF4444', dark: '#F87171' },
} as const;

export const colors = {
  background: '#0A0A0A',
  surface:    '#171717',
  elevated:   '#262626',
  overlay:    '#404040',

  text:       '#FAFAFA',
  textMuted:  '#A3A3A3',
  textDisabled: '#525252',

  borderSubtle:  '#262626',
  borderDefault: '#404040',
  borderStrong:  '#737373',

  primary:      palette.primary[600],
  primaryHover: palette.primary[700],
  accent:       palette.accent[500],
  accentHover:  palette.accent[600],
  accentPressed: palette.accent[700],

  success: palette.success.dark,
  warning: palette.warning.dark,
  error:   palette.error.dark,
} as const;

export const lightColors = {
  background: palette.neutral[50],
  surface:    '#FFFFFF',
  elevated:   palette.neutral[100],
  overlay:    palette.neutral[200],

  text:       palette.neutral[900],
  textMuted:  palette.neutral[600],
  textDisabled: palette.neutral[400],

  borderSubtle:  palette.neutral[200],
  borderDefault: palette.neutral[300],
  borderStrong:  palette.neutral[400],

  primary:      palette.primary[600],
  primaryHover: palette.primary[700],
  accent:       palette.accent[500],
  accentHover:  palette.accent[600],
  accentPressed: palette.accent[700],

  success: palette.success[500],
  warning: palette.warning[500],
  error:   palette.error[500],
} as const;

export type Colors = typeof colors;
