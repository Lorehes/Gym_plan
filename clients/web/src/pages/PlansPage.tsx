import { PageHeader } from '@/components/PageHeader';

// 루틴 목록 — GET /plans (PlanSummary[]).
// 후속 작업: 카드 그리드, 요일 그룹핑, 새 루틴 생성 모달.
export default function PlansPage() {
  return (
    <>
      <PageHeader
        title="루틴"
        description="이번 주 운동을 미리 계획하세요."
      />
      <section className="px-8 py-8">
        <div className="card p-8 text-center text-neutral-500">
          루틴 목록 화면 — 다음 PR 에서 구현 예정.
        </div>
      </section>
    </>
  );
}
