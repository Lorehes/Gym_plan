import { Pressable, View, type PressableProps, type ViewProps } from 'react-native';

import { colors, radius } from '@/theme';

// docs/design/components/card.md

export type CardVariant = 'default' | 'elevated' | 'active' | 'completed' | 'outline';

interface CardProps extends ViewProps {
  variant?: CardVariant;
  padding?: 'sm' | 'md' | 'lg';
}

const PADDING = { sm: 12, md: 16, lg: 20 };

function variantStyle(variant: CardVariant) {
  switch (variant) {
    case 'elevated':
      return {
        backgroundColor: colors.overlay,
        shadowColor: '#000',
        shadowOffset: { width: 0, height: 4 },
        shadowOpacity: 0.3,
        shadowRadius: 8,
        elevation: 8,
      };
    case 'active':
      return {
        backgroundColor: '#1E3A8A',
        borderWidth: 1.5,
        borderColor: '#3B82F6',
      };
    case 'completed':
      return {
        backgroundColor: colors.elevated,
        borderLeftWidth: 4,
        borderLeftColor: colors.success,
      };
    case 'outline':
      return {
        backgroundColor: 'transparent',
        borderWidth: 1.5,
        borderColor: colors.borderStrong,
        borderStyle: 'dashed' as const,
      };
    case 'default':
    default:
      return { backgroundColor: colors.elevated };
  }
}

export function Card({ variant = 'default', padding = 'md', style, children, ...rest }: CardProps) {
  return (
    <View
      style={[{ borderRadius: radius.xl, padding: PADDING[padding] }, variantStyle(variant), style]}
      {...rest}
    >
      {children}
    </View>
  );
}

interface InteractiveCardProps extends Omit<PressableProps, 'style'> {
  variant?: CardVariant;
  padding?: 'sm' | 'md' | 'lg';
  children: React.ReactNode;
}

export function InteractiveCard({
  variant = 'default',
  padding = 'md',
  children,
  ...rest
}: InteractiveCardProps) {
  return (
    <Pressable
      accessibilityRole="button"
      style={({ pressed }) => [
        { borderRadius: radius.xl, padding: PADDING[padding] },
        variantStyle(variant),
        pressed && { opacity: 0.85, transform: [{ scale: 0.99 }] },
      ]}
      {...rest}
    >
      {children}
    </Pressable>
  );
}
