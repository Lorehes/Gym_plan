import { Dialog, DialogPanel, DialogTitle, Description } from '@headlessui/react';
import { X } from 'lucide-react';

import { cn } from '@/lib/cn';

interface Props {
  open: boolean;
  onClose: () => void;
  title: string;
  description?: string;
  children: React.ReactNode;
  size?: 'sm' | 'md' | 'lg' | 'xl';
  staticBackdrop?: boolean;
}

const sizeClass = {
  sm: 'max-w-sm',
  md: 'max-w-md',
  lg: 'max-w-lg',
  xl: 'max-w-2xl',
};

// Headless UI Dialog — 포커스 트랩 + ESC 닫기 + aria 자동 처리.
export function Modal({
  open,
  onClose,
  title,
  description,
  children,
  size = 'md',
  staticBackdrop,
}: Props) {
  return (
    <Dialog
      open={open}
      onClose={staticBackdrop ? () => undefined : onClose}
      className="relative z-50"
    >
      <div
        className="fixed inset-0 bg-neutral-900/40 backdrop-blur-sm"
        aria-hidden="true"
      />
      <div className="fixed inset-0 flex items-center justify-center p-4">
        <DialogPanel
          className={cn(
            'w-full bg-white rounded-lg shadow-xl',
            'data-[closed]:opacity-0 transition duration-150',
            sizeClass[size],
          )}
        >
          <div className="flex items-start justify-between px-6 pt-5 pb-3">
            <div>
              <DialogTitle className="text-lg font-semibold text-neutral-900">
                {title}
              </DialogTitle>
              {description && (
                <Description className="mt-1 text-sm text-neutral-500">
                  {description}
                </Description>
              )}
            </div>
            <button
              type="button"
              onClick={onClose}
              aria-label="닫기"
              className="text-neutral-400 hover:text-neutral-700 rounded-md p-1
                         focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-primary-500"
            >
              <X size={18} />
            </button>
          </div>
          <div className="px-6 pb-6">{children}</div>
        </DialogPanel>
      </div>
    </Dialog>
  );
}
