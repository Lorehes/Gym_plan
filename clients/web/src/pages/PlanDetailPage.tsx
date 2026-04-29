import { forwardRef, useEffect, useRef, useState } from 'react';
import { useNavigate, useParams } from 'react-router-dom';
import {
  DndContext,
  DragOverlay,
  KeyboardSensor,
  PointerSensor,
  closestCenter,
  useSensor,
  useSensors,
  type DragEndEvent,
  type DragStartEvent,
} from '@dnd-kit/core';
import {
  SortableContext,
  sortableKeyboardCoordinates,
  useSortable,
  verticalListSortingStrategy,
} from '@dnd-kit/sortable';
import { CSS } from '@dnd-kit/utilities';
import {
  ArrowLeft,
  Dumbbell,
  GripVertical,
  Pencil,
  Plus,
  RotateCcw,
  Trash2,
  X,
} from 'lucide-react';

import type { PlanExercise } from '@/api/plan';
import type { AddPlanExerciseRequest, UpdatePlanRequest } from '@/api/plan';
import { ApiException } from '@/api/types';
import { AddExerciseDialog } from '@/components/AddExerciseDialog';
import { Button } from '@/components/Button';
import { ConfirmDialog } from '@/components/ConfirmDialog';
import { EditExerciseItemModal, type EditExerciseFormValues } from '@/components/EditExerciseItemModal';
import { EmptyState } from '@/components/EmptyState';
import { Skeleton } from '@/components/Skeleton';
import { muscleGroupLabel, dayOfWeekLabel, DAY_OF_WEEK_VALUES } from '@/theme/muscleGroup';
import { cn } from '@/lib/cn';
import { useToastStore } from '@/lib/toastStore';
import {
  useAddExerciseMutation,
  useDeleteExerciseMutation,
  usePlanDetailQuery,
  useReorderExercisesMutation,
  useUpdateExerciseMutation,
  useUpdatePlanMutation,
} from '@/hooks/usePlanDetail';

export default function PlanDetailPage() {
  const { planId: planIdStr } = useParams<{ planId: string }>();
  const planId = Number(planIdStr);
  const navigate = useNavigate();
  const toast = useToastStore((s) => s.show);

  const query = usePlanDetailQuery(planId);
  const updatePlan = useUpdatePlanMutation(planId);
  const addExercise = useAddExerciseMutation(planId);
  const updateExercise = useUpdateExerciseMutation(planId);
  const deleteExercise = useDeleteExerciseMutation(planId);
  const reorderExercises = useReorderExercisesMutation(planId);

  // ─── Plan info edit ──────────────────────────────────────────────────────
  const [editingPlan, setEditingPlan] = useState(false);
  const [planEditValues, setPlanEditValues] = useState<UpdatePlanRequest>({});

  const startEditPlan = () => {
    if (!query.data) return;
    setPlanEditValues({
      name: query.data.name,
      description: query.data.description ?? '',
      dayOfWeek: query.data.dayOfWeek,
    });
    setEditingPlan(true);
  };

  const cancelEditPlan = () => setEditingPlan(false);

  const savePlan = async () => {
    if (!planEditValues.name?.trim()) return;
    await updatePlan.mutateAsync({
      name: planEditValues.name.trim(),
      description: (planEditValues.description as string)?.trim() || null,
      dayOfWeek: planEditValues.dayOfWeek ?? null,
    } as UpdatePlanRequest);
    setEditingPlan(false);
  };

  // ESC 로 취소
  const nameInputRef = useRef<HTMLInputElement>(null);
  useEffect(() => {
    if (editingPlan) nameInputRef.current?.focus();
  }, [editingPlan]);
  useEffect(() => {
    const handler = (e: KeyboardEvent) => {
      if (e.key === 'Escape' && editingPlan) cancelEditPlan();
    };
    window.addEventListener('keydown', handler);
    return () => window.removeEventListener('keydown', handler);
  }, [editingPlan]);

  // ─── Exercise modals ─────────────────────────────────────────────────────
  const [editTarget, setEditTarget] = useState<PlanExercise | null>(null);
  const [deleteTarget, setDeleteTarget] = useState<PlanExercise | null>(null);
  const [addOpen, setAddOpen] = useState(false);

  const handleEditSave = async (exerciseItemId: number, values: EditExerciseFormValues) => {
    await updateExercise.mutateAsync({
      exerciseItemId,
      body: {
        targetSets: values.targetSets,
        targetReps: values.targetReps,
        targetWeightKg: values.isBodyweight ? null : (values.targetWeightKg ?? null),
        restSeconds: values.restSeconds,
        notes: values.notes,
      },
    });
    setEditTarget(null);
    toast('운동 설정이 저장되었습니다.');
  };

  const handleAdd = async (req: AddPlanExerciseRequest): Promise<void> => {
    const added = await addExercise.mutateAsync(req);
    toast(`${added.exerciseName}이(가) 추가되었습니다.`);
  };

  const handleDelete = () => {
    if (!deleteTarget) return;
    deleteExercise.mutate(deleteTarget.id, {
      onSettled: () => setDeleteTarget(null),
    });
  };

  // ─── Drag and drop ───────────────────────────────────────────────────────
  const [activeId, setActiveId] = useState<number | null>(null);

  const sensors = useSensors(
    useSensor(PointerSensor, { activationConstraint: { distance: 8 } }),
    useSensor(KeyboardSensor, { coordinateGetter: sortableKeyboardCoordinates }),
  );

  const exercises = [...(query.data?.exercises ?? [])].sort(
    (a, b) => a.orderIndex - b.orderIndex,
  );

  const activeExercise = activeId !== null ? exercises.find((e) => e.id === activeId) : null;

  const handleDragStart = ({ active }: DragStartEvent) => {
    setActiveId(active.id as number);
  };

  const handleDragEnd = ({ active, over }: DragEndEvent) => {
    setActiveId(null);
    if (!over || active.id === over.id) return;

    const oldIndex = exercises.findIndex((e) => e.id === active.id);
    const newIndex = exercises.findIndex((e) => e.id === over.id);
    if (oldIndex === -1 || newIndex === -1) return;

    const reordered = [...exercises];
    const [moved] = reordered.splice(oldIndex, 1);
    reordered.splice(newIndex, 0, moved);

    reorderExercises.mutate(reordered.map((e) => e.id));
  };

  // ─── Render ──────────────────────────────────────────────────────────────
  const nextOrderIndex = exercises.length;

  if (query.isPending) return <DetailSkeleton />;

  if (query.isError) {
    const message =
      query.error instanceof ApiException
        ? query.error.message
        : '루틴을 불러오지 못했어요.';
    return (
      <div className="px-4 py-8 md:px-8">
        <div className="card p-8 text-center space-y-3">
          <p className="text-sm text-neutral-700">{message}</p>
          <Button variant="ghost" onClick={() => query.refetch()}>
            <RotateCcw size={14} aria-hidden /> 다시 시도
          </Button>
        </div>
      </div>
    );
  }

  const plan = query.data;

  return (
    <div className="mx-auto max-w-3xl px-4 py-6 md:px-8 md:py-8">
      {/* 뒤로가기 */}
      <button
        type="button"
        onClick={() => navigate('/plans')}
        className="mb-4 flex items-center gap-1.5 text-sm text-neutral-500 hover:text-neutral-800
                   focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-primary-500 rounded"
      >
        <ArrowLeft size={15} aria-hidden />
        루틴 목록
      </button>

      {/* 루틴 정보 카드 */}
      <div className="card p-5 mb-6">
        {editingPlan ? (
          <PlanEditForm
            ref={nameInputRef}
            values={planEditValues}
            onChange={setPlanEditValues}
            onSave={savePlan}
            onCancel={cancelEditPlan}
            saving={updatePlan.isPending}
          />
        ) : (
          <PlanInfoView plan={plan} onEdit={startEditPlan} />
        )}
      </div>

      {/* 운동 목록 제목 + 추가 버튼 */}
      <div className="mb-3 flex items-center justify-between">
        <h2 className="text-base font-semibold text-neutral-700">
          운동 목록
          {exercises.length > 0 && (
            <span className="ml-1.5 text-sm font-normal text-neutral-400">
              {exercises.length}종목
            </span>
          )}
        </h2>
        <Button size="sm" onClick={() => setAddOpen(true)}>
          <Plus size={14} aria-hidden />
          운동 추가
        </Button>
      </div>

      {/* 운동 카드 리스트 */}
      {exercises.length === 0 ? (
        <EmptyState
          icon={Dumbbell}
          title="운동이 없어요"
          description="+ 운동 추가 버튼으로 첫 운동을 추가해보세요."
          action={
            <Button onClick={() => setAddOpen(true)}>
              <Plus size={16} aria-hidden />
              첫 운동 추가하기
            </Button>
          }
        />
      ) : (
        <DndContext
          sensors={sensors}
          collisionDetection={closestCenter}
          onDragStart={handleDragStart}
          onDragEnd={handleDragEnd}
        >
          <SortableContext
            items={exercises.map((e) => e.id)}
            strategy={verticalListSortingStrategy}
          >
            <ol className="space-y-2" aria-label="운동 목록 (드래그로 순서 변경 가능)">
              {exercises.map((ex) => (
                <li key={ex.id}>
                  <SortableExerciseCard
                    exercise={ex}
                    onEdit={() => setEditTarget(ex)}
                    onDelete={() => setDeleteTarget(ex)}
                    isReordering={activeId !== null}
                  />
                </li>
              ))}
            </ol>
          </SortableContext>
          <DragOverlay>
            {activeExercise && (
              <ExerciseCard exercise={activeExercise} isDraggingOverlay />
            )}
          </DragOverlay>
        </DndContext>
      )}

      {/* 모달 / 다이얼로그 */}
      <AddExerciseDialog
        open={addOpen}
        onClose={() => setAddOpen(false)}
        onAdd={handleAdd}
        nextOrderIndex={nextOrderIndex}
        loading={addExercise.isPending}
      />

      <EditExerciseItemModal
        open={editTarget !== null}
        exercise={editTarget}
        onClose={() => setEditTarget(null)}
        onSave={handleEditSave}
        loading={updateExercise.isPending}
      />

      <ConfirmDialog
        open={deleteTarget !== null}
        onClose={() => (deleteExercise.isPending ? undefined : setDeleteTarget(null))}
        onConfirm={handleDelete}
        title="운동을 삭제할까요?"
        description={
          deleteTarget
            ? `"${deleteTarget.exerciseName}"이(가) 루틴에서 제거됩니다.`
            : undefined
        }
        confirmLabel="삭제"
        cancelLabel="취소"
        variant="danger"
        loading={deleteExercise.isPending}
      />
    </div>
  );
}

// ─── Plan Info View ───────────────────────────────────────────────────────────

function PlanInfoView({
  plan,
  onEdit,
}: {
  plan: { name: string; description: string | null; dayOfWeek: number | null; isTemplate: boolean };
  onEdit: () => void;
}) {
  return (
    <div className="flex items-start justify-between gap-3">
      <div className="min-w-0">
        <h1 className="text-xl font-bold text-neutral-900 truncate">{plan.name}</h1>
        {plan.description && (
          <p className="mt-1 text-sm text-neutral-500">{plan.description}</p>
        )}
        <div className="mt-2 flex flex-wrap gap-1.5 text-xs">
          {plan.dayOfWeek !== null && plan.dayOfWeek !== undefined && (
            <span className="inline-flex items-center rounded-full bg-primary-50 px-2.5 py-1 text-primary-700 font-medium">
              {dayOfWeekLabel[plan.dayOfWeek] ?? '?'}요일
            </span>
          )}
          {plan.isTemplate && (
            <span className="inline-flex items-center rounded-full bg-neutral-100 px-2.5 py-1 text-neutral-600">
              템플릿
            </span>
          )}
        </div>
      </div>
      <Button variant="ghost" size="sm" onClick={onEdit}>
        <Pencil size={14} aria-hidden />
        편집
      </Button>
    </div>
  );
}

// ─── Plan Edit Form ───────────────────────────────────────────────────────────

interface PlanEditFormProps {
  values: UpdatePlanRequest;
  onChange: (v: UpdatePlanRequest) => void;
  onSave: () => void;
  onCancel: () => void;
  saving: boolean;
}

const PlanEditForm = forwardRef<HTMLInputElement, PlanEditFormProps>(function PlanEditForm(
  { values, onChange, onSave, onCancel, saving },
  ref,
) {
  return (
    <div className="space-y-3">
      {/* 이름 */}
      <div className="space-y-1">
        <label className="label">루틴 이름</label>
        <input
          ref={ref}
          type="text"
          value={values.name ?? ''}
          onChange={(e) => onChange({ ...values, name: e.target.value })}
          maxLength={100}
          placeholder="루틴 이름"
          disabled={saving}
          className="input"
        />
      </div>

      {/* 설명 */}
      <div className="space-y-1">
        <label className="label">
          설명 <span className="font-normal text-neutral-400">(선택)</span>
        </label>
        <textarea
          value={(values.description as string) ?? ''}
          onChange={(e) => onChange({ ...values, description: e.target.value })}
          rows={2}
          maxLength={500}
          placeholder="루틴 설명"
          disabled={saving}
          className="input min-h-[56px] resize-y"
        />
      </div>

      {/* 요일 */}
      <div className="space-y-2">
        <label className="label mb-0">요일 <span className="font-normal text-neutral-400">(선택)</span></label>
        <div className="flex flex-wrap gap-1.5">
          {DAY_OF_WEEK_VALUES.map((day) => {
            const active = values.dayOfWeek === day;
            return (
              <button
                key={day}
                type="button"
                disabled={saving}
                onClick={() => onChange({ ...values, dayOfWeek: active ? null : day })}
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
            disabled={saving}
            onClick={() => onChange({ ...values, dayOfWeek: null })}
            className={cn(
              'h-9 px-3 rounded-md text-sm font-medium border transition-colors ml-1',
              'focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-primary-500',
              values.dayOfWeek === null || values.dayOfWeek === undefined
                ? 'bg-neutral-100 text-neutral-900 border-neutral-300'
                : 'bg-white text-neutral-500 border-neutral-200 hover:border-neutral-400',
            )}
          >
            지정 안 함
          </button>
        </div>
      </div>

      <div className="flex justify-end gap-2 pt-1">
        <Button
          variant="ghost"
          size="sm"
          onClick={onCancel}
          disabled={saving}
        >
          <X size={14} aria-hidden />
          취소
        </Button>
        <Button
          size="sm"
          onClick={onSave}
          loading={saving}
          disabled={!values.name?.trim()}
        >
          저장
        </Button>
      </div>
    </div>
  );
});

// ─── Sortable Exercise Card ───────────────────────────────────────────────────

interface SortableExerciseCardProps {
  exercise: PlanExercise;
  onEdit: () => void;
  onDelete: () => void;
  isReordering: boolean;
}

function SortableExerciseCard({ exercise, onEdit, onDelete, isReordering }: SortableExerciseCardProps) {
  const { attributes, listeners, setNodeRef, transform, transition, isDragging } = useSortable({
    id: exercise.id,
  });

  return (
    <div
      ref={setNodeRef}
      style={{ transform: CSS.Transform.toString(transform), transition }}
      className={cn(isDragging && 'opacity-40')}
    >
      <ExerciseCard
        exercise={exercise}
        onEdit={onEdit}
        onDelete={onDelete}
        dragHandleProps={{ ...attributes, ...listeners }}
        isReordering={isReordering}
      />
    </div>
  );
}

// ─── Exercise Card ────────────────────────────────────────────────────────────

interface ExerciseCardProps {
  exercise: PlanExercise;
  onEdit?: () => void;
  onDelete?: () => void;
  dragHandleProps?: React.HTMLAttributes<HTMLButtonElement>;
  isReordering?: boolean;
  isDraggingOverlay?: boolean;
}

function ExerciseCard({
  exercise,
  onEdit,
  onDelete,
  dragHandleProps,
  isReordering,
  isDraggingOverlay,
}: ExerciseCardProps) {
  const weightLabel =
    exercise.targetWeightKg !== null
      ? `${exercise.targetWeightKg}kg`
      : '맨몸';

  return (
    <div
      className={cn(
        'card flex items-center gap-3 px-4 py-3',
        isDraggingOverlay && 'shadow-xl rotate-1',
      )}
    >
      {/* 드래그 핸들 */}
      <button
        type="button"
        aria-label={`${exercise.exerciseName} 드래그 핸들 — 스페이스로 잡기, 화살표로 이동, 엔터로 놓기`}
        className={cn(
          'shrink-0 touch-none rounded p-1 text-neutral-300',
          'hover:text-neutral-500 focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-primary-500',
          isReordering && 'cursor-grabbing',
          !isReordering && 'cursor-grab',
        )}
        {...dragHandleProps}
      >
        <GripVertical size={18} aria-hidden />
      </button>

      {/* 운동 정보 */}
      <div className="min-w-0 flex-1">
        <div className="flex flex-wrap items-center gap-2">
          <span className="text-sm font-semibold text-neutral-900 truncate">
            {exercise.exerciseName}
          </span>
          <span className="rounded-full bg-neutral-100 px-2 py-0.5 text-xs text-neutral-500">
            {muscleGroupLabel[exercise.muscleGroup] ?? exercise.muscleGroup}
          </span>
        </div>
        <div className="mt-1 flex flex-wrap items-center gap-x-3 gap-y-0.5 text-xs text-neutral-500">
          <span>{exercise.targetSets} × {exercise.targetReps}회</span>
          <span>{weightLabel}</span>
          <span>휴식 {exercise.restSeconds}초</span>
          {exercise.notes && (
            <span className="text-neutral-400 italic truncate max-w-[180px]">
              {exercise.notes}
            </span>
          )}
        </div>
      </div>

      {/* 액션 버튼 */}
      {!isDraggingOverlay && (
        <div className="flex shrink-0 items-center gap-1">
          <button
            type="button"
            onClick={onEdit}
            aria-label={`${exercise.exerciseName} 수정`}
            className="rounded-md p-1.5 text-neutral-400 hover:bg-neutral-100 hover:text-neutral-700
                       focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-primary-500"
          >
            <Pencil size={15} aria-hidden />
          </button>
          <button
            type="button"
            onClick={onDelete}
            aria-label={`${exercise.exerciseName} 삭제`}
            className="rounded-md p-1.5 text-neutral-400 hover:bg-error-50 hover:text-error-500
                       focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-error-500"
          >
            <Trash2 size={15} aria-hidden />
          </button>
        </div>
      )}
    </div>
  );
}

// ─── Skeleton ─────────────────────────────────────────────────────────────────

function DetailSkeleton() {
  return (
    <div className="mx-auto max-w-3xl px-4 py-6 md:px-8 md:py-8">
      <Skeleton className="mb-4 h-5 w-20" />
      <div className="card p-5 mb-6 space-y-3">
        <Skeleton className="h-7 w-1/2" />
        <Skeleton className="h-4 w-3/4" />
        <Skeleton className="h-6 w-14" />
      </div>
      <div className="space-y-2">
        {Array.from({ length: 4 }).map((_, i) => (
          <div key={i} className="card flex items-center gap-3 px-4 py-3">
            <Skeleton className="h-6 w-5 shrink-0" />
            <div className="flex-1 space-y-2">
              <Skeleton className="h-4 w-1/3" />
              <Skeleton className="h-3 w-1/2" />
            </div>
          </div>
        ))}
      </div>
    </div>
  );
}
