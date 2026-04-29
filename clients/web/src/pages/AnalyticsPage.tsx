import { PageHeader } from '@/components/PageHeader';

// 대시보드 — summary / volume / frequency / personal-records 통합.
// 후속 작업: 요약 카드, Recharts 라인차트, 캘린더 히트맵, PR 표.
export default function AnalyticsPage() {
  return (
    <>
      <PageHeader title="통계" description="주·월 단위 운동 통계와 신기록을 확인하세요." />
      <section className="px-8 py-8 space-y-6">
        <div className="card p-8 text-center text-neutral-500">
          대시보드 — 다음 PR 에서 구현 예정.
        </div>
      </section>
    </>
  );
}
