import { useState } from 'react';
import { useNavigate } from 'react-router-dom';
import { CalendarPlus, Plus, RotateCcw } from 'lucide-react';

import type { PlanSummary } from '@/api/plan';
import { ApiException } from '@/api/types';
import { Button } from '@/components/Button';
import { ConfirmDialog } from '@/components/ConfirmDialog';
import { CreatePlanModal } from '@/components/CreatePlanModal';
import { EmptyState } from '@/components/EmptyState';
import { PageHeader } from '@/components/PageHeader';
import { PlanCard } from '@/components/PlanCard';
import { Skeleton } from '@/components/Skeleton';
import { useDeletePlanMutation, usePlansQuery } from '@/hooks/usePlans';

export default function PlansPage() {
  const navigate = useNavigate();
  const plansQuery = usePlansQuery();
  const deleteMutation = useDeletePlanMutation();

  const [createOpen, setCreateOpen] = useState(false);
  const [deleteTarget, setDeleteTarget] = useState<PlanSummary | null>(null);

  const handleDelete = () => {
    if (!deleteTarget) return;
    const target = deleteTarget;
    deleteMutation.mutate(target.planId, {
      onSettled: () => {
        // 성공/실패 모두 다이얼로그는 닫는다 (실패 시 토스트로 알림 — 추후).
        setDeleteTarget(null);
      },
    });
  };

  return (
    <>
      <PageHeader
        title="내 루틴"
        description="이번 주 운동을 미리 계획하세요."
        actions={
          <Button variant="primary" onClick={() => setCreateOpen(true)}>
            <Plus size={16} aria-hidden />
            새 루틴
          </Button>
        }
      />

      <section className="px-4 py-4 md:px-8 md:py-8">
        <PlansContent
          query={plansQuery}
          onCreate={() => setCreateOpen(true)}
          onDelete={(p) => setDeleteTarget(p)}
        />
      </section>

      <CreatePlanModal
        open={createOpen}
        onClose={() => setCreateOpen(false)}
        onCreated={(planId) => {
          setCreateOpen(false);
          navigate(`/plans/${planId}`);
        }}
      />

      <ConfirmDialog
        open={deleteTarget !== null}
        onClose={() => (deleteMutation.isPending ? undefined : setDeleteTarget(null))}
        onConfirm={handleDelete}
        title="루틴을 삭제할까요?"
        description={
          deleteTarget
            ? `"${deleteTarget.name}" 루틴이 삭제됩니다. 이 작업은 되돌릴 수 없어요.`
            : undefined
        }
        confirmLabel="삭제"
        cancelLabel="취소"
        variant="danger"
        loading={deleteMutation.isPending}
      />
    </>
  );
}

interface ContentProps {
  query: ReturnType<typeof usePlansQuery>;
  onCreate: () => void;
  onDelete: (plan: PlanSummary) => void;
}

function PlansContent({ query, onCreate, onDelete }: ContentProps) {
  if (query.isPending) {
    return (
      <div className="grid gap-4 grid-cols-[repeat(auto-fill,minmax(220px,1fr))]">
        {Array.from({ length: 6 }).map((_, i) => (
          <PlanCardSkeleton key={i} />
        ))}
      </div>
    );
  }

  if (query.isError) {
    const message =
      query.error instanceof ApiException
        ? query.error.message
        : '루틴을 불러오지 못했어요.';
    return (
      <div className="card p-8 text-center space-y-3">
        <p className="text-sm text-neutral-700">{message}</p>
        <Button variant="ghost" onClick={() => query.refetch()}>
          <RotateCcw size={14} aria-hidden /> 다시 시도
        </Button>
      </div>
    );
  }

  const plans = query.data;
  if (!plans || plans.length === 0) {
    return (
      <EmptyState
        icon={CalendarPlus}
        title="아직 만든 루틴이 없어요"
        description="요일별 루틴을 미리 짜두면 체육관에서 모바일 앱이 바로 보여줍니다."
        action={
          <Button variant="primary" onClick={onCreate}>
            <Plus size={16} aria-hidden /> 첫 루틴 만들기
          </Button>
        }
      />
    );
  }

  return (
    <div className="grid gap-4 grid-cols-[repeat(auto-fill,minmax(220px,1fr))]">
      {plans.map((plan) => (
        <PlanCard key={plan.planId} plan={plan} onDelete={onDelete} />
      ))}
    </div>
  );
}

function PlanCardSkeleton() {
  return (
    <div className="card p-5">
      <Skeleton className="h-5 w-3/4" />
      <div className="mt-3 flex gap-1.5">
        <Skeleton className="h-5 w-12" />
        <Skeleton className="h-5 w-16" />
      </div>
    </div>
  );
}
