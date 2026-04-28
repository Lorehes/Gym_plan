import { createBottomTabNavigator } from '@react-navigation/bottom-tabs';

import { useSessionRecovery } from '@/hooks/useSessionRecovery';
import { HomeScreen } from '@/screens/main/HomeScreen';
import { WorkoutScreen } from '@/screens/main/WorkoutScreen';
import { HistoryScreen } from '@/screens/main/HistoryScreen';
import { ProfileScreen } from '@/screens/main/ProfileScreen';
import { colors } from '@/theme';

import type { MainTabParamList } from './types';

const Tab = createBottomTabNavigator<MainTabParamList>();

export function MainTabs() {
  // 인증 트리 진입 시 진행 중 세션을 workoutStore에 복구.
  useSessionRecovery();

  return (
    <Tab.Navigator
      screenOptions={{
        headerStyle: { backgroundColor: colors.background },
        headerTintColor: colors.text,
        tabBarStyle: {
          backgroundColor: colors.surface,
          borderTopColor: colors.borderSubtle,
          height: 64,
        },
        tabBarActiveTintColor: colors.accent,
        tabBarInactiveTintColor: colors.textMuted,
        tabBarLabelStyle: { fontSize: 12, fontWeight: '600' },
      }}
    >
      <Tab.Screen name="Home" component={HomeScreen} options={{ title: '홈' }} />
      <Tab.Screen name="Workout" component={WorkoutScreen} options={{ title: '운동' }} />
      <Tab.Screen name="History" component={HistoryScreen} options={{ title: '기록' }} />
      <Tab.Screen name="Profile" component={ProfileScreen} options={{ title: '프로필' }} />
    </Tab.Navigator>
  );
}
