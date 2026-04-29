import { useParams } from 'react-router-dom';

import { PageHeader } from '@/components/PageHeader';

// 루틴 편집 — GET /plans/{id} + 운동 항목 CRUD + 드래그 reorder.
export default function PlanDetailPage() {
  const { planId } = useParams<{ planId: string }>();
  return (
    <>
      <PageHeader title="루틴 편집" description={`planId=${planId}`} />
      <section className="px-8 py-8">
        <div className="card p-8 text-center text-neutral-500">
          루틴 상세/편집 화면 — 다음 PR 에서 구현 예정.
        </div>
      </section>
    </>
  );
}
