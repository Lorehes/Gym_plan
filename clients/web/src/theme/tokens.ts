// docs/design/colors.md — 라이트모드 기본(웹), 다크모드 토큰 병기.
// Tailwind 클래스 외에 동적 스타일·차트에서 직접 사용할 때 참조.

export const lightTokens = {
  bgBase: '#FAFAFA',
  bgSurface: '#FFFFFF',
  bgElevated: '#F5F5F5',
  bgOverlay: '#E5E5E5',

  textPrimary: '#171717',
  textSecondary: '#525252',
  textDisabled: '#A3A3A3',

  borderSubtle: '#E5E5E5',
  borderDefault: '#D4D4D4',
  borderStrong: '#A3A3A3',

  primaryBtn: '#2563EB',
  accentBtn: '#F97316',
  accentBtnHover: '#EA580C',
} as const;

export const darkTokens = {
  bgBase: '#0A0A0A',
  bgSurface: '#171717',
  bgElevated: '#262626',
  bgOverlay: '#404040',

  textPrimary: '#FAFAFA',
  textSecondary: '#A3A3A3',
  textDisabled: '#525252',

  borderSubtle: '#262626',
  borderDefault: '#404040',
  borderStrong: '#737373',

  primaryBtn: '#2563EB',
  accentBtn: '#F97316',
  accentBtnHover: '#EA580C',
} as const;

// docs/design/colors.md — Recharts 등에서 부위별 색상 매핑용.
export const muscleColors: Record<string, string> = {
  CHEST: '#2563EB',
  BACK: '#F97316',
  SHOULDERS: '#22C55E',
  ARMS: '#A855F7',
  BICEPS: '#A855F7',
  TRICEPS: '#7C3AED',
  LEGS: '#EF4444',
  GLUTES: '#EC4899',
  CORE: '#F59E0B',
  CARDIO: '#06B6D4',
};

export type Tokens = typeof lightTokens;
