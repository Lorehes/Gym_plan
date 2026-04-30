import { apiClient } from './client';

// docs/api/notification-service.md
// ⚠️ 웹 전용: /settings 만. SSE 타이머 / FCM 푸시는 모바일 전용.

export interface NotificationSettings {
  restTimerEnabled: boolean;
  workoutCompleteAlert: boolean;
  pushEnabled: boolean;
}

export async function fetchNotificationSettings(): Promise<NotificationSettings> {
  const res = await apiClient.get<NotificationSettings>('/notifications/settings');
  return res.data;
}

export async function updateNotificationSettings(
  body: NotificationSettings,
): Promise<NotificationSettings> {
  const res = await apiClient.put<NotificationSettings>('/notifications/settings', body);
  return res.data;
}
