import { Text, View } from 'react-native';

import { useOnlineStatus } from '@/hooks/useOnlineStatus';
import { colors, radius, spacing, textStyles } from '@/theme';

export function OfflineBanner() {
  const { isOnline } = useOnlineStatus();
  if (isOnline) return null;

  return (
    <View
      style={{
        backgroundColor: colors.warning,
        paddingVertical: spacing.sm,
        paddingHorizontal: spacing.lg,
        borderRadius: radius.md,
      }}
    >
      <Text style={{ ...textStyles.bodySm, color: '#171717', fontWeight: '600' }}>
        오프라인 — 연결되면 자동으로 동기화됩니다
      </Text>
    </View>
  );
}
