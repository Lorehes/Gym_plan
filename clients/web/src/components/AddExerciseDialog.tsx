import { useState } from 'react';
import { useForm, Controller } from 'react-hook-form';
import { zodResolver } from '@hookform/resolvers/zod';
import { z } from 'zod';
import { ArrowLeft, Loader2, Search, Video } from 'lucide-react';

import type { ExerciseSearchItem } from '@/api/exercise';
import type { AddPlanExerciseRequest } from '@/api/plan';
import type { MuscleGroup } from '@/api/types';
import { muscleGroupLabel } from '@/theme/muscleGroup';
import { useDebouncedValue, useExerciseSearchQuery } from '@/hooks/usePlanDetail';
import { cn } from '@/lib/cn';

import { Button } from './Button';
import { Modal } from './Modal';

// 추가 폼 스키마
const addSchema = z
  .object({
    targetSets: z.coerce.number().int().min(1, '1세트 이상').max(20, '20세트 이하'),
    targetReps: z.coerce.number().int().min(1, '1회 이상').max(100, '100회 이하'),
    isBodyweight: z.boolean(),
    targetWeightKg: z.union([
      z.coerce.number().min(0).max(500),
      z.null(),
    ]),
    restSeconds: z.coerce.number().int().min(10, '10초 이상').max(600, '600초 이하'),
    notes: z.string().max(200, '200자 이내').nullable(),
  })
  .refine(
    (d) => d.isBodyweight || d.targetWeightKg !== null,
    { message: '무게를 입력하거나 맨몸을 선택하세요.', path: ['targetWeightKg'] },
  );

type AddFormValues = z.infer<typeof addSchema>;

const MUSCLE_FILTERS: MuscleGroup[] = [
  'CHEST', 'BACK', 'SHOULDERS', 'ARMS', 'LEGS', 'CORE', 'CARDIO',
];

interface Props {
  open: boolean;
  onClose: () => void;
  onAdd: (req: AddPlanExerciseRequest) => Promise<void>;
  nextOrderIndex: number;
  loading?: boolean;
}

type Phase = 'search' | 'configure';

export function AddExerciseDialog({ open, onClose, onAdd, nextOrderIndex, loading }: Props) {
  const [phase, setPhase] = useState<Phase>('search');
  const [selected, setSelected] = useState<ExerciseSearchItem | null>(null);
  const [q, setQ] = useState('');
  const [muscleFilter, setMuscleFilter] = useState<MuscleGroup | undefined>();

  const debouncedQ = useDebouncedValue(q, 300);
  const searchParams = {
    q: debouncedQ || undefined,
    muscle: muscleFilter,
    size: 20,
  };
  const searchQuery = useExerciseSearchQuery(searchParams);

  const {
    register,
    handleSubmit,
    control,
    reset,
    watch,
    setValue,
    formState: { errors },
  } = useForm<AddFormValues>({
    resolver: zodResolver(addSchema),
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

  const handleClose = () => {
    setPhase('search');
    setSelected(null);
    setQ('');
    setMuscleFilter(undefined);
    reset();
    onClose();
  };

  const selectExercise = (ex: ExerciseSearchItem) => {
    setSelected(ex);
    reset({
      targetSets: 4,
      targetReps: 10,
      isBodyweight: false,
      targetWeightKg: null,
      restSeconds: 90,
      notes: null,
    });
    setPhase('configure');
  };

  const onSubmit = handleSubmit(async (values) => {
    if (!selected) return;
    await onAdd({
      exerciseId: selected.exerciseId,
      exerciseName: selected.name,
      muscleGroup: selected.muscleGroup,
      orderIndex: nextOrderIndex,
      targetSets: values.targetSets,
      targetReps: values.targetReps,
      targetWeightKg: values.isBodyweight ? null : values.targetWeightKg,
      restSeconds: values.restSeconds,
      notes: values.notes || null,
    });
    // 성공 후 검색 화면으로 복귀 (연속 추가 가능)
    setSelected(null);
    reset();
    setPhase('search');
  });

  return (
    <Modal
      open={open}
      onClose={handleClose}
      title={phase === 'search' ? '운동 추가' : selected?.name ?? '운동 추가'}
      description={
        phase === 'search'
          ? '종목을 검색해 루틴에 추가하세요.'
          : selected
          ? `${muscleGroupLabel[selected.muscleGroup] ?? selected.muscleGroup} · ${selected.equipment}`
          : undefined
      }
      size="xl"
      staticBackdrop={loading}
    >
      {phase === 'search' ? (
        <SearchPhase
          q={q}
          onQChange={setQ}
          muscleFilter={muscleFilter}
          onMuscleFilter={setMuscleFilter}
          searchQuery={searchQuery}
          onSelect={selectExercise}
        />
      ) : (
        <ConfigurePhase
          form={{ register, handleSubmit, control, watch, setValue, errors }}
          isBodyweight={isBodyweight}
          loading={loading}
          onBack={() => setPhase('search')}
          onSubmit={onSubmit}
          onCancel={handleClose}
        />
      )}
    </Modal>
  );
}

// ─── Search Phase ─────────────────────────────────────────────────────────────

interface SearchPhaseProps {
  q: string;
  onQChange: (v: string) => void;
  muscleFilter: MuscleGroup | undefined;
  onMuscleFilter: (v: MuscleGroup | undefined) => void;
  searchQuery: ReturnType<typeof useExerciseSearchQuery>;
  onSelect: (ex: ExerciseSearchItem) => void;
}

function SearchPhase({ q, onQChange, muscleFilter, onMuscleFilter, searchQuery, onSelect }: SearchPhaseProps) {
  const results = searchQuery.data?.content ?? [];
  const hasQuery = !!(q || muscleFilter);

  return (
    <div className="space-y-3">
      {/* 검색 입력 */}
      <div className="relative">
        <Search
          size={16}
          className="absolute left-3 top-1/2 -translate-y-1/2 text-neutral-400"
          aria-hidden
        />
        <input
          type="search"
          value={q}
          onChange={(e) => onQChange(e.target.value)}
          placeholder="종목 이름 검색"
          className="input pl-9"
          autoFocus
        />
      </div>

      {/* 부위 필터 칩 */}
      <div className="flex flex-wrap gap-1.5">
        {MUSCLE_FILTERS.map((mg) => {
          const active = muscleFilter === mg;
          return (
            <button
              key={mg}
              type="button"
              onClick={() => onMuscleFilter(active ? undefined : mg)}
              className={cn(
                'rounded-full border px-3 py-1 text-xs font-medium transition-colors',
                'focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-primary-500',
                active
                  ? 'border-primary-600 bg-primary-600 text-white'
                  : 'border-neutral-200 bg-white text-neutral-600 hover:border-primary-400',
              )}
            >
              {muscleGroupLabel[mg]}
            </button>
          );
        })}
      </div>

      {/* 결과 목록 */}
      <div className="max-h-72 overflow-y-auto rounded-md border border-neutral-200">
        {searchQuery.isFetching && (
          <div className="flex items-center justify-center py-8">
            <Loader2 size={20} className="animate-spin text-neutral-400" />
          </div>
        )}
        {!searchQuery.isFetching && !hasQuery && (
          <p className="py-8 text-center text-sm text-neutral-400">
            종목명이나 부위를 입력하면 검색결과가 표시됩니다.
          </p>
        )}
        {!searchQuery.isFetching && hasQuery && results.length === 0 && (
          <p className="py-8 text-center text-sm text-neutral-400">검색 결과가 없어요.</p>
        )}
        {!searchQuery.isFetching && results.length > 0 && (
          <ul>
            {results.map((ex) => (
              <li key={ex.exerciseId}>
                <button
                  type="button"
                  onClick={() => onSelect(ex)}
                  className="flex w-full items-center gap-3 px-4 py-3 text-left transition-colors
                             hover:bg-neutral-50 focus-visible:outline-none focus-visible:ring-2
                             focus-visible:ring-inset focus-visible:ring-primary-500"
                >
                  <div className="min-w-0 flex-1">
                    <p className="truncate text-sm font-medium text-neutral-900">{ex.name}</p>
                    <p className="mt-0.5 text-xs text-neutral-500">
                      {muscleGroupLabel[ex.muscleGroup] ?? ex.muscleGroup}
                      {' · '}
                      {ex.equipment}
                    </p>
                  </div>
                  {ex.difficulty && (
                    <span
                      className={cn(
                        'shrink-0 rounded-full px-2 py-0.5 text-xs font-medium',
                        ex.difficulty === 'BEGINNER' && 'bg-success-100 text-success-700',
                        ex.difficulty === 'INTERMEDIATE' && 'bg-warning-100 text-warning-700',
                        ex.difficulty === 'ADVANCED' && 'bg-error-100 text-error-500',
                      )}
                    >
                      {ex.difficulty === 'BEGINNER' ? '초급' : ex.difficulty === 'INTERMEDIATE' ? '중급' : '고급'}
                    </span>
                  )}
                  <Video size={14} className="shrink-0 text-neutral-300" aria-hidden />
                </button>
              </li>
            ))}
          </ul>
        )}
      </div>
    </div>
  );
}

// ─── Configure Phase ──────────────────────────────────────────────────────────

interface ConfigurePhaseProps {
  // eslint-disable-next-line @typescript-eslint/no-explicit-any
  form: any;
  isBodyweight: boolean;
  loading?: boolean;
  onBack: () => void;
  onSubmit: (e: React.FormEvent) => void;
  onCancel: () => void;
}

function ConfigurePhase({ form, isBodyweight, loading, onBack, onSubmit, onCancel }: ConfigurePhaseProps) {
  const { register, control, setValue, errors } = form;

  return (
    <form onSubmit={onSubmit} noValidate className="space-y-4">
      <div className="grid grid-cols-2 gap-3">
        {/* 세트 */}
        <div className="space-y-1">
          <label className="label">목표 세트</label>
          <input
            {...register('targetSets')}
            type="number"
            min={1}
            max={20}
            className={cn('input', errors.targetSets && 'border-error-500 focus:ring-error-500')}
          />
          {errors.targetSets && (
            <p role="alert" className="text-xs text-error-500">{errors.targetSets.message}</p>
          )}
        </div>
        {/* 횟수 */}
        <div className="space-y-1">
          <label className="label">목표 횟수</label>
          <input
            {...register('targetReps')}
            type="number"
            min={1}
            max={100}
            className={cn('input', errors.targetReps && 'border-error-500 focus:ring-error-500')}
          />
          {errors.targetReps && (
            <p role="alert" className="text-xs text-error-500">{errors.targetReps.message}</p>
          )}
        </div>
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
                  onChange={(e) => {
                    field.onChange(e);
                    if (e.target.checked) setValue('targetWeightKg', null);
                  }}
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
            disabled={isBodyweight}
            className={cn(
              'input pr-10',
              isBodyweight && 'bg-neutral-50 text-neutral-400',
              errors.targetWeightKg && 'border-error-500 focus:ring-error-500',
            )}
          />
          <span className="absolute right-3 top-1/2 -translate-y-1/2 text-sm text-neutral-400">kg</span>
        </div>
        {errors.targetWeightKg && (
          <p role="alert" className="text-xs text-error-500">{errors.targetWeightKg.message}</p>
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
            className={cn('input pr-10', errors.restSeconds && 'border-error-500 focus:ring-error-500')}
          />
          <span className="absolute right-3 top-1/2 -translate-y-1/2 text-sm text-neutral-400">초</span>
        </div>
        {errors.restSeconds && (
          <p role="alert" className="text-xs text-error-500">{errors.restSeconds.message}</p>
        )}
      </div>

      {/* 메모 */}
      <div className="space-y-1">
        <label className="label">
          메모 <span className="font-normal text-neutral-400">(선택)</span>
        </label>
        <textarea
          {...register('notes', { setValueAs: (v: string) => v || null })}
          rows={2}
          maxLength={200}
          placeholder="예: 가슴 수축 집중"
          className="input min-h-[56px] resize-y"
        />
      </div>

      <div className="flex items-center justify-between pt-1">
        <button
          type="button"
          onClick={onBack}
          className="btn-ghost flex items-center gap-1.5 text-sm"
          disabled={loading}
        >
          <ArrowLeft size={14} aria-hidden />
          검색으로
        </button>
        <div className="flex gap-2">
          <Button variant="ghost" onClick={onCancel} disabled={loading}>
            취소
          </Button>
          <Button type="submit" loading={loading}>
            추가
          </Button>
        </div>
      </div>
    </form>
  );
}
