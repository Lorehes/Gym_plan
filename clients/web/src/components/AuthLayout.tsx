import { Dumbbell } from 'lucide-react';

interface Props {
  title: string;
  description?: string;
  children: React.ReactNode;
  footer?: React.ReactNode;
}

// 좌측 브랜딩(데스크탑) + 우측 폼. 좁은 화면에서는 단일 컬럼.
export function AuthLayout({ title, description, children, footer }: Props) {
  return (
    <div className="min-h-full grid lg:grid-cols-2">
      {/* 좌측 브랜딩 — lg 이상에서만 표시 */}
      <aside
        className="hidden lg:flex flex-col justify-between p-12 text-white"
        style={{
          // docs/design/colors.md primary-700 → primary-900 그라데이션
          backgroundImage: 'linear-gradient(135deg, #1D4ED8 0%, #1E3A8A 100%)',
        }}
      >
        <div className="flex items-center gap-2">
          <Dumbbell size={28} />
          <span className="text-2xl font-bold tracking-tight">GymPlan</span>
        </div>
        <div className="space-y-4 max-w-md">
          <h2 className="text-3xl font-bold leading-tight">
            계획은 책상에서,<br />
            실행은 체육관에서.
          </h2>
          <p className="text-primary-100 text-sm leading-relaxed">
            GymPlan 웹은 이번 주 루틴을 미리 짜고, 지난 운동을 회고하는 공간이에요.
            모바일 앱은 체육관에서 한 손으로 세트를 기록합니다.
          </p>
        </div>
        <p className="text-xs text-primary-200">© GymPlan</p>
      </aside>

      {/* 우측 폼 */}
      <main className="flex items-center justify-center bg-neutral-50 p-6 sm:p-10">
        <div className="w-full max-w-md">
          <div className="lg:hidden mb-8 flex items-center gap-2 text-primary-600">
            <Dumbbell size={22} />
            <span className="text-xl font-bold tracking-tight">GymPlan</span>
          </div>

          <div className="card p-6 sm:p-8">
            <header className="mb-6">
              <h1 className="text-2xl font-bold text-neutral-900">{title}</h1>
              {description && (
                <p className="mt-1 text-sm text-neutral-500">{description}</p>
              )}
            </header>
            {children}
          </div>

          {footer && <div className="mt-6 text-center text-sm text-neutral-500">{footer}</div>}
        </div>
      </main>
    </div>
  );
}
