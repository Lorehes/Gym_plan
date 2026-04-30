import { useEffect } from 'react';
import { Navigate, Route, Routes, useNavigate } from 'react-router-dom';

import { setUnauthorizedHandler } from '@/api/client';
import { useAuthStore } from '@/auth/authStore';
import { AppShell } from '@/components/AppShell';
import { RedirectIfAuthenticated } from '@/components/RedirectIfAuthenticated';
import { RequireAuth } from '@/components/RequireAuth';

import LoginPage from '@/pages/LoginPage';
import RegisterPage from '@/pages/RegisterPage';
import PlansPage from '@/pages/PlansPage';
import PlanDetailPage from '@/pages/PlanDetailPage';
import ExercisesPage from '@/pages/ExercisesPage';
import AnalyticsPage from '@/pages/AnalyticsPage';
import HistoryPage from '@/pages/HistoryPage';
import SessionDetailPage from '@/pages/SessionDetailPage';
import SettingsPage from '@/pages/SettingsPage';
import NotFoundPage from '@/pages/NotFoundPage';

export default function App() {
  const bootstrap = useAuthStore((s) => s.bootstrap);
  const signOut = useAuthStore((s) => s.signOut);
  const navigate = useNavigate();

  useEffect(() => {
    bootstrap();
  }, [bootstrap]);

  // 인터셉터의 401(refresh 실패) → 로그인 화면으로 강제 이동.
  useEffect(() => {
    setUnauthorizedHandler(() => {
      void signOut();
      navigate('/login', { replace: true });
    });
    return () => setUnauthorizedHandler(null);
  }, [signOut, navigate]);

  return (
    <Routes>
      <Route
        path="/login"
        element={
          <RedirectIfAuthenticated>
            <LoginPage />
          </RedirectIfAuthenticated>
        }
      />
      <Route
        path="/register"
        element={
          <RedirectIfAuthenticated>
            <RegisterPage />
          </RedirectIfAuthenticated>
        }
      />

      <Route
        element={
          <RequireAuth>
            <AppShell />
          </RequireAuth>
        }
      >
        <Route index element={<Navigate to="/plans" replace />} />
        <Route path="/plans" element={<PlansPage />} />
        <Route path="/plans/:planId" element={<PlanDetailPage />} />
        <Route path="/exercises" element={<ExercisesPage />} />
        <Route path="/analytics" element={<AnalyticsPage />} />
        <Route path="/history" element={<HistoryPage />} />
        <Route path="/history/:sessionId" element={<SessionDetailPage />} />
        <Route path="/settings" element={<SettingsPage />} />
      </Route>

      <Route path="*" element={<NotFoundPage />} />
    </Routes>
  );
}
