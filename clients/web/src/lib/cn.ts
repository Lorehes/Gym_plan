import clsx, { type ClassValue } from 'clsx';
import { twMerge } from 'tailwind-merge';

// Tailwind 클래스 병합 헬퍼 — 충돌 클래스(예: `p-2 p-4`)는 마지막 것이 이김.
export function cn(...inputs: ClassValue[]): string {
  return twMerge(clsx(inputs));
}
