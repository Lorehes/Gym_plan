import { useEffect, useState } from 'react';
import { useForm, Controller } from 'react-hook-form';
import { zodResolver } from '@hookform/resolvers/zod';
import { z } from 'zod';

import { ApiException } from '@/api/types';
import { useCreatePlanMutation } from '@/hooks/usePlans';
import { DAY_OF_WEEK_VALUES, dayOfWeekLabel } from '@/theme/muscleGroup';
import { cn } from '@/lib/cn';

import { Modal } from './Modal';
import { Input } from './Input';
import { Button } from './Button';

// 백엔드 검증과 일치 (plan-service PlanRequests.kt CreatePlanRequest).
// name 1~100, description 0~500, dayOfWeek 0~6 또는 null.
const schema = z.object({
  name: z
    .string()
    .min(1, '루틴 이름을 입력해주세요.')
    .max(100, '루틴 이름은 100자 이내여야 합니다.'),
  description: z
    .string()
    .max(500, '설명은 500자 이내여야 합니다.')
    .optional(),
  dayOfWeek: z
    .number()
    .int()
    .min(0)
    .max(6)
    .nullable(),
});

type FormValues = z.infer<typeof schema>;

interface Props {
  open: boolean;
  onClose: () => void;
  // 생성 완료 후 라우팅 이동을 부모가 처리.
  onCreated: (planId: number) => void;
}

export function CreatePlanModal({ open, onClose, onCreated }: Props) {
  const create = useCreatePlanMutation();
  const [serverError, setServerError] = useState<string | null>(null);

  const {
    register,
    handleSubmit,
    control,
    reset,
    setFocus,
    formState: { errors, isSubmitting },
  } = useForm<FormValues>({
    resolver: zodResolver(schema),
    defaultValues: { name: '', description: '', dayOfWeek: null },
    mode: 'onSubmit',
    reValidateMode: 'onChange',
  });

  // 모달이 열릴 때마다 폼 초기화 + 첫 입력에 포커스.
  useEffect(() => {
    if (open) {
      reset({ name: '', description: '', dayOfWeek: null });
      setServerError(null);
      // setFocus 는 mount 후 호출되어야 — 한 틱 지연.
      const t = setTimeout(() => setFocus('name'), 50);
      return () => clearTimeout(t);
    }
    return undefined;
  }, [open, reset, setFocus]);

  const onSubmit = handleSubmit(async (values) => {
    setServerError(null);
    try {
      const detail = await create.mutateAsync({
        name: values.name.trim(),
        description: values.description?.trim() || undefined,
        dayOfWeek: values.dayOfWeek,
      });
      onCreated(detail.planId);
    } catch (err) {
      const msg =
        err instanceof ApiException
          ? err.message || '루틴을 만들지 못했어요. 잠시 후 다시 시도해주세요.'
          : '루틴을 만들지 못했어요. 잠시 후 다시 시도해주세요.';
      setServerError(msg);
    }
  });

  return (
    <Modal
      open={open}
      onClose={onClose}
      title="새 루틴 만들기"
      description="이름과 요일만 정해도 시작할 수 있어요."
      size="lg"
      staticBackdrop={isSubmitting}
    >
      <form onSubmit={onSubmit} noValidate className="space-y-5">
        <Input
          {...register('name')}
          label="루틴 이름"
          placeholder="예: 가슴/삼두 루틴"
          maxLength={100}
          error={errors.name?.message}
          disabled={isSubmitting}
        />

        <div className="space-y-1">
          <label className="label" htmlFor="plan-description">설명 <span className="text-neutral-400 font-normal">(선택)</span></label>
          <textarea
            id="plan-description"
            {...register('description')}
            rows={3}
            maxLength={500}
            placeholder="루틴 의도, 메모 등 (최대 500자)"
            className={cn(
              'input min-h-[88px] resize-y',
              errors.description && 'border-error-500 focus:border-error-500 focus:ring-error-500',
            )}
            disabled={isSubmitting}
            aria-invalid={errors.description ? true : undefined}
          />
          {errors.description && (
            <p role="alert" className="text-xs text-error-500">{errors.description.message}</p>
          )}
        </div>

        <Controller
          control={control}
          name="dayOfWeek"
          render={({ field }) => (
            <fieldset className="space-y-2" disabled={isSubmitting}>
              <legend className="label mb-1">요일 <span className="text-neutral-400 font-normal">(선택)</span></legend>
              <div className="flex flex-wrap gap-1.5" role="radiogroup" aria-label="요일 선택">
                {DAY_OF_WEEK_VALUES.map((day) => {
                  const active = field.value === day;
                  return (
                    <button
                      key={day}
                      type="button"
                      role="radio"
                      aria-checked={active}
                      onClick={() => field.onChange(active ? null : day)}
                      className={cn(
                        'h-9 w-10 rounded-md text-sm font-medium border transition-colors',
                        'focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-primary-500',
                        active
                          ? 'bg-primary-600 text-white border-primary-600'
                          : 'bg-white text-neutral-700 border-neutral-300 hover:border-primary-400',
                      )}
                    >
                      {dayOfWeekLabel[day]}
                    </button>
                  );
                })}
                <button
                  type="button"
                  onClick={() => field.onChange(null)}
                  className={cn(
                    'h-9 px-3 rounded-md text-sm font-medium border transition-colors ml-1',
                    'focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-primary-500',
                    field.value === null
                      ? 'bg-neutral-100 text-neutral-900 border-neutral-300'
                      : 'bg-white text-neutral-500 border-neutral-200 hover:border-neutral-400',
                  )}
                >
                  지정 안 함
                </button>
              </div>
            </fieldset>
          )}
        />

        {serverError && (
          <p
            role="alert"
            aria-live="polite"
            className="rounded-md bg-error-100 px-3 py-2 text-sm text-error-500"
          >
            {serverError}
          </p>
        )}

        <div className="flex justify-end gap-2 pt-2">
          <Button variant="ghost" onClick={onClose} disabled={isSubmitting}>
            취소
          </Button>
          <Button type="submit" loading={isSubmitting}>
            만들기
          </Button>
        </div>
      </form>
    </Modal>
  );
}
