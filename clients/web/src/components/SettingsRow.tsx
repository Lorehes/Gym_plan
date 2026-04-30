import type { ReactNode } from 'react';

import { cn } from '@/lib/cn';

interface Props {
  label: string;
  description?: string;
  control: ReactNode;
  disabled?: boolean;
}

export function SettingsRow({ label, description, control, disabled }: Props) {
  return (
    <div className={cn('flex items-center justify-between gap-4', disabled && 'opacity-50')}>
      <div className="min-w-0 flex-1">
        <p className="text-sm font-medium text-neutral-800">{label}</p>
        {description && (
          <p className="mt-0.5 text-xs text-neutral-500">{description}</p>
        )}
      </div>
      <div className="shrink-0">{control}</div>
    </div>
  );
}
