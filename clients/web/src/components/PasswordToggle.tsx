import { Eye, EyeOff } from 'lucide-react';

import { cn } from '@/lib/cn';

interface Props {
  visible: boolean;
  onToggle: () => void;
  ariaControls?: string;
  className?: string;
}

// 비밀번호 표시/숨기기 토글 — Input.rightSlot 에 주입.
export function PasswordToggle({ visible, onToggle, ariaControls, className }: Props) {
  return (
    <button
      type="button"
      onClick={onToggle}
      aria-label={visible ? '비밀번호 숨기기' : '비밀번호 표시'}
      aria-pressed={visible}
      aria-controls={ariaControls}
      className={cn(
        'flex items-center justify-center h-8 w-8 rounded-md text-neutral-500',
        'hover:text-neutral-800 hover:bg-neutral-100',
        'focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-primary-500',
        className,
      )}
      // 폼 제출 시 토글에 잠시 포커스 머무는 것을 방지.
      tabIndex={-1}
    >
      {visible ? <EyeOff size={16} aria-hidden /> : <Eye size={16} aria-hidden />}
    </button>
  );
}
