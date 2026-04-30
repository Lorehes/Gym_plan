import { useState } from 'react';
import { useNavigate } from 'react-router-dom';
import { LogOut } from 'lucide-react';

import { Button } from '@/components/Button';
import { ConfirmDialog } from '@/components/ConfirmDialog';
import { SettingsSection } from '@/components/SettingsSection';
import { useAuthStore } from '@/auth/authStore';

export function DangerZone() {
  const [confirmOpen, setConfirmOpen] = useState(false);
  const [loading, setLoading] = useState(false);
  const signOut = useAuthStore((s) => s.signOut);
  const navigate = useNavigate();

  const handleLogout = async () => {
    setLoading(true);
    try {
      await signOut();
      navigate('/login', { replace: true });
    } finally {
      setLoading(false);
      setConfirmOpen(false);
    }
  };

  return (
    <>
      <SettingsSection title="위험 영역" danger>
        <div className="flex items-center justify-between">
          <div>
            <p className="text-sm font-medium text-neutral-800">로그아웃</p>
            <p className="mt-0.5 text-xs text-neutral-500">
              이 기기에서 로그아웃합니다. 저장된 데이터는 유지돼요.
            </p>
          </div>
          <Button
            variant="danger"
            size="sm"
            onClick={() => setConfirmOpen(true)}
            className="shrink-0"
          >
            <LogOut size={14} aria-hidden className="mr-1.5" />
            로그아웃
          </Button>
        </div>

        {/* 계정 탈퇴 — 백엔드 미구현 */}
        <div className="flex items-center justify-between opacity-50">
          <div>
            <p className="text-sm font-medium text-neutral-800">계정 탈퇴</p>
            <p className="mt-0.5 text-xs text-neutral-500">
              계정과 모든 데이터를 영구 삭제해요. 되돌릴 수 없어요.
            </p>
          </div>
          <Button variant="danger" size="sm" disabled>
            탈퇴하기
          </Button>
        </div>
        <p className="text-xs text-neutral-400 text-right">계정 탈퇴 기능은 준비 중이에요.</p>
      </SettingsSection>

      <ConfirmDialog
        open={confirmOpen}
        onClose={() => setConfirmOpen(false)}
        onConfirm={handleLogout}
        title="로그아웃"
        description="로그아웃하시겠어요? 다시 로그인하면 이용할 수 있어요."
        confirmLabel="로그아웃"
        cancelLabel="취소"
        variant="danger"
        loading={loading}
      />
    </>
  );
}
