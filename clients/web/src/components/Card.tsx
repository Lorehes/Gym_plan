import { forwardRef, type HTMLAttributes } from 'react';

import { cn } from '@/lib/cn';

export interface CardProps extends HTMLAttributes<HTMLDivElement> {
  interactive?: boolean;
}

// 기본 카드 — interactive 일 때만 hover 그림자/커서.
export const Card = forwardRef<HTMLDivElement, CardProps>(function Card(
  { interactive, className, ...rest },
  ref,
) {
  return (
    <div
      ref={ref}
      className={cn(
        'card',
        interactive && 'cursor-pointer transition-shadow hover:shadow-md focus-within:shadow-md',
        className,
      )}
      {...rest}
    />
  );
});
