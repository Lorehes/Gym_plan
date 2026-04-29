import { useState } from 'react';
import { NavLink, Outlet } from 'react-router-dom';
import { BarChart3, Calendar, Dumbbell, History, Menu, Search, Settings, LogOut, X } from 'lucide-react';

import { useAuthStore } from '@/auth/authStore';
import { cn } from '@/lib/cn';
import { Toast } from './Toast';

const navItems = [
  { to: '/plans', label: '루틴', icon: Calendar },
  { to: '/exercises', label: '종목', icon: Search },
  { to: '/analytics', label: '통계', icon: BarChart3 },
  { to: '/history', label: '히스토리', icon: History },
  { to: '/settings', label: '설정', icon: Settings },
];

export function AppShell() {
  const user = useAuthStore((s) => s.user);
  const signOut = useAuthStore((s) => s.signOut);
  const [drawerOpen, setDrawerOpen] = useState(false);

  const closDrawer = () => setDrawerOpen(false);

  return (
    <div className="flex h-full">
      {/* 모바일 오버레이 */}
      {drawerOpen && (
        <div
          className="fixed inset-0 z-30 bg-neutral-900/40 md:hidden"
          onClick={closDrawer}
          aria-hidden="true"
        />
      )}

      {/* 사이드바 — 데스크탑: 항상 표시 / 모바일: 드로어 */}
      <aside
        className={cn(
          'fixed inset-y-0 left-0 z-40 flex w-60 flex-col border-r border-neutral-200 bg-white',
          'transition-transform duration-200 ease-in-out',
          'md:static md:translate-x-0',
          drawerOpen ? 'translate-x-0' : '-translate-x-full',
        )}
      >
        <div className="flex items-center justify-between border-b border-neutral-200 px-5 py-5">
          <div className="flex items-center gap-2">
            <Dumbbell className="text-primary-600" size={22} />
            <span className="text-lg font-bold tracking-tight">GymPlan</span>
          </div>
          <button
            type="button"
            onClick={closDrawer}
            className="rounded-md p-1 text-neutral-400 hover:text-neutral-700 md:hidden
                       focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-primary-500"
            aria-label="메뉴 닫기"
          >
            <X size={18} />
          </button>
        </div>

        <nav className="flex-1 px-2 py-3 space-y-1">
          {navItems.map(({ to, label, icon: Icon }) => (
            <NavLink
              key={to}
              to={to}
              onClick={closDrawer}
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

      {/* 메인 영역 */}
      <div className="flex flex-1 flex-col min-w-0">
        {/* 모바일 상단 바 */}
        <header className="flex items-center gap-3 border-b border-neutral-200 bg-white px-4 py-3 md:hidden">
          <button
            type="button"
            onClick={() => setDrawerOpen(true)}
            className="rounded-md p-1.5 text-neutral-500 hover:bg-neutral-100
                       focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-primary-500"
            aria-label="메뉴 열기"
          >
            <Menu size={20} />
          </button>
          <div className="flex items-center gap-2">
            <Dumbbell className="text-primary-600" size={18} />
            <span className="text-base font-bold tracking-tight">GymPlan</span>
          </div>
        </header>

        <main className="flex-1 overflow-y-auto">
          <Outlet />
        </main>
      </div>

      <Toast />
    </div>
  );
}
