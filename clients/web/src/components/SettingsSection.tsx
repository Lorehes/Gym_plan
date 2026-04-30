import type { ReactNode } from 'react';

interface Props {
  title: string;
  description?: string;
  children: ReactNode;
  danger?: boolean;
}

export function SettingsSection({ title, description, children, danger }: Props) {
  return (
    <div className={`card p-6 space-y-5${danger ? ' border-error-200' : ''}`}>
      <div className="border-b border-neutral-100 pb-4">
        <h2 className={`text-base font-semibold${danger ? ' text-error-600' : ' text-neutral-900'}`}>
          {title}
        </h2>
        {description && (
          <p className="mt-1 text-sm text-neutral-500">{description}</p>
        )}
      </div>
      {children}
    </div>
  );
}
