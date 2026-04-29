import { forwardRef, useId, type InputHTMLAttributes, type ReactNode } from 'react';

import { cn } from '@/lib/cn';

export interface InputProps extends InputHTMLAttributes<HTMLInputElement> {
  label: string;
  error?: string | null;
  helper?: string;
  rightSlot?: ReactNode;
  containerClassName?: string;
}

export const Input = forwardRef<HTMLInputElement, InputProps>(function Input(
  { label, error, helper, rightSlot, containerClassName, id, className, disabled, ...rest },
  ref,
) {
  const reactId = useId();
  const inputId = id ?? reactId;
  const errorId = error ? `${inputId}-error` : undefined;
  const helperId = helper ? `${inputId}-helper` : undefined;
  const describedBy = errorId ?? helperId;

  return (
    <div className={cn('space-y-1', containerClassName)}>
      <label htmlFor={inputId} className="label">
        {label}
      </label>
      <div className="relative">
        <input
          ref={ref}
          id={inputId}
          disabled={disabled}
          aria-invalid={error ? true : undefined}
          aria-describedby={describedBy}
          className={cn(
            'input',
            rightSlot && 'pr-11',
            error && 'border-error-500 focus:border-error-500 focus:ring-error-500',
            className,
          )}
          {...rest}
        />
        {rightSlot && (
          <div className="absolute inset-y-0 right-0 flex items-center pr-2">{rightSlot}</div>
        )}
      </div>
      {error ? (
        <p id={errorId} role="alert" className="text-xs text-error-500">
          {error}
        </p>
      ) : helper ? (
        <p id={helperId} className="text-xs text-neutral-500">
          {helper}
        </p>
      ) : null}
    </div>
  );
});
