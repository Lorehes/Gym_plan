import { useEffect, useState } from 'react';
import { useForm } from 'react-hook-form';
import { zodResolver } from '@hookform/resolvers/zod';
import { z } from 'zod';
import { Pencil } from 'lucide-react';

import { Button } from '@/components/Button';
import { Input } from '@/components/Input';
import { Skeleton } from '@/components/Skeleton';
import { SettingsSection } from '@/components/SettingsSection';
import { SettingsRow } from '@/components/SettingsRow';
import { useUserMeQuery, useUpdateUserMutation } from '@/hooks/useSettings';
import { useToastStore } from '@/lib/toastStore';

const schema = z.object({
  nickname: z.string().min(2, '닉네임은 2자 이상이어야 해요.').max(20, '닉네임은 20자 이하여야 해요.'),
});
type FormValues = z.infer<typeof schema>;

function InitialAvatar({ nickname }: { nickname: string }) {
  const initial = nickname.charAt(0).toUpperCase();
  return (
    <div className="flex h-14 w-14 items-center justify-center rounded-full bg-primary-100 text-lg font-bold text-primary-700 shrink-0">
      {initial}
    </div>
  );
}

function formatDate(iso: string): string {
  const d = new Date(iso);
  return `${d.getFullYear()}년 ${d.getMonth() + 1}월 ${d.getDate()}일`;
}

export function AccountSettings() {
  const { data, isPending, isError, refetch } = useUserMeQuery();
  const updateMutation = useUpdateUserMutation();
  const showToast = useToastStore((s) => s.show);

  const [editing, setEditing] = useState(false);

  const {
    register,
    handleSubmit,
    reset,
    watch,
    formState: { errors, isDirty },
  } = useForm<FormValues>({
    resolver: zodResolver(schema),
    defaultValues: { nickname: '' },
  });

  // data 로드 후 폼 초기화
  useEffect(() => {
    if (data) reset({ nickname: data.nickname });
  }, [data, reset]);

  const onSubmit = handleSubmit(async ({ nickname }) => {
    await updateMutation.mutateAsync({ nickname });
    showToast('닉네임이 저장되었어요.');
    setEditing(false);
    reset({ nickname });
  });

  const handleCancel = () => {
    reset({ nickname: data?.nickname ?? '' });
    setEditing(false);
  };

  const currentNickname = watch('nickname');

  if (isPending) {
    return (
      <SettingsSection title="계정 정보">
        <div className="flex items-center gap-4">
          <Skeleton className="h-14 w-14 rounded-full" />
          <div className="space-y-2 flex-1">
            <Skeleton className="h-4 w-32" />
            <Skeleton className="h-4 w-48" />
          </div>
        </div>
        <Skeleton className="h-4 w-24 mt-2" />
      </SettingsSection>
    );
  }

  if (isError || !data) {
    return (
      <SettingsSection title="계정 정보">
        <div className="flex flex-col items-center py-6 text-sm text-neutral-500 space-y-2">
          <p>프로필을 불러오지 못했어요.</p>
          <button onClick={() => refetch()} className="text-primary-600 hover:underline">
            다시 시도
          </button>
        </div>
      </SettingsSection>
    );
  }

  return (
    <SettingsSection title="계정 정보" description="프로필과 기본 정보를 관리해요.">
      {/* 아바타 + 이름 */}
      <div className="flex items-center gap-4">
        {data.profileImg ? (
          <img
            src={data.profileImg}
            alt={data.nickname}
            className="h-14 w-14 rounded-full object-cover shrink-0"
          />
        ) : (
          <InitialAvatar nickname={data.nickname} />
        )}
        <div className="min-w-0">
          <p className="text-base font-semibold text-neutral-900">{data.nickname}</p>
          <p className="text-sm text-neutral-500">{data.email}</p>
        </div>
      </div>

      <div className="space-y-4 pt-2">
        {/* 이메일 — 읽기 전용 */}
        <SettingsRow
          label="이메일"
          control={
            <span className="text-sm text-neutral-500">{data.email}</span>
          }
        />

        {/* 닉네임 */}
        <div className="border-t border-neutral-100 pt-4">
          {editing ? (
            <form onSubmit={onSubmit} className="space-y-3">
              <Input
                label="닉네임"
                {...register('nickname')}
                error={errors.nickname?.message}
                autoFocus
              />
              <div className="flex gap-2 justify-end">
                <Button variant="ghost" size="sm" onClick={handleCancel} type="button">
                  취소
                </Button>
                <Button
                  type="submit"
                  size="sm"
                  loading={updateMutation.isPending}
                  disabled={!isDirty || currentNickname === data.nickname}
                >
                  저장
                </Button>
              </div>
            </form>
          ) : (
            <SettingsRow
              label="닉네임"
              description="앱에서 표시되는 이름이에요."
              control={
                <button
                  type="button"
                  onClick={() => setEditing(true)}
                  className="inline-flex items-center gap-1.5 rounded-md px-2.5 py-1.5 text-sm
                             text-primary-600 hover:bg-primary-50
                             focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-primary-500"
                >
                  <Pencil size={13} aria-hidden />
                  {data.nickname}
                </button>
              }
            />
          )}
        </div>

        {/* 가입일 */}
        <SettingsRow
          label="가입일"
          control={
            <span className="text-sm text-neutral-500">{formatDate(data.createdAt)}</span>
          }
        />
      </div>
    </SettingsSection>
  );
}
