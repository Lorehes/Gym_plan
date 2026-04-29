import { useEffect, useState } from 'react';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';

import {
  addPlanExercise,
  deletePlanExercise,
  fetchPlan,
  reorderPlanExercises,
  updatePlan,
  updatePlanExercise,
  type AddPlanExerciseRequest,
  type PlanDetail,
  type PlanExercise,
  type UpdatePlanExerciseRequest,
  type UpdatePlanRequest,
} from '@/api/plan';
import { searchExercises, type SearchExerciseParams } from '@/api/exercise';

import { planKeys } from './usePlans';

// ─── Plan Detail ────────────────────────────────────────────────────────────

export function usePlanDetailQuery(planId: number) {
  return useQuery<PlanDetail>({
    queryKey: planKeys.detail(planId),
    queryFn: () => fetchPlan(planId),
  });
}

export function useUpdatePlanMutation(planId: number) {
  const qc = useQueryClient();
  return useMutation<PlanDetail, Error, UpdatePlanRequest>({
    mutationFn: (body) => updatePlan(planId, body),
    onSuccess: (data) => {
      qc.setQueryData<PlanDetail>(planKeys.detail(planId), data);
      void qc.invalidateQueries({ queryKey: planKeys.list() });
    },
  });
}

// ─── Exercise Items ──────────────────────────────────────────────────────────

export function useAddExerciseMutation(planId: number) {
  const qc = useQueryClient();
  return useMutation<PlanExercise, Error, AddPlanExerciseRequest>({
    mutationFn: (body) => addPlanExercise(planId, body),
    onSuccess: () => {
      void qc.invalidateQueries({ queryKey: planKeys.detail(planId) });
    },
  });
}

export function useUpdateExerciseMutation(planId: number) {
  const qc = useQueryClient();
  return useMutation<
    PlanExercise,
    Error,
    { exerciseItemId: number; body: UpdatePlanExerciseRequest }
  >({
    mutationFn: ({ exerciseItemId, body }) =>
      updatePlanExercise(planId, exerciseItemId, body),
    onSuccess: () => {
      void qc.invalidateQueries({ queryKey: planKeys.detail(planId) });
    },
  });
}

export function useDeleteExerciseMutation(planId: number) {
  const qc = useQueryClient();
  return useMutation<void, Error, number>({
    mutationFn: (exerciseItemId) => deletePlanExercise(planId, exerciseItemId),
    onMutate: async (exerciseItemId) => {
      await qc.cancelQueries({ queryKey: planKeys.detail(planId) });
      const prev = qc.getQueryData<PlanDetail>(planKeys.detail(planId));
      if (prev) {
        qc.setQueryData<PlanDetail>(planKeys.detail(planId), {
          ...prev,
          exercises: prev.exercises.filter((e) => e.id !== exerciseItemId),
        });
      }
      return { prev };
    },
    onError: (_err, _id, ctx) => {
      const prev = (ctx as { prev?: PlanDetail } | undefined)?.prev;
      if (prev) qc.setQueryData(planKeys.detail(planId), prev);
    },
    onSettled: () => {
      void qc.invalidateQueries({ queryKey: planKeys.detail(planId) });
    },
  });
}

export function useReorderExercisesMutation(planId: number) {
  const qc = useQueryClient();
  return useMutation<void, Error, number[]>({
    mutationFn: (orderedIds) => reorderPlanExercises(planId, orderedIds),
    onMutate: async (orderedIds) => {
      await qc.cancelQueries({ queryKey: planKeys.detail(planId) });
      const prev = qc.getQueryData<PlanDetail>(planKeys.detail(planId));
      if (prev) {
        const map = new Map(prev.exercises.map((e) => [e.id, e]));
        const reordered = orderedIds
          .map((id, index) => {
            const e = map.get(id);
            return e ? { ...e, orderIndex: index } : null;
          })
          .filter((e): e is PlanExercise => e !== null);
        qc.setQueryData<PlanDetail>(planKeys.detail(planId), {
          ...prev,
          exercises: reordered,
        });
      }
      return { prev };
    },
    onError: (_err, _ids, ctx) => {
      const prev = (ctx as { prev?: PlanDetail } | undefined)?.prev;
      if (prev) qc.setQueryData(planKeys.detail(planId), prev);
    },
    onSettled: () => {
      void qc.invalidateQueries({ queryKey: planKeys.detail(planId) });
    },
  });
}

// ─── Exercise Search ─────────────────────────────────────────────────────────

export const exerciseKeys = {
  search: (params: SearchExerciseParams) => ['exercises', 'search', params] as const,
};

export function useExerciseSearchQuery(params: SearchExerciseParams) {
  return useQuery({
    queryKey: exerciseKeys.search(params),
    queryFn: () => searchExercises(params),
    enabled: !!(params.q || params.muscle),
    staleTime: 60_000,
    placeholderData: (prev) => prev,
  });
}

// ─── Debounce ────────────────────────────────────────────────────────────────

export function useDebouncedValue<T>(value: T, delay: number): T {
  const [debounced, setDebounced] = useState<T>(value);
  useEffect(() => {
    const t = setTimeout(() => setDebounced(value), delay);
    return () => clearTimeout(t);
  }, [value, delay]);
  return debounced;
}
