import { StyleSheet, Text, View } from 'react-native';

import { colors, radius } from '@/theme';

// docs/design/components/badge.md — muscle group별 컬러 매핑.

export type MuscleGroup = 'CHEST' | 'BACK' | 'SHOULDERS' | 'ARMS' | 'LEGS' | 'CORE' | 'CARDIO';

const MUSCLE_COLORS: Record<MuscleGroup, { bg: string; fg: string }> = {
  CHEST:     { bg: '#7F1D1D', fg: '#FECACA' }, // red
  BACK:      { bg: '#1E3A8A', fg: '#BFDBFE' }, // blue
  SHOULDERS: { bg: '#854D0E', fg: '#FDE68A' }, // amber
  ARMS:      { bg: '#581C87', fg: '#E9D5FF' }, // purple
  LEGS:      { bg: '#14532D', fg: '#BBF7D0' }, // green
  CORE:      { bg: '#9A3412', fg: '#FED7AA' }, // orange
  CARDIO:    { bg: '#155E75', fg: '#A5F3FC' }, // cyan
};

const MUSCLE_LABEL: Record<MuscleGroup, string> = {
  CHEST: '가슴',
  BACK: '등',
  SHOULDERS: '어깨',
  ARMS: '팔',
  LEGS: '하체',
  CORE: '코어',
  CARDIO: '유산소',
};

interface BadgeProps {
  children: string;
  tone?: 'neutral' | 'primary' | 'accent';
}

export function Badge({ children, tone = 'neutral' }: BadgeProps) {
  const { bg, fg } = toneColors(tone);
  return (
    <View style={[styles.base, { backgroundColor: bg }]}>
      <Text style={[styles.text, { color: fg }]}>{children}</Text>
    </View>
  );
}

interface MuscleBadgeProps {
  group: MuscleGroup;
}

export function MuscleBadge({ group }: MuscleBadgeProps) {
  const { bg, fg } = MUSCLE_COLORS[group];
  return (
    <View style={[styles.base, { backgroundColor: bg }]}>
      <Text style={[styles.text, { color: fg }]}>{MUSCLE_LABEL[group]}</Text>
    </View>
  );
}

function toneColors(tone: 'neutral' | 'primary' | 'accent') {
  switch (tone) {
    case 'primary': return { bg: '#1E40AF', fg: '#DBEAFE' };
    case 'accent':  return { bg: '#9A3412', fg: '#FED7AA' };
    case 'neutral': default: return { bg: colors.overlay, fg: colors.text };
  }
}

const styles = StyleSheet.create({
  base: {
    paddingHorizontal: 8,
    paddingVertical: 4,
    borderRadius: radius.pill,
    alignSelf: 'flex-start',
  },
  text: {
    fontSize: 12,
    fontWeight: '600',
    letterSpacing: 0.3,
  },
});
