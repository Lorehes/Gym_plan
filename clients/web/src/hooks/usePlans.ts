import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';

import {
  createPlan,
  deletePlan,
  fetchPlans,
  type CreatePlanRequest,
  type PlanDetail,
  type PlanSummary,
} from '@/api/plan';

// React Query 키는 한 곳에 모아 무효화 누락을 방지.
export const planKeys = {
  all: ['plans'] as const,
  list: () => [...planKeys.all, 'list'] as const,
  detail: (planId: number) => [...planKeys.all, 'detail', planId] as const,
};

export function usePlansQuery() {
  return useQuery<PlanSummary[]>({
    queryKey: planKeys.list(),
    queryFn: fetchPlans,
  });
}

export function useCreatePlanMutation() {
  const qc = useQueryClient();
  return useMutation<PlanDetail, Error, CreatePlanRequest>({
    mutationFn: createPlan,
    onSuccess: () => {
      void qc.invalidateQueries({ queryKey: planKeys.list() });
    },
  });
}

export function useDeletePlanMutation() {
  const qc = useQueryClient();
  return useMutation<void, Error, number>({
    mutationFn: deletePlan,
    // 낙관적 업데이트 — 즉시 목록에서 제거, 실패 시 롤백.
    onMutate: async (planId) => {
      await qc.cancelQueries({ queryKey: planKeys.list() });
      const prev = qc.getQueryData<PlanSummary[]>(planKeys.list());
      if (prev) {
        qc.setQueryData<PlanSummary[]>(
          planKeys.list(),
          prev.filter((p) => p.planId !== planId),
        );
      }
      return { prev };
    },
    onError: (_err, _planId, ctx) => {
      const prev = (ctx as { prev?: PlanSummary[] } | undefined)?.prev;
      if (prev) qc.setQueryData(planKeys.list(), prev);
    },
    onSettled: () => {
      void qc.invalidateQueries({ queryKey: planKeys.list() });
    },
  });
}
