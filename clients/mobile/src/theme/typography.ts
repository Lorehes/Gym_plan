import { Platform, type TextStyle } from 'react-native';

// Tokens mirror docs/design/typography.md.

export const fontFamily = {
  regular: Platform.select({ ios: 'System', android: 'Roboto', default: 'System' }),
  mono: Platform.select({ ios: 'Courier New', android: 'monospace', default: 'monospace' }),
};

export const textStyles = {
  display: { fontSize: 32, lineHeight: 38, fontWeight: '700' },
  h1:      { fontSize: 26, lineHeight: 34, fontWeight: '700' },
  h2:      { fontSize: 22, lineHeight: 29, fontWeight: '600' },
  h3:      { fontSize: 18, lineHeight: 25, fontWeight: '600' },
  bodyLg:  { fontSize: 17, lineHeight: 26, fontWeight: '400' },
  body:    { fontSize: 15, lineHeight: 23, fontWeight: '400' },
  bodySm:  { fontSize: 14, lineHeight: 21, fontWeight: '400' },
  caption: { fontSize: 12, lineHeight: 17, fontWeight: '400' },

  // 체육관 특화 — 즉각 판독용 대형 숫자
  setNum:  { fontSize: 48, lineHeight: 48, fontWeight: '800' },
  weight:  { fontSize: 36, lineHeight: 36, fontWeight: '700' },
  reps:    { fontSize: 36, lineHeight: 36, fontWeight: '700' },
  timer:   { fontSize: 52, lineHeight: 52, fontWeight: '300', letterSpacing: -1 },
} as const satisfies Record<string, TextStyle>;

export const letterSpacing = {
  tight: -0.5,
  normal: 0,
  wide: 0.3,
  widest: 1.0,
} as const;
