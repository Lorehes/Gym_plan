import { PageHeader } from '@/components/PageHeader';

// 히스토리 — GET /sessions/history (페이징).
// SessionDetail 내부 exercises[].sets[] 는 nested 구조 (flat 아님).
export default function HistoryPage() {
  return (
    <>
      <PageHeader title="히스토리" description="지난 운동 세션을 회고하세요." />
      <section className="px-8 py-8">
        <div className="card p-8 text-center text-neutral-500">
          히스토리 목록 — 다음 PR 에서 구현 예정.
        </div>
      </section>
    </>
  );
}
