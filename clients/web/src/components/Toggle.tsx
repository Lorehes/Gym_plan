import { Switch } from '@headlessui/react';

import { cn } from '@/lib/cn';

interface Props {
  checked: boolean;
  onChange: (checked: boolean) => void;
  disabled?: boolean;
  label?: string; // sr-only label for accessibility
}

export function Toggle({ checked, onChange, disabled, label }: Props) {
  return (
    <Switch
      checked={checked}
      onChange={onChange}
      disabled={disabled}
      aria-label={label}
      className={cn(
        'relative inline-flex h-6 w-11 shrink-0 cursor-pointer rounded-full border-2 border-transparent',
        'transition-colors duration-200 ease-in-out',
        'focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-primary-500 focus-visible:ring-offset-2',
        checked ? 'bg-primary-600' : 'bg-neutral-200',
        disabled && 'cursor-not-allowed opacity-40',
      )}
    >
      <span
        aria-hidden="true"
        className={cn(
          'pointer-events-none inline-block h-5 w-5 rounded-full bg-white shadow-md',
          'transform transition duration-200 ease-in-out',
          checked ? 'translate-x-5' : 'translate-x-0',
        )}
      />
    </Switch>
  );
}
