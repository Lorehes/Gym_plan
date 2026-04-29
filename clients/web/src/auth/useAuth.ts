import { useAuthStore } from './authStore';

// 컴포넌트 친화 훅 — store selector 표준화.
export function useAuth() {
  return useAuthStore((s) => ({
    status: s.status,
    user: s.user,
    signIn: s.signIn,
    signOut: s.signOut,
  }));
}
