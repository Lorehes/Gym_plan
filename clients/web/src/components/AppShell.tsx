import { NavLink, Outlet } from 'react-router-dom';
import { BarChart3, Calendar, Dumbbell, History, Search, Settings, LogOut } from 'lucide-react';

import { useAuthStore } from '@/auth/authStore';
import { cn } from '@/lib/cn';

const navItems = [
  { to: '/plans', label: '루틴', icon: Calendar },
  { to: '/exercises', label: '종목', icon: Search },
  { to: '/analytics', label: '통계', icon: BarChart3 },
  { to: '/history', label: '히스토리', icon: History },
  { to: '/settings', label: '설정', icon: Settings },
];

// 좌측 사이드바 + 메인 영역. 데스크탑 우선 — 정보 밀도 위주.
export function AppShell() {
  const user = useAuthStore((s) => s.user);
  const signOut = useAuthStore((s) => s.signOut);

  return (
    <div className="flex h-full">
      <aside className="w-60 shrink-0 border-r border-neutral-200 bg-white flex flex-col">
        <div className="px-5 py-5 border-b border-neutral-200 flex items-center gap-2">
          <Dumbbell className="text-primary-600" size={22} />
          <span className="text-lg font-bold tracking-tight">GymPlan</span>
        </div>
        <nav className="flex-1 px-2 py-3 space-y-1">
          {navItems.map(({ to, label, icon: Icon }) => (
            <NavLink
              key={to}
              to={to}
              className={({ isActive }) =>
                cn(
                  'flex items-center gap-2 px-3 py-2 rounded-md text-sm font-medium',
                  isActive
                    ? 'bg-primary-50 text-primary-700'
                    : 'text-neutral-700 hover:bg-neutral-100',
                )
              }
            >
              <Icon size={18} />
              {label}
            </NavLink>
          ))}
        </nav>
        <div className="border-t border-neutral-200 px-3 py-3">
          {user && (
            <div className="px-2 pb-2 text-xs text-neutral-500">
              <div className="font-medium text-neutral-800">{user.nickname}</div>
              <div className="truncate">{user.email}</div>
            </div>
          )}
          <button
            onClick={() => signOut()}
            className="btn-ghost w-full justify-start"
          >
            <LogOut size={16} />
            로그아웃
          </button>
        </div>
      </aside>

      <main className="flex-1 overflow-y-auto">
        <Outlet />
      </main>
    </div>
  );
}
