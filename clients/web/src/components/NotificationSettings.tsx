import { useEffect, useState } from 'react';

import type { NotificationSettings as NS } from '@/api/notification';
import { Button } from '@/components/Button';
import { Skeleton } from '@/components/Skeleton';
import { SettingsRow } from '@/components/SettingsRow';
import { SettingsSection } from '@/components/SettingsSection';
import { Toggle } from '@/components/Toggle';
import { useNotificationSettingsQuery, useUpdateNotificationSettingsMutation } from '@/hooks/useSettings';
import { useToastStore } from '@/lib/toastStore';

function isEqual(a: NS, b: NS): boolean {
  return (
    a.pushEnabled === b.pushEnabled &&
    a.restTimerEnabled === b.restTimerEnabled &&
    a.workoutCompleteAlert === b.workoutCompleteAlert
  );
}

export function NotificationSettings() {
  const { data, isPending, isError, refetch } = useNotificationSettingsQuery();
  const updateMutation = useUpdateNotificationSettingsMutation();
  const showToast = useToastStore((s) => s.show);

  const [local, setLocal] = useState<NS>({
    pushEnabled: true,
    restTimerEnabled: true,
    workoutCompleteAlert: true,
  });

  useEffect(() => {
    if (data) setLocal(data);
  }, [data]);

  const isDirty = data ? !isEqual(local, data) : false;

  const toggle = (key: keyof NS) => {
    setLocal((prev) => ({ ...prev, [key]: !prev[key] }));
  };

  const handleSave = async () => {
    await updateMutation.mutateAsync(local);
    showToast('알림 설정이 저장되었어요.');
  };

  const handleReset = () => {
    if (data) setLocal(data);
  };

  if (isPending) {
    return (
      <SettingsSection title="알림 설정">
        {Array.from({ length: 3 }).map((_, i) => (
          <div key={i} className="flex items-center justify-between py-1">
            <div className="space-y-1.5">
              <Skeleton className="h-4 w-40" />
              <Skeleton className="h-3 w-56" />
            </div>
            <Skeleton className="h-6 w-11 rounded-full" />
          </div>
        ))}
      </SettingsSection>
    );
  }

  if (isError) {
    return (
      <SettingsSection title="알림 설정">
        <div className="flex flex-col items-center py-6 text-sm text-neutral-500 space-y-2">
          <p>알림 설정을 불러오지 못했어요.</p>
          <button onClick={() => refetch()} className="text-primary-600 hover:underline">
            다시 시도
          </button>
        </div>
      </SettingsSection>
    );
  }

  const subDisabled = !local.pushEnabled;

  return (
    <SettingsSection
      title="알림 설정"
      description="모바일 앱 푸시 알림 동작을 제어해요."
    >
      <div className="space-y-5">
        {/* 전체 푸시 — 마스터 토글 */}
        <SettingsRow
          label="푸시 알림"
          description="모든 알림의 수신 여부를 결정해요."
          control={
            <Toggle
              checked={local.pushEnabled}
              onChange={() => toggle('pushEnabled')}
              label="푸시 알림 전체"
            />
          }
        />

        <div className="border-t border-neutral-100 pt-4 space-y-5 pl-4">
          {/* 하위 항목들: pushEnabled가 OFF면 disabled */}
          <SettingsRow
            label="휴식 타이머 알림"
            description="세트 완료 후 휴식 타이머 종료 시 알려요."
            disabled={subDisabled}
            control={
              <Toggle
                checked={local.restTimerEnabled}
                onChange={() => toggle('restTimerEnabled')}
                disabled={subDisabled}
                label="휴식 타이머 알림"
              />
            }
          />

          <SettingsRow
            label="운동 완료 알림"
            description="운동 세션이 완료되면 요약을 푸시로 보내요."
            disabled={subDisabled}
            control={
              <Toggle
                checked={local.workoutCompleteAlert}
                onChange={() => toggle('workoutCompleteAlert')}
                disabled={subDisabled}
                label="운동 완료 알림"
              />
            }
          />
        </div>
      </div>

      {/* 저장 영역 */}
      <div className="flex items-center justify-end gap-2 border-t border-neutral-100 pt-4">
        {isDirty && (
          <span className="mr-auto text-xs text-warning-600 font-medium">
            저장하지 않은 변경사항이 있어요.
          </span>
        )}
        <Button variant="ghost" size="sm" onClick={handleReset} disabled={!isDirty}>
          되돌리기
        </Button>
        <Button
          size="sm"
          onClick={handleSave}
          disabled={!isDirty}
          loading={updateMutation.isPending}
        >
          저장
        </Button>
      </div>
    </SettingsSection>
  );
}
