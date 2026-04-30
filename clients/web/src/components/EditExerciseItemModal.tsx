import { useEffect } from 'react';
import { Controller, useForm } from 'react-hook-form';
import { zodResolver } from '@hookform/resolvers/zod';
import { z } from 'zod';

import type { PlanExercise } from '@/api/plan';
import { muscleGroupLabel } from '@/theme/muscleGroup';
import { cn } from '@/lib/cn';

import { Button } from './Button';
import { Input } from './Input';
import { Modal } from './Modal';

const schema = z
  .object({
    targetSets: z.coerce
      .number({ invalid_type_error: '숫자를 입력하세요.' })
      .int()
      .min(1, '1세트 이상')
      .max(20, '20세트 이하'),
    targetReps: z.coerce
      .number({ invalid_type_error: '숫자를 입력하세요.' })
      .int()
      .min(1, '1회 이상')
      .max(100, '100회 이하'),
    isBodyweight: z.boolean(),
    targetWeightKg: z.union([
      z.coerce.number({ invalid_type_error: '숫자를 입력하세요.' }).min(0).max(500),
      z.null(),
    ]),
    restSeconds: z.coerce
      .number({ invalid_type_error: '숫자를 입력하세요.' })
      .int()
      .min(10, '10초 이상')
      .max(600, '600초 이하'),
    notes: z.string().max(200, '200자 이내').nullable(),
  })
  .refine(
    (d) => d.isBodyweight || d.targetWeightKg !== null,
    { message: '무게를 입력하거나 맨몸을 선택하세요.', path: ['targetWeightKg'] },
  );

type FormValues = z.infer<typeof schema>;

interface Props {
  open: boolean;
  exercise: PlanExercise | null;
  onClose: () => void;
  onSave: (exerciseItemId: number, values: FormValues) => void;
  loading?: boolean;
}

export type EditExerciseFormValues = FormValues;

export function EditExerciseItemModal({ open, exercise, onClose, onSave, loading }: Props) {
  const {
    register,
    handleSubmit,
    control,
    reset,
    watch,
    setValue,
    formState: { errors },
  } = useForm<FormValues>({
    resolver: zodResolver(schema),
    defaultValues: {
      targetSets: 4,
      targetReps: 10,
      isBodyweight: false,
      targetWeightKg: null,
      restSeconds: 90,
      notes: null,
    },
  });

  const isBodyweight = watch('isBodyweight');

  useEffect(() => {
    if (open && exercise) {
      reset({
        targetSets: exercise.targetSets,
        targetReps: exercise.targetReps,
        isBodyweight: exercise.targetWeightKg === null,
        targetWeightKg: exercise.targetWeightKg,
        restSeconds: exercise.restSeconds,
        notes: exercise.notes ?? null,
      });
    }
  }, [open, exercise, reset]);

  // 맨몸 토글 시 무게 초기화
  useEffect(() => {
    if (isBodyweight) setValue('targetWeightKg', null);
  }, [isBodyweight, setValue]);

  const onSubmit = handleSubmit((values) => {
    if (!exercise) return;
    const final: FormValues = {
      ...values,
      targetWeightKg: values.isBodyweight ? null : values.targetWeightKg,
    };
    onSave(exercise.id, final);
  });

  if (!exercise) return null;

  return (
    <Modal
      open={open}
      onClose={onClose}
      title="운동 수정"
      description={`${exercise.exerciseName} · ${muscleGroupLabel[exercise.muscleGroup] ?? exercise.muscleGroup}`}
      staticBackdrop={loading}
    >
      <form onSubmit={onSubmit} noValidate className="space-y-4">
        {/* 세트 / 횟수 */}
        <div className="grid grid-cols-2 gap-3">
          <Input
            {...register('targetSets')}
            label="목표 세트"
            type="number"
            min={1}
            max={20}
            error={errors.targetSets?.message}
            disabled={loading}
          />
          <Input
            {...register('targetReps')}
            label="목표 횟수"
            type="number"
            min={1}
            max={100}
            error={errors.targetReps?.message}
            disabled={loading}
          />
        </div>

        {/* 무게 */}
        <div className="space-y-1">
          <div className="flex items-center justify-between">
            <label className="label mb-0">목표 무게</label>
            <Controller
              control={control}
              name="isBodyweight"
              render={({ field }) => (
                <label className="flex cursor-pointer items-center gap-1.5 text-sm text-neutral-600">
                  <input
                    type="checkbox"
                    checked={field.value}
                    onChange={field.onChange}
                    disabled={loading}
                    className="h-4 w-4 rounded border-neutral-300 text-primary-600
                               focus:ring-primary-500 focus:ring-offset-0"
                  />
                  맨몸
                </label>
              )}
            />
          </div>
          <div className="relative">
            <input
              {...register('targetWeightKg')}
              type="number"
              min={0}
              max={500}
              step={0.5}
              placeholder={isBodyweight ? '맨몸' : '예: 70'}
              disabled={loading || isBodyweight}
              className={cn(
                'input pr-10',
                isBodyweight && 'bg-neutral-50 text-neutral-400',
                errors.targetWeightKg && 'border-error-500 focus:border-error-500 focus:ring-error-500',
              )}
            />
            <span className="absolute right-3 top-1/2 -translate-y-1/2 text-sm text-neutral-400">
              kg
            </span>
          </div>
          {errors.targetWeightKg && (
            <p role="alert" className="text-xs text-error-500">
              {errors.targetWeightKg.message}
            </p>
          )}
        </div>

        {/* 휴식 시간 */}
        <div className="space-y-1">
          <label className="label">휴식 시간</label>
          <div className="relative">
            <input
              {...register('restSeconds')}
              type="number"
              min={10}
              max={600}
              step={10}
              disabled={loading}
              className={cn(
                'input pr-10',
                errors.restSeconds && 'border-error-500 focus:border-error-500 focus:ring-error-500',
              )}
            />
            <span className="absolute right-3 top-1/2 -translate-y-1/2 text-sm text-neutral-400">
              초
            </span>
          </div>
          {errors.restSeconds && (
            <p role="alert" className="text-xs text-error-500">
              {errors.restSeconds.message}
            </p>
          )}
        </div>

        {/* 메모 */}
        <div className="space-y-1">
          <label className="label">
            메모{' '}
            <span className="font-normal text-neutral-400">(선택)</span>
          </label>
          <textarea
            {...register('notes', { setValueAs: (v) => v || null })}
            rows={2}
            maxLength={200}
            placeholder="예: 가슴 수축 집중"
            disabled={loading}
            className="input min-h-[64px] resize-y"
          />
          {errors.notes && (
            <p role="alert" className="text-xs text-error-500">
              {errors.notes.message}
            </p>
          )}
        </div>

        <div className="flex justify-end gap-2 pt-1">
          <Button variant="ghost" onClick={onClose} disabled={loading}>
            취소
          </Button>
          <Button type="submit" loading={loading}>
            저장
          </Button>
        </div>
      </form>
    </Modal>
  );
}
