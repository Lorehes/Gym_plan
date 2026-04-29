import { cn } from '@/lib/cn';

interface Props {
  className?: string;
}

export function Skeleton({ className }: Props) {
  return (
    <div
      aria-hidden
      className={cn('animate-pulse rounded-md bg-neutral-200', className)}
    />
  );
}
