import { PageHeader } from '@/components/PageHeader';

// 알림 설정(GET/PUT /notifications/settings) + 프로필 수정(PUT /users/me).
export default function SettingsPage() {
  return (
    <>
      <PageHeader title="설정" description="프로필과 알림을 관리하세요." />
      <section className="px-8 py-8 space-y-6">
        <div className="card p-8 text-center text-neutral-500">
          설정 화면 — 다음 PR 에서 구현 예정.
        </div>
      </section>
    </>
  );
}
