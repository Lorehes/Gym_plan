import { useParams } from 'react-router-dom';

import { PageHeader } from '@/components/PageHeader';

// 단건 회고 — GET /sessions/{sessionId}.
// status 는 COMPLETED 또는 CANCELLED 일 수 있음.
export default function SessionDetailPage() {
  const { sessionId } = useParams<{ sessionId: string }>();
  return (
    <>
      <PageHeader title="세션 상세" description={`sessionId=${sessionId}`} />
      <section className="px-8 py-8">
        <div className="card p-8 text-center text-neutral-500">
          세션 회고 화면 — 다음 PR 에서 구현 예정.
        </div>
      </section>
    </>
  );
}
