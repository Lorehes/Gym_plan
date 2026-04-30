import { Navigate, useLocation } from 'react-router-dom';

import { useAuthStore } from '@/auth/authStore';

interface Props {
  children: React.ReactNode;
}

// 인증 가드 — bootstrap 중에는 잠깐 깜빡이지 않도록 로딩 상태 유지.
export function RequireAuth({ children }: Props) {
  const status = useAuthStore((s) => s.status);
  const location = useLocation();

  if (status === 'loading') {
    return (
      <div className="flex h-full items-center justify-center text-neutral-500 text-sm">
        불러오는 중…
      </div>
    );
  }

  if (status === 'unauthenticated') {
    return <Navigate to="/login" replace state={{ from: location }} />;
  }

  return <>{children}</>;
}
