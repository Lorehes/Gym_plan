import { PageHeader } from '@/components/PageHeader';

// 종목 검색 — GET /exercises (Elasticsearch).
// 후속 작업: 검색바, 부위/장비 필터, 페이징.
export default function ExercisesPage() {
  return (
    <>
      <PageHeader title="운동 종목" description="루틴에 추가할 종목을 검색하세요." />
      <section className="px-8 py-8">
        <div className="card p-8 text-center text-neutral-500">
          종목 검색 화면 — 다음 PR 에서 구현 예정.
        </div>
      </section>
    </>
  );
}
