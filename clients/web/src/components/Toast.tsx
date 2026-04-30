import { useToastStore } from '@/lib/toastStore';
import { cn } from '@/lib/cn';

export function Toast() {
  const message = useToastStore((s) => s.message);

  return (
    <div
      role="status"
      aria-live="polite"
      aria-atomic="true"
      className={cn(
        'fixed bottom-6 left-1/2 z-50 -translate-x-1/2',
        'rounded-lg bg-neutral-900 px-4 py-2.5 text-sm text-white shadow-lg',
        'transition-all duration-200 ease-out',
        message
          ? 'translate-y-0 opacity-100'
          : 'pointer-events-none translate-y-2 opacity-0',
      )}
    >
      {message}
    </div>
  );
}
