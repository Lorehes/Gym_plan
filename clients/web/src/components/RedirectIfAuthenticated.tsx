import { Navigate } from 'react-router-dom';

import { useAuthStore } from '@/auth/authStore';

interface Props {
  children: React.ReactNode;
  to?: string;
}

// 이미 로그인 상태에서 /login, /register 등 진입 시 메인으로 보냄.
// bootstrap 중에는 children 을 그대로 보여줘 깜빡임을 방지.
export function RedirectIfAuthenticated({ children, to = '/plans' }: Props) {
  const status = useAuthStore((s) => s.status);
  if (status === 'authenticated') {
    return <Navigate to={to} replace />;
  }
  return <>{children}</>;
}
