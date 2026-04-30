import { AccountSettings } from '@/components/AccountSettings';
import { DangerZone } from '@/components/DangerZone';
import { NotificationSettings } from '@/components/NotificationSettings';
import { PageHeader } from '@/components/PageHeader';

export default function SettingsPage() {
  return (
    <>
      <PageHeader title="설정" description="프로필과 알림을 관리하세요." />

      <section className="px-4 py-6 md:px-8 md:py-8">
        <div className="mx-auto max-w-2xl space-y-6">
          <AccountSettings />
          <NotificationSettings />
          <DangerZone />
        </div>
      </section>
    </>
  );
}
