import { Text, View } from 'react-native';

import { useAuthStore } from '@/auth/authStore';
import { Button } from '@/components/Button';
import { colors, spacing, textStyles } from '@/theme';
import { screenStyles } from '../_styles';

export function ProfileScreen() {
  const user = useAuthStore((s) => s.user);
  const signOut = useAuthStore((s) => s.signOut);

  return (
    <View style={screenStyles.container}>
      <Text style={screenStyles.title}>프로필</Text>

      {user && (
        <View style={{ gap: spacing.xs }}>
          <Text style={{ ...textStyles.h3, color: colors.text }}>{user.nickname}</Text>
          <Text style={{ ...textStyles.body, color: colors.textMuted }}>{user.email}</Text>
        </View>
      )}

      <View style={{ flex: 1 }} />

      <Button variant="secondary" onPress={() => void signOut()} size="full">
        로그아웃
      </Button>
    </View>
  );
}
