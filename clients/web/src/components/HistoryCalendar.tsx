import { useMemo, useRef } from 'react';
import { ChevronLeft, ChevronRight } from 'lucide-react';

import { Skeleton } from '@/components/Skeleton';
import { useSessionHistoryQuery } from '@/hooks/useSessionHistory';
import { cn } from '@/lib/cn';

interface Props {
  year: number;
  month: number;
  selectedDate: string | null; // 'YYYY-MM-DD'
  onSelectDate: (date: string | null) => void;
  onPrev: () => void;
  onNext: () => void;
}

const DAY_LABELS = ['월', '화', '수', '목', '금', '토', '일'];

function pad(n: number) {
  return String(n).padStart(2, '0');
}

// Returns flat 7-column grid: null = empty cell, number = day-of-month
function buildFlatGrid(year: number, month: number): (number | null)[] {
  const daysInMonth = new Date(year, month, 0).getDate();
  const firstDayJS = new Date(year, month - 1, 1).getDay(); // 0=Sun
  const firstDayMon = (firstDayJS + 6) % 7; // 0=Mon
  const cells: (number | null)[] = Array(firstDayMon).fill(null);
  for (let d = 1; d <= daysInMonth; d++) cells.push(d);
  // pad to full weeks
  while (cells.length % 7 !== 0) cells.push(null);
  return cells;
}

export function HistoryCalendar({
  year,
  month,
  selectedDate,
  onSelectDate,
  onPrev,
  onNext,
}: Props) {
  const { data, isPending } = useSessionHistoryQuery();
  const gridRef = useRef<HTMLDivElement>(null);

  const now = new Date();
  const todayStr = `${now.getFullYear()}-${pad(now.getMonth() + 1)}-${pad(now.getDate())}`;
  const isCurrentMonth = year === now.getFullYear() && month === now.getMonth() + 1;

  // date → session count for this month
  const countByDate = useMemo(() => {
    const map: Record<string, number> = {};
    if (!data) return map;
    data.content.forEach((s) => {
      const d = new Date(s.startedAt);
      if (d.getFullYear() !== year || d.getMonth() + 1 !== month) return;
      const key = `${year}-${pad(month)}-${pad(d.getDate())}`;
      map[key] = (map[key] ?? 0) + 1;
    });
    return map;
  }, [data, year, month]);

  const cells = useMemo(() => buildFlatGrid(year, month), [year, month]);

  const handleKeyDown = (e: React.KeyboardEvent, dateStr: string) => {
    if (e.key === 'Enter' || e.key === ' ') {
      e.preventDefault();
      onSelectDate(selectedDate === dateStr ? null : dateStr);
    }
    // Arrow key navigation within grid
    if (!['ArrowLeft', 'ArrowRight', 'ArrowUp', 'ArrowDown'].includes(e.key)) return;
    e.preventDefault();
    const dayEl = gridRef.current?.querySelector<HTMLElement>(`[data-date="${dateStr}"]`);
    if (!dayEl) return;
    const allDays = Array.from(
      gridRef.current?.querySelectorAll<HTMLElement>('[data-date]') ?? [],
    );
    const idx = allDays.indexOf(dayEl);
    let next = idx;
    if (e.key === 'ArrowLeft') next = idx - 1;
    if (e.key === 'ArrowRight') next = idx + 1;
    if (e.key === 'ArrowUp') next = idx - 7;
    if (e.key === 'ArrowDown') next = idx + 7;
    allDays[next]?.focus();
  };

  return (
    <div className="select-none">
      {/* Header */}
      <div className="flex items-center justify-between mb-4">
        <button
          type="button"
          onClick={onPrev}
          aria-label="이전 달"
          className="rounded-md p-1.5 text-neutral-500 hover:bg-neutral-100
                     focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-primary-500"
        >
          <ChevronLeft size={16} aria-hidden />
        </button>
        <span className="text-sm font-semibold text-neutral-800">
          {year}년 {month}월
        </span>
        <button
          type="button"
          onClick={onNext}
          disabled={isCurrentMonth}
          aria-label="다음 달"
          className="rounded-md p-1.5 text-neutral-500 hover:bg-neutral-100
                     focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-primary-500
                     disabled:opacity-30 disabled:cursor-not-allowed"
        >
          <ChevronRight size={16} aria-hidden />
        </button>
      </div>

      {/* Day-of-week labels */}
      <div className="grid grid-cols-7 mb-1">
        {DAY_LABELS.map((d) => (
          <div key={d} className="text-center text-xs font-medium text-neutral-400 py-1">
            {d}
          </div>
        ))}
      </div>

      {/* Grid */}
      {isPending ? (
        <div className="grid grid-cols-7 gap-1">
          {Array.from({ length: 35 }).map((_, i) => (
            <Skeleton key={i} className="h-10 w-full rounded-md" />
          ))}
        </div>
      ) : (
        <div ref={gridRef} className="grid grid-cols-7 gap-1" role="grid" aria-label={`${year}년 ${month}월 캘린더`}>
          {cells.map((day, i) => {
            if (day === null) {
              return <div key={i} role="gridcell" aria-hidden />;
            }
            const dateStr = `${year}-${pad(month)}-${pad(day)}`;
            const count = countByDate[dateStr] ?? 0;
            const isToday = dateStr === todayStr;
            const isFuture = dateStr > todayStr;
            const isSelected = dateStr === selectedDate;

            return (
              <div
                key={i}
                role="gridcell"
                data-date={dateStr}
                tabIndex={isFuture ? -1 : 0}
                aria-label={`${month}월 ${day}일${count > 0 ? `, 운동 ${count}회` : ''}${isToday ? ', 오늘' : ''}`}
                aria-pressed={isSelected}
                aria-disabled={isFuture}
                onClick={() => !isFuture && onSelectDate(isSelected ? null : dateStr)}
                onKeyDown={(e) => !isFuture && handleKeyDown(e, dateStr)}
                className={cn(
                  'relative flex flex-col items-center justify-center rounded-md py-1.5 transition-colors',
                  'focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-primary-500',
                  isFuture
                    ? 'opacity-30 cursor-not-allowed'
                    : 'cursor-pointer hover:bg-neutral-100',
                  isSelected && 'bg-primary-600 hover:bg-primary-700 text-white',
                  isToday && !isSelected && 'ring-2 ring-primary-400',
                )}
              >
                <span
                  className={cn(
                    'text-sm leading-none',
                    isSelected ? 'font-semibold text-white' : 'text-neutral-700',
                    isToday && !isSelected && 'font-semibold text-primary-600',
                  )}
                >
                  {day}
                </span>
                {/* Session count indicator */}
                {count > 0 && (
                  <span
                    className={cn(
                      'mt-0.5 text-[10px] leading-none font-medium',
                      isSelected ? 'text-primary-100' : 'text-primary-500',
                    )}
                  >
                    {count}회
                  </span>
                )}
              </div>
            );
          })}
        </div>
      )}
    </div>
  );
}
