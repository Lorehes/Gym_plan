import { useMemo } from 'react';
import { ChevronLeft, ChevronRight } from 'lucide-react';

import type { FrequencyMap } from '@/api/analytics';
import { Skeleton } from '@/components/Skeleton';
import { useFrequencyQuery } from '@/hooks/useAnalytics';
import { cn } from '@/lib/cn';

interface Props {
  year: number;
  month: number;
  onPrev: () => void;
  onNext: () => void;
}

const DAY_LABELS = ['월', '화', '수', '목', '금', '토', '일'];

function pad(n: number) {
  return String(n).padStart(2, '0');
}

// 월별 캘린더 그리드 생성 — [dayIndex 0-6=Mon-Sun][weekIndex] = dayNumber | null
function buildGrid(year: number, month: number): (number | null)[][] {
  const daysInMonth = new Date(year, month, 0).getDate(); // month is 1-based
  const firstDayJS = new Date(year, month - 1, 1).getDay(); // 0=Sun..6=Sat
  const firstDayMon = (firstDayJS + 6) % 7; // 0=Mon..6=Sun

  const numWeeks = Math.ceil((firstDayMon + daysInMonth) / 7);
  // weeks[weekIndex][dayIndex]
  const weeks: (number | null)[][] = Array.from({ length: numWeeks }, () =>
    Array(7).fill(null),
  );
  for (let day = 1; day <= daysInMonth; day++) {
    const offset = firstDayMon + day - 1;
    weeks[Math.floor(offset / 7)][offset % 7] = day;
  }
  // 전치: dayRows[dayIndex][weekIndex]
  return DAY_LABELS.map((_, dayIdx) => weeks.map((week) => week[dayIdx]));
}

function cellColor(count: number, hasDay: boolean): string {
  if (!hasDay) return 'invisible';
  if (count === 0) return 'bg-neutral-100';
  if (count === 1) return 'bg-primary-200';
  if (count === 2) return 'bg-primary-400';
  return 'bg-primary-600';
}

interface HeatmapGridProps {
  year: number;
  month: number;
  frequencyMap: FrequencyMap;
}

function HeatmapGrid({ year, month, frequencyMap }: HeatmapGridProps) {
  const dayRows = useMemo(() => buildGrid(year, month), [year, month]);

  return (
    <div className="overflow-x-auto">
      <div className="inline-block min-w-full">
        <div className="space-y-1">
          {dayRows.map((cells, dayIdx) => (
            <div key={dayIdx} className="flex items-center gap-1">
              <span className="w-5 shrink-0 text-right text-xs text-neutral-400">
                {DAY_LABELS[dayIdx]}
              </span>
              {cells.map((day, weekIdx) => {
                const hasDay = day !== null;
                const dateStr = hasDay
                  ? `${year}-${pad(month)}-${pad(day as number)}`
                  : '';
                const entry = dateStr ? frequencyMap[dateStr] : undefined;
                const count = entry?.sessionCount ?? 0;
                const tooltipText = hasDay
                  ? entry
                    ? `${dateStr}: ${count}회 (${Math.round(entry.totalVolume).toLocaleString('ko-KR')}kg)`
                    : dateStr
                  : '';

                return (
                  <div
                    key={weekIdx}
                    title={tooltipText}
                    aria-label={tooltipText || undefined}
                    className={cn(
                      'h-5 w-5 rounded-sm transition-opacity',
                      cellColor(count, hasDay),
                      hasDay && count > 0 && 'hover:opacity-80',
                    )}
                  />
                );
              })}
            </div>
          ))}
        </div>

        {/* 범례 */}
        <div className="mt-3 flex items-center justify-end gap-1.5 text-xs text-neutral-400">
          <span>적게</span>
          {['bg-neutral-100', 'bg-primary-200', 'bg-primary-400', 'bg-primary-600'].map(
            (cls) => (
              <div key={cls} className={cn('h-4 w-4 rounded-sm', cls)} />
            ),
          )}
          <span>많이</span>
        </div>
      </div>
    </div>
  );
}

export function FrequencyHeatmap({ year, month, onPrev, onNext }: Props) {
  const { data, isPending, isError, refetch } = useFrequencyQuery(year, month);

  const now = new Date();
  const isCurrentMonth = year === now.getFullYear() && month === now.getMonth() + 1;

  return (
    <div className="space-y-3">
      {/* 월 내비게이션 */}
      <div className="flex items-center justify-between">
        <button
          type="button"
          onClick={onPrev}
          aria-label="이전 달"
          className="rounded-md p-1.5 text-neutral-500 hover:bg-neutral-100
                     focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-primary-500"
        >
          <ChevronLeft size={16} aria-hidden />
        </button>
        <span className="text-sm font-medium text-neutral-700">
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

      {/* 그리드 */}
      {isPending ? (
        <div className="space-y-1">
          {Array.from({ length: 7 }).map((_, i) => (
            <div key={i} className="flex items-center gap-1">
              <Skeleton className="h-4 w-5" />
              {Array.from({ length: 5 }).map((_, j) => (
                <Skeleton key={j} className="h-5 w-5 rounded-sm" />
              ))}
            </div>
          ))}
        </div>
      ) : isError ? (
        <div className="flex flex-col items-center py-8 text-sm text-neutral-500 space-y-2">
          <p>캘린더를 불러오지 못했어요.</p>
          <button onClick={() => refetch()} className="text-primary-600 hover:underline">
            다시 시도
          </button>
        </div>
      ) : (
        <HeatmapGrid year={year} month={month} frequencyMap={data ?? {}} />
      )}
    </div>
  );
}
