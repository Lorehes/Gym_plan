import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';

import { fetchMe, updateMe, type UpdateMeRequest } from '@/api/auth';
import { fetchNotificationSettings, updateNotificationSettings, type NotificationSettings } from '@/api/notification';
import { useAuthStore } from '@/auth/authStore';

export const settingsKeys = {
  userMe: () => ['user', 'me'] as const,
  notifications: () => ['notifications', 'settings'] as const,
};

export function useUserMeQuery() {
  return useQuery({
    queryKey: settingsKeys.userMe(),
    queryFn: fetchMe,
    staleTime: 5 * 60_000,
  });
}

export function useUpdateUserMutation() {
  const queryClient = useQueryClient();
  const setUser = useAuthStore((s) => s.setUser);

  return useMutation({
    mutationFn: (body: UpdateMeRequest) => updateMe(body),
    onSuccess: (updated) => {
      queryClient.invalidateQueries({ queryKey: settingsKeys.userMe() });
      // 사이드바 닉네임 즉시 반영
      setUser({
        id: updated.userId,
        email: updated.email,
        nickname: updated.nickname,
        profileImg: updated.profileImg,
      });
    },
  });
}

export function useNotificationSettingsQuery() {
  return useQuery({
    queryKey: settingsKeys.notifications(),
    queryFn: fetchNotificationSettings,
    staleTime: 5 * 60_000,
  });
}

export function useUpdateNotificationSettingsMutation() {
  const queryClient = useQueryClient();

  return useMutation({
    mutationFn: (body: NotificationSettings) => updateNotificationSettings(body),
    onSuccess: (updated) => {
      queryClient.setQueryData(settingsKeys.notifications(), updated);
    },
  });
}
